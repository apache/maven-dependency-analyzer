<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Apache Maven Dependency Analyzer

[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/apache/maven.svg?label=License)][license]
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.maven.shared/maven-dependency-analyzer.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.apache.maven.shared/maven-dependency-analyzer)
[![Reproducible Builds](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/jvm-repo-rebuild/reproducible-central/master/content/org/apache/maven/shared/maven-dependency-analyzer/badge.json)](https://github.com/jvm-repo-rebuild/reproducible-central/blob/master/content/org/apache/maven/shared/maven-dependency-analyzer/README.md)
[![Jenkins Build](https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fci-maven.apache.org%2Fjob%2FMaven%2Fjob%2Fmaven-box%2Fjob%2Fmaven-dependency-analyzer%2Fjob%2Fmaster%2F)][build]
[![Jenkins Tests](https://img.shields.io/jenkins/tests?jobUrl=https%3A%2F%2Fci-maven.apache.org%2Fjob%2FMaven%2Fjob%2Fmaven-box%2Fjob%2Fmaven-dependency-analyzer%2Fjob%2Fmaster%2F)][test-results]
[![Java CI](https://github.com/apache/maven-dependency-analyzer/actions/workflows/maven-verify.yml/badge.svg)][gh-build]

Finds the unused declared and the used undeclared dependencies of a Maven project by reading its
compiled classes and those of its dependencies. The
[Maven Dependency Plugin](https://maven.apache.org/plugins/maven-dependency-plugin/) uses it for
`dependency:analyze`; other plugins inject `ProjectDependencyAnalyzer` and call
`analyze(project, dependencies)`.

The library requires Java 17 and builds against the Maven 4 API, which the calling plugin's
Maven runtime provides.

- Documentation: <https://maven.apache.org/shared/maven-dependency-analyzer/>
- Issues: <https://github.com/apache/maven-dependency-analyzer/issues>
- Security reports: see [SECURITY.md](SECURITY.md); never a public issue
- Contributing: see [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md)

## Build

```shell
mvn verify
mvn -P run-its verify
```

[license]: https://www.apache.org/licenses/LICENSE-2.0
[test-results]: https://ci-maven.apache.org/job/Maven/job/maven-box/job/maven-dependency-analyzer/job/master/lastCompletedBuild/testReport/
[build]: https://ci-maven.apache.org/job/Maven/job/maven-box/job/maven-dependency-analyzer/job/master/
[gh-build]: https://github.com/apache/maven-dependency-analyzer/actions/workflows/maven-verify.yml?query=branch%3Amaster
