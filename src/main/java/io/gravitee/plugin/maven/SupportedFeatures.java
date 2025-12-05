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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration bean describing which features are supported by the target plugin.
 *
 * Defaults are true so there is no impact for existing consumers.
 *
 * Example Maven configuration:
 * <supportedFeatures>
 *   <expressionLanguageOnly>true</expressionLanguageOnly>
 *   <secretsAndExpressionLanguage>true</secretsAndExpressionLanguage>
 * </supportedFeatures>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportedFeatures {

    /**
     * Whether Expression Language (EL) is supported. Default: true.
     */
    @Builder.Default
    private Boolean expressionLanguageOnly = true;

    /**
     * Whether both secrets and EL are supported. Default: true.
     */
    @Builder.Default
    private Boolean secretsAndExpressionLanguage = true;
}
