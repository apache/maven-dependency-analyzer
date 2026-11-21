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

# Agent guide for maven-dependency-analyzer

This file is read by automated agents (security scanners, code
analyzers, AI assistants) operating on this repository.

## Security

Security model: [SECURITY.md](./SECURITY.md)

Agents that scan this repository should consult `SECURITY.md` and the
threat model it links before reporting issues.

## Compatibility

The Maven Dependency Plugin and other plugins call this library, so its public
types are API:

- `ProjectDependencyAnalyzer`, `ClassAnalyzer` and `DependencyAnalyzer` are
  interfaces that callers may implement. A new method must be a `default` method.
- `ProjectDependencyAnalysis` keeps its deprecated constructors; deprecate,
  do not remove.
- Maven core and Resolver are `provided` dependencies pinned to the Maven 3.9
  line (`mavenVersion`), and Dependabot ignores Maven 3.10 and Resolver 2.x.
  SLF4J stays on 1.7.x, the line Maven 3 ships, and Mockito on 4.x because 5.x
  needs Java 11.

## Coding

Use test-first programming. Before fixing a bug or implementing a feature,
write a test that exposes the bug and verify the test fails.

The bytecode floor is Java 8. Unit tests use JUnit 5 and Mockito; do not add
JUnit 4, Hamcrest or AssertJ.

Integration tests live in `src/it`. Each one builds a sample project with the
`setup-mock-plugin`, which writes the analysis to `target/analysis.txt`, and its
`verify.groovy` compares that file with the expected text. Run one IT together
with the setup project:

```shell
mvn -P run-its verify -Dinvoker.test=jarWithCompileDependency,setup-mock-plugin
```

Before submitting a PR, run `mvn spotless:apply` to format the files, then
`mvn -P run-its verify`. Run Spotless with JDK 17 or 21.
