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
package io.gravitee.plugin.maven.fixtures;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.Builder;
import lombok.Singular;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;

/**
 * Small testing utility to build a JAR with arbitrary entries and create a Maven
 * {@link org.apache.maven.artifact.Artifact} pointing to that JAR.
 */
@Builder
public class TestJarArtifactBuilder {

    /** Temporary directory where the JAR will be written. */
    private final Path tempDir;

    /** Name of the JAR file to create. Defaults to "external-schemas.jar" if not provided. */
    @Builder.Default
    private final String jarName = "external-schemas.jar";

    /**
     * Entries to write into the JAR: internal path -> content (UTF-8).
     */
    @Singular("entry")
    private final Map<String, String> entries;

    // Maven Artifact info
    private final String groupId;
    private final String artifactId;

    @Builder.Default
    private final String version = "1.0.0";

    /**
     * Builds the JAR on disk and returns the resulting file.
     */
    public File buildJar() throws IOException {
        Objects.requireNonNull(tempDir, "tempDir must be provided");
        Map<String, String> toWrite = (entries == null) ? new LinkedHashMap<>() : entries;
        File jarFile = tempDir.resolve(jarName != null ? jarName : "external-schemas.jar").toFile();
        if (jarFile.getParentFile() != null) {
            jarFile.getParentFile().mkdirs();
        }
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(jarFile))) {
            for (Map.Entry<String, String> e : toWrite.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                byte[] bytes = e.getValue() == null ? new byte[0] : e.getValue().getBytes(StandardCharsets.UTF_8);
                zos.write(bytes);
                zos.closeEntry();
            }
        }
        return jarFile;
    }

    /**
     * Builds the JAR and returns a Maven Artifact pointing to that JAR.
     */
    public Artifact buildArtifact() throws IOException {
        Objects.requireNonNull(groupId, "groupId must be provided");
        Objects.requireNonNull(artifactId, "artifactId must be provided");
        File jar = buildJar();
        DefaultArtifact artifact = new DefaultArtifact(
            groupId,
            artifactId,
            version,
            "compile",
            "jar",
            null,
            new DefaultArtifactHandler("jar")
        );
        artifact.setFile(jar);
        return artifact;
    }
}
