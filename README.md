# Gravitee – Plugin Common Configurations Maven Plugin

[![Gravitee.io](https://img.shields.io/static/v1?label=Available%20at&message=Gravitee.io&color=1EC9D2)](https://download.gravitee.io/#graviteeio-apim/plugins/policies/gravitee-plugin-common-configurations-maven-plugin/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/gravitee-plugin-common-configurations-maven-plugin/blob/master/LICENSE.txt)
[![Releases](https://img.shields.io/badge/semantic--release-conventional%20commits-e10079?logo=semantic-release)](https://github.com/gravitee-io/gravitee-plugin-common-configurations-maven-plugin/releases)
[![CircleCI](https://circleci.com/gh/gravitee-io/gravitee-plugin-common-configurations-maven-plugin.svg?style=svg)](https://circleci.com/gh/gravitee-io/gravitee-plugin-common-configurations-maven-plugin)


This Maven plugin bundles JSON Schema fragments coming from your project and from selected dependencies into a single, ready‑to‑ship `schema-form.json`. It is designed for Gravitee plugins (policies, resources, endpoints) that expose a configuration UI based on JSON Schema.

At build time, the plugin:
- reads your local `schema-form.json` (the one in your plugin’s `src/main/resources/schemas/`),
- scans selected dependency JARs for JSON files matching include patterns (by default `schemas/**/*.json`),
- extracts a specific field named `gioExternalDefinitions` from those files,
- merges all extracted definitions with your local ones into a single, sorted `gioExternalDefinitions` object written to your build output.

The result is a deterministic, merged schema that your plugin packages in its final artifact. Local definitions always win on key conflicts.


## Why in Gravitee? The http-proxy use case

Several Gravitee plugins share common configuration building blocks (HTTP client, proxy, SSL, etc.). Instead of duplicating schema definitions everywhere, we publish these common blocks in dedicated artifacts. Your plugin can then “import” them during the build.

For instance, the http‑proxy related components reuse common HTTP client and SSL configuration schemas. By adding those provider artifacts to your plugin’s dependencies and by configuring this Maven plugin to include them, you automatically bundle their `gioExternalDefinitions` into your own `schema-form.json`. This keeps your local schema concise, while guaranteeing consistency across plugins.


## How it works (concepts)

- Local schema: your plugin’s `src/main/resources/schemas/schema-form.json` must be a JSON object. If it does not contain a `gioExternalDefinitions` field, the plugin will create an empty one before merging.
- External schemas: JSON files inside dependency JARs matching the configured `includeSchemas` patterns. If a file contains a top‑level field `gioExternalDefinitions` and it is a JSON object, its entries are merged into the final output.
- Conflict resolution: when the same key exists in both external and local `gioExternalDefinitions`, the local value overrides the external one.
- Deterministic output: the final `gioExternalDefinitions` keys are sorted alphabetically to produce a stable file across builds.


## Goal and lifecycle

- Goal: `common-schema-form-bundle`
- Default phase: `process-resources`
- Dependency resolution: compile scope


## Configuration parameters

- includeArtifacts (Set<String>)
  - Format: `groupId:artifactId` (e.g., `io.gravitee:gravitee-plugin-common-configurations`)
  - Which artifacts to scan for external schemas.
  - Default: if omitted or empty, all resolved artifacts are included.

- includeSchemas (Set<String>)
  - Ant‑style include patterns applied to paths inside JARs.
  - Example values: `schemas/**/*.json`, `schemas/external/*.json`
  - Default: `schemas/**/*.json` (if not specified).

- localSchemaFile (File)
  - Path to your local schema to be augmented.
  - Default: `${project.basedir}/src/main/resources/schemas/schema-form.json`.

- outputFile (File)
  - Where the merged schema file is written.
  - Default: `${project.build.outputDirectory}/schemas/schema-form.json`.


## Minimal example

Add the plugin to your plugin project (policy/resource/endpoint):

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.gravitee</groupId>
      <artifactId>gravitee-plugin-common-configurations-maven-plugin</artifactId>
      <version>${project.version}</version>
      <executions>
        <execution>
          <goals>
            <goal>common-schema-form-bundle</goal>
          </goals>
          <configuration>
            <!-- Option 1: include everything resolved at compile scope -->
            <!-- <includeArtifacts/> -->

            <!-- Option 2: restrict to specific providers -->
            <includeArtifacts>
              <includeArtifact>io.gravitee:gravitee-plugin-common-configurations</includeArtifact>
              <!-- add more if needed -->
            </includeArtifacts>

            <!-- Optional: refine which files in JARs are scanned -->
            <includeSchemas>
              <includeSchema>schemas/**/*.json</includeSchema>
            </includeSchemas>

            <!-- Optional: use defaults unless you need custom paths -->
            <!-- <localSchemaFile>${project.basedir}/src/main/resources/schemas/schema-form.json</localSchemaFile> -->
            <!-- <outputFile>${project.build.outputDirectory}/schemas/schema-form.json</outputFile> -->
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Make sure the provider artifact(s) are regular dependencies of your plugin so Maven resolves and provides their JARs to this Mojo:

```xml
<dependencies>
  <dependency>
    <groupId>io.gravitee</groupId>
    <artifactId>gravitee-plugin-common-configurations</artifactId>
    <version>${project.version}</version>
  </dependency>
</dependencies>
```


## Local schema requirements

Your local schema file must be valid JSON and a JSON object. If the `gioExternalDefinitions` field is absent, the plugin adds an empty object before merging. The plugin writes the merged, pretty‑printed schema to `outputFile`, creating the parent directory if necessary.


## Failure modes and logs

- Missing local file: the build fails with a clear error.
- Invalid local JSON or non‑object: the build fails.
- Invalid JSON in external files: the build fails with a message indicating which artifact entry caused the issue.
- Artifacts without files or missing JARs: silently ignored.
- Keys in the final `gioExternalDefinitions` are sorted for stable diffs and reproducibility.


## Tips

- Keep your local schema focused on plugin‑specific fields; offload shared blocks (HTTP, SSL, proxy, etc.) to provider artifacts and import them via this plugin.
- Narrow `includeArtifacts` to exactly what you need to minimize I/O and avoid accidental imports.
- Use multiple `includeSchemas` patterns if your providers place schemas in different folders (e.g., `schemas/external/*.json`, `schemas/other-external/*.json`).
