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

# Contributing

Read the [Maven developer guide](https://maven.apache.org/guides/development/guide-maven-development.html)
before your first pull request. For this repository:

- Report bugs in [GitHub Issues](https://github.com/apache/maven-dependency-analyzer/issues);
  report security issues privately as [SECURITY.md](SECURITY.md) describes.
- Discuss new features and API changes on the [dev mailing list](https://maven.apache.org/mailing-lists.html) first.
- Add a unit test or an integration test under `src/it` for every behavior change.
- Run `mvn spotless:apply` and `mvn -P run-its verify` before you push.
- The pull request title becomes the release note entry, so write it for users.
