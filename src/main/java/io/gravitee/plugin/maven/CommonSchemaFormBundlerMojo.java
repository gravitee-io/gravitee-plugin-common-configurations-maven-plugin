/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.plugin.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.SelectorUtils;

/**
 * A Maven plugin Mojo that processes and bundles schema definitions from local and external artifacts
 * into a unified schema file. This Mojo operates during the Maven lifecycle and consolidates schema
 * resources for downstream usage.
 *
 * The goal of this Mojo is defined as `common-schema-form-bundle`, and it operates within the
 * `process-resources` phase of the Maven lifecycle. It requires compile-scope dependencies to be resolved
 * for proper execution.
 */
@Mojo(
    name = "common-schema-form-bundle",
    defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE
)
public class CommonSchemaFormBundlerMojo extends AbstractMojo {

    public static final String EXTERNAL_DEFINITIONS_JSON_FIELD = "gioExternalDefinitions";
    public static final String PRETTIER_IGNORE = ".prettierignore";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject currentMavenProject;

    /**
     * A set of artifact patterns to be included during the processing of schema files.
     * This variable specifies the artifacts that should be considered for inclusion
     * in the form of {artifactGroup:artifactId}, for example "gravitee.plugin:gravitee-plugin-api".
     * The patterns are used to filter the artifacts processed by the plugin to include only those that match the specified criteria.
     *
     * The value of this field is configured using the property {@code includeArtifacts}.
     */
    @Parameter(property = "includeArtifacts")
    private Set<String> includeArtifacts;

    /**
     * A set of Ant-style patterns specifying which schema files should be included in the process.
     * The patterns allow finer control over the selection of schema files, enabling specific inclusion
     * based on their paths or names.
     *
     * This variable is mandatory and must be set for proper execution. If not explicitly provided,
     * it may default to certain values depending on the context or implementation.
     *
     * Patterns typically follow a directory structure
     */
    @Parameter(property = "includeSchemas")
    private Set<String> includeSchemas;

    /**
     * Path to the local schema-form file in the project to be used.
     */
    @Parameter(property = "localSchemaFile", defaultValue = "${project.basedir}/src/main/resources/schemas/schema-form.json")
    private File localSchemaFile;

    /**
     * Path to the generated schema-form file in the project.
     */
    @Parameter(property = "outputFile", defaultValue = "${project.build.outputDirectory}/schemas/schema-form.json")
    private File outputFile;

    private final ObjectMapper mapper = new ObjectMapper();

    public CommonSchemaFormBundlerMojo() {}

    @VisibleForTesting
    protected CommonSchemaFormBundlerMojo(
        MavenProject currentMavenProject,
        Set<String> includeArtifacts,
        Set<String> includeSchemas,
        File localSchemaFile,
        File outputFile
    ) {
        this.currentMavenProject = currentMavenProject;
        this.includeArtifacts = includeArtifacts;
        this.includeSchemas = includeSchemas;
        this.localSchemaFile = localSchemaFile;
        this.outputFile = outputFile;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        checkPrettierIgnoreExistence();
        initializeDefaultIncludeSchemasPattern();
        verifyLocalSchemaFormExistence();

        ObjectNode localSchemaObjectNode = initializeLocalSchemaObjectNode();
        Stream<Artifact> filteredArtifacts = filterArtifacts();
        ObjectNode mergedExternalDefinitions = mergeExternalSchemasDefinitions(filteredArtifacts);
        ObjectNode mergedDefinitions = mergeLocalAndExternalDefinitions(localSchemaObjectNode, mergedExternalDefinitions);

        // Sort keys to have a deterministic output
        ObjectNode sortedDefinitions = mapper.createObjectNode();
        List<String> keys = new ArrayList<>();
        mergedDefinitions.fieldNames().forEachRemaining(keys::add);
        Collections.sort(keys);
        for (String key : keys) {
            sortedDefinitions.set(key, mergedDefinitions.get(key));
        }
        localSchemaObjectNode.set(EXTERNAL_DEFINITIONS_JSON_FIELD, sortedDefinitions);

        getLog().debug("Generated schema: " + localSchemaObjectNode.toPrettyString());

        writeMergedSchemaFormFile(localSchemaObjectNode);

        getLog().info("Schema merged successfully to " + outputFile.getAbsolutePath());
    }

    private void checkPrettierIgnoreExistence() {
        File basedir = currentMavenProject.getBasedir();
        File prettierIgnoreFile = findPrettierIgnoreFile(basedir);

        if (prettierIgnoreFile == null) {
            getLog().warn("No .prettierignore file found in project hierarchy (starting from " + basedir + ").");
            getLog().warn(
                "It is highly recommended to create a .prettierignore file containing 'target' to prevent Prettier from scanning generated files."
            );
            return;
        }

        try {
            boolean targetIsIgnored = java.nio.file.Files.lines(prettierIgnoreFile.toPath())
                .map(String::trim)
                // Accept "target", "target/", "/target" ou "/target/"
                .anyMatch(line -> line.equals("target") || line.equals("target/") || line.equals("/target") || line.equals("/target/"));

            if (!targetIsIgnored) {
                getLog().warn(
                    "The .prettierignore file at " +
                        prettierIgnoreFile.getAbsolutePath() +
                        " does not seem to ignore the 'target' directory."
                );
                getLog().warn(
                    "It is highly recommended to add 'target' to your .prettierignore file to prevent Prettier from scanning generated files."
                );
            }
        } catch (IOException e) {
            getLog().warn("Could not read .prettierignore file: " + prettierIgnoreFile.getAbsolutePath());
        }
    }

    private File findPrettierIgnoreFile(File directory) {
        File currentDir = directory;
        while (currentDir != null && currentDir.exists()) {
            File prettierIgnore = new File(currentDir, PRETTIER_IGNORE);
            if (prettierIgnore.exists()) {
                return prettierIgnore;
            }
            // Remonter au parent
            currentDir = currentDir.getParentFile();
        }
        return null;
    }

    private void verifyLocalSchemaFormExistence() throws MojoExecutionException {
        if (!localSchemaFile.exists()) {
            throw new MojoExecutionException("Local schema file does not exist: " + localSchemaFile.getAbsolutePath());
        }
    }

    private void initializeDefaultIncludeSchemasPattern() {
        if (includeSchemas == null || includeSchemas.isEmpty()) {
            getLog().debug("No includeSchemas pattern specified, using default pattern: schemas/**/*.json");
            includeSchemas = Collections.singleton("schemas/**/*.json");
        }
    }

    private ObjectNode initializeLocalSchemaObjectNode() throws MojoExecutionException {
        ObjectNode localSchema;
        try {
            JsonNode root = mapper.readTree(localSchemaFile);
            if (!root.isObject()) {
                throw new MojoExecutionException("Local schema file is not a valid JSON object: " + localSchemaFile.getAbsolutePath());
            }
            localSchema = (ObjectNode) root;
            getLog().debug("Local schema file successfully parsed " + localSchemaFile.getAbsolutePath());
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot parse local schema file: " + localSchemaFile.getAbsolutePath());
        }
        if (!localSchema.has(EXTERNAL_DEFINITIONS_JSON_FIELD)) {
            getLog().debug("Local schema file does not contain a 'gioExternalDefinitions' field, creating it");
            localSchema.putObject(EXTERNAL_DEFINITIONS_JSON_FIELD);
        }
        return localSchema;
    }

    private Stream<Artifact> filterArtifacts() {
        return currentMavenProject.getArtifacts().stream().filter(this::shouldIncludeArtifact);
    }

    private ObjectNode mergeExternalSchemasDefinitions(Stream<Artifact> filteredArtifacts) throws MojoExecutionException {
        try {
            return filteredArtifacts
                .filter(artifact -> artifact.getFile() != null && artifact.getFile().exists())
                .map(Artifact::getFile)
                .flatMap(artifactFile -> {
                    try {
                        return extractDefinitionsFromJar(artifactFile);
                    } catch (IOException e) {
                        // Explicitly make the build fail in case of error.
                        throw new RuntimeException("Error extracting 'gioExternalDefinitions' from " + artifactFile.getAbsolutePath(), e);
                    }
                })
                .reduce(mapper.createObjectNode(), (acc, node) -> {
                    if (node.isObject()) {
                        node.fields().forEachRemaining(fieldEntry -> acc.set(fieldEntry.getKey(), fieldEntry.getValue()));
                    }
                    getLog().debug(
                        "External schema file successfully parsed. Merging definitions with keys: " +
                            acc.properties().stream().map(Map.Entry::getKey).toList()
                    );
                    return acc;
                });
        } catch (RuntimeException e) {
            throw new MojoExecutionException("Error occurred during extraction of 'gioExternalDefinitions' json object: ", e);
        }
    }

    private Stream<ObjectNode> extractDefinitionsFromJar(File artifactFile) throws IOException {
        // Use a LinkedHashSet to ensure deterministic insertion order
        Set<ObjectNode> definitions = new LinkedHashSet<>();

        try (JarFile jarFile = new JarFile(artifactFile)) {
            getLog().debug("Parsing jar file: " + artifactFile.getAbsolutePath());
            Set<String> matchingEntries = findMatchingSchemaFormEntries(jarFile);
            getLog().debug("Found matching entries: " + matchingEntries);
            for (String entryName : matchingEntries) {
                JarEntry entry = jarFile.getJarEntry(entryName);
                try (InputStream is = jarFile.getInputStream(entry)) {
                    getLog().debug("Parsing schema file: " + entryName);
                    JsonNode schema = mapper.readTree(is);
                    if (schema.has(EXTERNAL_DEFINITIONS_JSON_FIELD)) {
                        getLog().debug("Found 'gioExternalDefinitions' in: " + entryName);
                        definitions.add((ObjectNode) schema.get(EXTERNAL_DEFINITIONS_JSON_FIELD));
                    }
                }
            }
        }

        return definitions.stream();
    }

    private ObjectNode mergeLocalAndExternalDefinitions(ObjectNode localSchema, ObjectNode mergedDefinitions) {
        localSchema
            .get(EXTERNAL_DEFINITIONS_JSON_FIELD)
            .fields()
            .forEachRemaining(fieldEntry -> {
                // Set definition objects and override existing ones with the ones from the local schema
                if (mergedDefinitions.has(fieldEntry.getKey())) {
                    getLog().debug("Local definition overrides external one for key: " + fieldEntry.getKey());
                }
                mergedDefinitions.set(fieldEntry.getKey(), fieldEntry.getValue());
            });
        getLog().info("Local and external definitions successfully merged");
        return mergedDefinitions;
    }

    private Set<String> findMatchingSchemaFormEntries(JarFile jarfile) {
        return jarfile
            .stream()
            .map(JarEntry::getName)
            // Ignore folders
            .filter(name -> !name.endsWith("/"))
            .filter(this::matchesAnyIncludeSchemaFormPattern)
            // Use a LinkedHashSet to ensure deterministic insertion order
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean matchesAnyIncludeSchemaFormPattern(String jarEntryName) {
        return includeSchemas.stream().anyMatch(pattern -> matchesAntPattern(jarEntryName, pattern));
    }

    /**
     * Transform Ant-style patterns to regex
     * Ant-style provides syntax as in classical maven plugins (such as maven-dependency-plugin)
     */
    private boolean matchesAntPattern(String path, String pattern) {
        // Normalize separators
        path = path.replace('\\', '/');
        pattern = pattern.replace('\\', '/');

        return SelectorUtils.matchPath(pattern, path);
    }

    private boolean shouldIncludeArtifact(Artifact artifact) {
        if (includeArtifacts == null || includeArtifacts.isEmpty()) {
            getLog().debug("No includeArtifacts pattern specified, using default pattern: *:*, all available artifacts will be included");
            return true;
        }
        String id = artifact.getGroupId() + ":" + artifact.getArtifactId();
        return includeArtifacts.contains(id);
    }

    private void writeMergedSchemaFormFile(ObjectNode mergedSchemaForm) throws MojoExecutionException {
        try {
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new MojoExecutionException("Unable to create output directory: " + parent.getAbsolutePath());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, mergedSchemaForm);
        } catch (IOException e) {
            throw new MojoExecutionException("Error occurred while writing output file: " + outputFile.getAbsolutePath());
        }
    }
}
