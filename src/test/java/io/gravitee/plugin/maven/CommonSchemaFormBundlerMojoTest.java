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

import static io.gravitee.plugin.maven.fixtures.FileHelpers.loadResourceAsFile;
import static io.gravitee.plugin.maven.fixtures.FileHelpers.loadResourceAsString;
import static io.gravitee.plugin.maven.fixtures.FileHelpers.prepareOutputSchemaFormFile;
import static io.gravitee.plugin.maven.fixtures.JsonNodeHelpers.extractExternalDefinitionsJsonNode;
import static io.gravitee.plugin.maven.fixtures.JsonNodeHelpers.extractJsonKeysInOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.plugin.maven.fixtures.TestJarArtifactBuilder;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class CommonSchemaFormBundlerMojoTest {

    public static final String LOCAL_PROJECT_SCHEMA_FORM = "/localProject/src/main/resources/schemas/schema-form.json";
    public static final String LOCAL_PROJECT_SCHEMA_FORM_WITHOUT_GIO_DEFINITIONS =
        "/localProject/src/main/resources/schemas/schema-form-without-gio-definitions.json";
    public static final String LOCAL_PROJECT_SCHEMA_FORM_WITH_DESCRIPTIONS =
        "/localProject/src/main/resources/schemas/schema-form-with-descriptions.json";

    @Test
    void should_not_modify_local_schema_form_when_nothing_import(@TempDir Path tempDir) throws Exception {
        // Given
        File localSchema = loadResourceAsFile(LOCAL_PROJECT_SCHEMA_FORM);
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        MavenProject currentMavenProject = prepareMavenProject(Collections.emptySet());

        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            currentMavenProject,
            Set.of(),
            Set.of(),
            localSchema,
            outputFile,
            SupportedFeatures.builder().build()
        );

        // When
        mojo.execute();

        // Then
        JsonNode gioExternalDefinitions = extractExternalDefinitionsJsonNode(outputFile);
        // Collect keys in order
        List<String> keys = extractJsonKeysInOrder(gioExternalDefinitions);
        // Expect deterministic order [a, b]
        assertThat(keys).containsExactly("a", "b");
        // Ensure content is preserved
        assertThat(gioExternalDefinitions.get("a"))
            .asString()
            .isEqualTo(
                """
                {"type":"integer"}"""
            );
        assertThat(gioExternalDefinitions.get("b"))
            .asString()
            .isEqualTo(
                """
                {"type":"string"}"""
            );
    }

    @Test
    void should_get_external_definitions_from_all_schemas_of_a_jar(@TempDir Path tempDir) throws Exception {
        // Given
        File localSchema = loadResourceAsFile(LOCAL_PROJECT_SCHEMA_FORM);
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        // Create an external jar containing a json schema and build an Artifact pointing to it
        String extGroupId = "com.acme";
        String extArtifactId = "ext";

        Artifact extArtifact = TestJarArtifactBuilder.builder()
            .tempDir(tempDir)
            .jarName("external-schemas.jar")
            .groupId(extGroupId)
            .artifactId(extArtifactId)
            .entry("schemas/external/external-schema-c-key.json", loadResourceAsString("/external/schema-form-c-key.json"))
            .entry("schemas/external/external-schema-d-key.json", loadResourceAsString("/external/schema-form-d-key.json"))
            .build()
            .buildArtifact();

        MavenProject currentMavenProject = prepareMavenProject(Set.of(extArtifact));

        // When
        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            currentMavenProject,
            Set.of(extGroupId + ":" + extArtifactId),
            Set.of(),
            localSchema,
            outputFile,
            SupportedFeatures.builder().build()
        );

        mojo.execute();

        // Then
        JsonNode gioExternalDefinitions = extractExternalDefinitionsJsonNode(outputFile);
        // Collect keys in order
        List<String> keys = extractJsonKeysInOrder(gioExternalDefinitions);
        // Expect deterministic order [a, b, c, d]
        assertThat(keys).containsExactly("a", "b", "c", "d");
        // Ensure content is preserved
        assertThat(gioExternalDefinitions.get("a"))
            .asString()
            .isEqualTo(
                """
                {"type":"integer"}"""
            );
        assertThat(gioExternalDefinitions.get("b"))
            .asString()
            .isEqualTo(
                """
                {"type":"string"}"""
            );
        assertThat(gioExternalDefinitions.get("c"))
            .asString()
            .isEqualTo(
                """
                {"type":"boolean"}"""
            );

        assertThat(gioExternalDefinitions.get("d"))
            .asString()
            .isEqualTo(
                """
                {"type":"object"}"""
            );
    }

    @Test
    void should_get_external_definitions_from_filtered_schemas_from_different_jars(@TempDir Path tempDir) throws Exception {
        // Given
        File localSchema = loadResourceAsFile(LOCAL_PROJECT_SCHEMA_FORM);
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        // Create an external jar containing a json schema and build an Artifact pointing to it
        String extGroupId = "com.acme";
        String extArtifactId = "ext";
        String otherExtArtifactId = "otherExt";

        Artifact extArtifact = TestJarArtifactBuilder.builder()
            .tempDir(tempDir)
            .jarName("external-schemas.jar")
            .groupId(extGroupId)
            .artifactId(extArtifactId)
            .entry("schemas/external/external-schema-c-key.json", loadResourceAsString("/external/schema-form-c-key.json"))
            .build()
            .buildArtifact();

        Artifact otherExtArtifact = TestJarArtifactBuilder.builder()
            .tempDir(tempDir)
            .jarName("other-external-schemas.jar")
            .groupId(extGroupId)
            .artifactId(otherExtArtifactId)
            .entry("schemas/other-external/external-schema-d-key.json", loadResourceAsString("/external/schema-form-d-key.json"))
            .build()
            .buildArtifact();

        MavenProject currentMavenProject = prepareMavenProject(Set.of(extArtifact, otherExtArtifact));

        // When
        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            currentMavenProject,
            Set.of(extGroupId + ":" + extArtifactId, extGroupId + ":" + otherExtArtifactId),
            Set.of(),
            localSchema,
            outputFile,
            SupportedFeatures.builder().build()
        );

        mojo.execute();

        // Then
        JsonNode gioExternalDefinitions = extractExternalDefinitionsJsonNode(outputFile);
        // Collect keys in order
        List<String> keys = extractJsonKeysInOrder(gioExternalDefinitions);
        // Expect deterministic order [a, b, c, d]
        assertThat(keys).containsExactly("a", "b", "c", "d");
        // Ensure content is preserved
        assertThat(gioExternalDefinitions.get("a"))
            .asString()
            .isEqualTo(
                """
                {"type":"integer"}"""
            );
        assertThat(gioExternalDefinitions.get("b"))
            .asString()
            .isEqualTo(
                """
                {"type":"string"}"""
            );
        assertThat(gioExternalDefinitions.get("c"))
            .asString()
            .isEqualTo(
                """
                {"type":"boolean"}"""
            );

        assertThat(gioExternalDefinitions.get("d"))
            .asString()
            .isEqualTo(
                """
                {"type":"object"}"""
            );
    }

    @Test
    void should_get_external_definitions_from_one_schema(@TempDir Path tempDir) throws Exception {
        // Given
        File localSchema = loadResourceAsFile(LOCAL_PROJECT_SCHEMA_FORM);
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        // Create an external jar containing a json schema and build an Artifact pointing to it
        String extGroupId = "com.acme";
        String extArtifactId = "ext";

        Artifact extArtifact = TestJarArtifactBuilder.builder()
            .tempDir(tempDir)
            .jarName("external-schemas.jar")
            .groupId(extGroupId)
            .artifactId(extArtifactId)
            .entry("schemas/external/external-schema-c-key.json", loadResourceAsString("/external/schema-form-c-key.json"))
            .entry("schemas/external/external-schema-d-key.json", loadResourceAsString("/external/schema-form-d-key.json"))
            .build()
            .buildArtifact();

        MavenProject currentMavenProject = prepareMavenProject(Set.of(extArtifact));

        // When
        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            currentMavenProject,
            Set.of(extGroupId + ":" + extArtifactId),
            Set.of("schemas/external/*c-key.json"),
            localSchema,
            outputFile,
            SupportedFeatures.builder().build()
        );

        mojo.execute();

        // Then
        JsonNode gioExternalDefinitions = extractExternalDefinitionsJsonNode(outputFile);
        // Collect keys in order
        List<String> keys = extractJsonKeysInOrder(gioExternalDefinitions);
        // Expect deterministic order [a, b, c]
        assertThat(keys).containsExactly("a", "b", "c");
        // Ensure content is preserved
        assertThat(gioExternalDefinitions.get("a"))
            .asString()
            .isEqualTo(
                """
                {"type":"integer"}"""
            );
        assertThat(gioExternalDefinitions.get("b"))
            .asString()
            .isEqualTo(
                """
                {"type":"string"}"""
            );
        assertThat(gioExternalDefinitions.get("c"))
            .asString()
            .isEqualTo(
                """
                {"type":"boolean"}"""
            );
    }

    @Test
    void should_get_local_value_when_conflicting_key_from_external_schema(@TempDir Path tempDir) throws Exception {
        // Given
        File localSchema = loadResourceAsFile(LOCAL_PROJECT_SCHEMA_FORM);
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        // Create an external jar containing a json schema and build an Artifact pointing to it
        String extGroupId = "com.acme";
        String extArtifactId = "ext";

        Artifact extArtifact = TestJarArtifactBuilder.builder()
            .tempDir(tempDir)
            .jarName("external-schemas.jar")
            .groupId(extGroupId)
            .artifactId(extArtifactId)
            .entry("schemas/external/external-schema-a-key.json", loadResourceAsString("/external/schema-form-a-key.json"))
            .build()
            .buildArtifact();

        MavenProject currentMavenProject = prepareMavenProject(Set.of(extArtifact));

        // When
        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            currentMavenProject,
            Set.of(extGroupId + ":" + extArtifactId),
            Set.of(),
            localSchema,
            outputFile,
            SupportedFeatures.builder().build()
        );

        mojo.execute();

        // Then
        JsonNode gioExternalDefinitions = extractExternalDefinitionsJsonNode(outputFile);
        // Collect keys in order
        List<String> keys = extractJsonKeysInOrder(gioExternalDefinitions);
        // Expect deterministic order [a, b]
        assertThat(keys).containsExactly("a", "b");
        // Ensure content is preserved
        assertThat(gioExternalDefinitions.get("a"))
            .asString()
            .isEqualTo(
                """
                {"type":"integer"}"""
            );
        assertThat(gioExternalDefinitions.get("b"))
            .asString()
            .isEqualTo(
                """
                {"type":"string"}"""
            );
    }

    @Test
    void should_initialize_gio_external_definitions_json_object_when_not_existing(@TempDir Path tempDir) throws Exception {
        // Given
        File localSchema = loadResourceAsFile(LOCAL_PROJECT_SCHEMA_FORM_WITHOUT_GIO_DEFINITIONS);
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        // Create an external jar containing a json schema and build an Artifact pointing to it
        String extGroupId = "com.acme";
        String extArtifactId = "ext";

        Artifact extArtifact = TestJarArtifactBuilder.builder()
            .tempDir(tempDir)
            .jarName("external-schemas.jar")
            .groupId(extGroupId)
            .artifactId(extArtifactId)
            .entry("schemas/external/external-schema-a-key.json", loadResourceAsString("/external/schema-form-a-key.json"))
            .build()
            .buildArtifact();

        MavenProject currentMavenProject = prepareMavenProject(Set.of(extArtifact));

        // When
        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            currentMavenProject,
            Set.of(extGroupId + ":" + extArtifactId),
            Set.of(),
            localSchema,
            outputFile,
            SupportedFeatures.builder().build()
        );

        mojo.execute();

        // Then
        JsonNode gioExternalDefinitions = extractExternalDefinitionsJsonNode(outputFile);
        // Collect keys in order
        List<String> keys = extractJsonKeysInOrder(gioExternalDefinitions);
        // Expect deterministic order [a]
        assertThat(keys).containsExactly("a");
        // Ensure content is preserved
        assertThat(gioExternalDefinitions.get("a"))
            .asString()
            .isEqualTo(
                """
                {"type":"boolean"}"""
            );
    }

    @Test
    void should_fail_when_external_schema_is_invalid_json(@TempDir Path tempDir) throws Exception {
        File localSchema = loadResourceAsFile(LOCAL_PROJECT_SCHEMA_FORM);
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        Artifact badArtifact = TestJarArtifactBuilder.builder()
            .tempDir(tempDir)
            .jarName("bad.jar")
            .groupId("com.acme")
            .artifactId("bad")
            .entry("schemas/external/bad.json", "{ not-json ")
            .build()
            .buildArtifact();

        MavenProject project = prepareMavenProject(Set.of(badArtifact));

        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            project,
            Set.of("com.acme:bad"),
            Set.of(),
            localSchema,
            outputFile,
            SupportedFeatures.builder().build()
        );

        assertThatThrownBy(mojo::execute)
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Error occurred during extraction");
    }

    @Test
    void should_fail_when_local_schema_is_missing(@TempDir Path tempDir) {
        File missingLocal = tempDir.resolve("missing.json").toFile();
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            prepareMavenProject(Set.of()),
            Set.of(),
            Set.of(),
            missingLocal,
            outputFile,
            SupportedFeatures.builder().build()
        );

        assertThatThrownBy(mojo::execute)
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Local schema file does not exist");
    }

    @Test
    void should_merge_multiple_includeSchemas_patterns(@TempDir Path tempDir) throws Exception {
        File localSchema = loadResourceAsFile(LOCAL_PROJECT_SCHEMA_FORM);
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        Artifact ext = TestJarArtifactBuilder.builder()
            .tempDir(tempDir)
            .groupId("g")
            .artifactId("ext")
            .entry("schemas/external/s1.json", loadResourceAsString("/external/schema-form-c-key.json"))
            .entry("schemas/other-external/s2.json", loadResourceAsString("/external/schema-form-d-key.json"))
            .build()
            .buildArtifact();

        MavenProject project = prepareMavenProject(Set.of(ext));

        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            project,
            Set.of("g:ext"),
            Set.of("schemas/external/*.json", "schemas/other-external/*.json"),
            localSchema,
            outputFile,
            SupportedFeatures.builder().build()
        );

        mojo.execute();
        JsonNode defs = extractExternalDefinitionsJsonNode(outputFile);
        assertThat(extractJsonKeysInOrder(defs)).containsExactly("a", "b", "c", "d");
    }

    @Test
    void should_ignore_artifact_without_file(@TempDir Path tempDir) throws Exception {
        File localSchema = loadResourceAsFile(LOCAL_PROJECT_SCHEMA_FORM);
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        Artifact noFile = new DefaultArtifact("g", "nf", "1", "compile", "jar", null, new DefaultArtifactHandler("jar"));
        // no call to .setFile() to force .getFile() to return null

        MavenProject project = prepareMavenProject(Set.of(noFile));
        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            project,
            Set.of("g:nf"),
            Set.of(),
            localSchema,
            outputFile,
            SupportedFeatures.builder().build()
        );

        mojo.execute();
        JsonNode defs = extractExternalDefinitionsJsonNode(outputFile);
        assertThat(extractJsonKeysInOrder(defs)).containsExactly("a", "b");
    }

    @Test
    void should_ignore_external_schema_without_gioExternalDefinitions(@TempDir Path tempDir) throws Exception {
        File localSchema = loadResourceAsFile(LOCAL_PROJECT_SCHEMA_FORM);
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        Artifact ext = TestJarArtifactBuilder.builder()
            .tempDir(tempDir)
            .groupId("g")
            .artifactId("x")
            .entry("schemas/external/no-defs.json", loadResourceAsString("/external/schema-form-no-external-def.json"))
            .build()
            .buildArtifact();

        MavenProject project = prepareMavenProject(Set.of(ext));
        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            project,
            Set.of("g:x"),
            Set.of(),
            localSchema,
            outputFile,
            SupportedFeatures.builder().build()
        );

        mojo.execute();
        JsonNode defs = extractExternalDefinitionsJsonNode(outputFile);
        assertThat(extractJsonKeysInOrder(defs)).containsExactly("a", "b");
    }

    @Test
    void should_keep_descriptions_based_on_supported_features_defaults(@TempDir Path tempDir) throws Exception {
        // Given: local schema with descriptions containing EL / secrets hints
        File localSchema = loadResourceAsFile(LOCAL_PROJECT_SCHEMA_FORM_WITH_DESCRIPTIONS);
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            prepareMavenProject(Set.of()),
            Set.of(),
            Set.of(),
            localSchema,
            outputFile,
            SupportedFeatures.builder().build()
        );

        // When
        mojo.execute();

        // Then
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(outputFile);
        JsonNode props = root.get("properties");
        assertThat(props.get("p1").get("description").asText()).isEqualTo("Some field (Supports EL)");
        assertThat(props.get("p2").get("description").asText()).isEqualTo("Another (Supports EL and secrets)");
        assertThat(props.get("p3").get("description").asText()).isEqualTo("All fields support EL and secrets");
        assertThat(props.get("p4").get("description").asText()).isEqualTo("(Supports EL)");
        assertThat(props.get("p5").get("description").asText()).isEqualTo("  (Supports EL and secrets)  ");
        // Also ensure nested properties up to depth 4 keep their descriptions with defaults
        JsonNode level4 = props
            .get("nested")
            .get("properties")
            .get("level2")
            .get("properties")
            .get("level3")
            .get("properties")
            .get("level4");
        assertThat(level4.get("description").asText()).isEqualTo("Nested field (Supports EL)");
        // And in arrays
        JsonNode arrayInner = props.get("arr").get("items").get("properties").get("inner");
        assertThat(arrayInner.get("description").asText()).isEqualTo("All fields support EL and secrets");
    }

    @Test
    void should_clean_descriptions_when_features_disabled(@TempDir Path tempDir) throws Exception {
        // Given
        File localSchema = loadResourceAsFile(LOCAL_PROJECT_SCHEMA_FORM_WITH_DESCRIPTIONS);
        File outputFile = prepareOutputSchemaFormFile(tempDir);

        SupportedFeatures features = new SupportedFeatures();
        features.setExpressionLanguageOnly(false);
        features.setSecretsAndExpressionLanguage(false);
        CommonSchemaFormBundlerMojo mojo = new CommonSchemaFormBundlerMojo(
            prepareMavenProject(Set.of()),
            Set.of(),
            Set.of(),
            localSchema,
            outputFile,
            SupportedFeatures.builder().expressionLanguageOnly(false).secretsAndExpressionLanguage(false).build()
        );

        // When
        mojo.execute();

        // Then
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(outputFile);
        JsonNode props = root.get("properties");
        assertThat(props.get("p1").get("description").asText()).isEqualTo("Some field");
        assertThat(props.get("p2").get("description").asText()).isEqualTo("Another");
        assertThat(props.get("p3").has("description")).isFalse();
        assertThat(props.get("p4").has("description")).isFalse();
        assertThat(props.get("p5").has("description")).isFalse();
        // Also ensure nested properties are cleaned when features are disabled
        JsonNode level4 = props
            .get("nested")
            .get("properties")
            .get("level2")
            .get("properties")
            .get("level3")
            .get("properties")
            .get("level4");
        assertThat(level4.get("description").asText()).isEqualTo("Nested field");
        // And array items are cleaned accordingly
        JsonNode arrayInner = props.get("arr").get("items").get("properties").get("inner");
        assertThat(arrayInner.has("description")).isFalse();
    }

    private static MavenProject prepareMavenProject(Set<Artifact> artifacts) {
        MavenProject externalMavenProject = new MavenProject();
        externalMavenProject.setArtifacts(artifacts);
        return externalMavenProject;
    }
}
