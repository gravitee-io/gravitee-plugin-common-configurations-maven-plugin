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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.plugin.maven.CommonSchemaFormBundlerMojo;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for working with {@link JsonNode} instances,
 * providing methods for extracting keys and specific nodes in JSON structures.
 */
public class JsonNodeHelpers {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Extracts all the keys from the given {@link JsonNode} in the order they appear and returns them as a list.
     * The method iterates over the fields of the provided JSON node and collects their names.
     *
     * @param gioExternalDefinitions the {@link JsonNode} from which to extract keys
     * @return a list of strings representing the keys present in the given {@link JsonNode}, in their original order
     */
    public static List<String> extractJsonKeysInOrder(JsonNode gioExternalDefinitions) {
        List<String> keys = new ArrayList<>();
        gioExternalDefinitions.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    /**
     * Extracts the JSON node corresponding to the external definitions field from the provided file.
     * This method reads the JSON structure from the specified file, ensures the file is valid and
     * contains the required external definitions field, and returns the corresponding JSON node.
     *
     * @param outputFile the file containing the JSON structure to be parsed
     * @return the {@link JsonNode} corresponding to the external definitions field in the file
     * @throws IOException if an error occurs while reading the file or parsing the JSON
     */
    public static JsonNode extractExternalDefinitionsJsonNode(File outputFile) throws IOException {
        assertThat(outputFile).exists().isFile();
        JsonNode root = MAPPER.readTree(outputFile);
        assertThat(root.get(CommonSchemaFormBundlerMojo.EXTERNAL_DEFINITIONS_JSON_FIELD)).isNotNull();
        return root.get(CommonSchemaFormBundlerMojo.EXTERNAL_DEFINITIONS_JSON_FIELD);
    }
}
