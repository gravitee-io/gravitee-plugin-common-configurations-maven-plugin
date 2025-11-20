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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class providing helper methods for file operations.
 */
public class FileHelpers {

    /**
     * Prepares and returns a file object pointing to the schema form file within the provided temporary directory.
     * It creates a path with "schemas/schema-form.json" under the specified tempDir and converts it to a File object.
     *
     * @param tempDir the base temporary directory where the schema form file path is to be resolved
     * @return the file object pointing to the resolved "schema-form.json" file
     */
    public static File prepareOutputSchemaFormFile(Path tempDir) {
        return tempDir.resolve("schemas").resolve("schema-form.json").toFile();
    }

    /**
     * Loads a resource from the specified file path and returns it as a File object.
     * The method ensures that the resource exists and verifies its presence on the test classpath.
     *
     * @param filePath the relative path to the resource file to be loaded
     * @return a File object representing the resource
     * @throws URISyntaxException if the resource's URL cannot be converted to a URI
     */
    public static File loadResourceAsFile(String filePath) throws URISyntaxException {
        URL resource = FileHelpers.class.getResource(filePath);
        assertThat(resource).as(filePath + " should be on test classpath").isNotNull();
        return new File(resource.toURI());
    }

    /**
     * Loads the content of a resource file from the specified file path and returns it as a String.
     * The method ensures that the resource exists and verifies its presence on the test classpath.
     *
     * @param filePath the relative path to the resource file to be loaded
     * @return the content of the resource file as a String
     * @throws URISyntaxException if the resource's URL cannot be converted to a URI
     * @throws IOException if an I/O error occurs while reading the resource file
     */
    public static String loadResourceAsString(String filePath) throws URISyntaxException, IOException {
        URL resource = FileHelpers.class.getResource(filePath);
        assertThat(resource).as(filePath + " should be on test classpath").isNotNull();
        return Files.readString(Paths.get(resource.toURI()));
    }
}
