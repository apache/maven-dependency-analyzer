/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.shared.dependency.analyzer.asm;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

import org.apache.maven.shared.dependency.analyzer.ClassesPatterns;
import org.apache.maven.shared.dependency.analyzer.DependencyAnalyzer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ASMDependencyAnalyzerTest {
    private final DependencyAnalyzer analyzer = new ASMDependencyAnalyzer();

    @Test
    void test() throws Exception {
        URL jarUrl = this.getClass().getResource("/org/objectweb/asm/ClassReader.class");
        assertThat(jarUrl).isNotNull();
        String fileUrl =
                jarUrl.toString().substring("jar:".length(), jarUrl.toString().indexOf("!/"));

        Set<String> result = analyzer.analyze(new URL(fileUrl));

        assertThat(result).isNotEmpty();
    }

    @Test
    void verifyImplicitClassReferenceIncludedInUsedClasses() throws Exception {
        Path file = Paths.get("target/test-classes/org/apache/maven/shared/dependency/analyzer/testcases/analyze");

        Set<String> result = analyzer.analyze(file.toUri().toURL());
        assertThat(result).contains("org.apache.maven.artifact.resolver.ArtifactResolutionRequest");
        assertThat(result).contains("java.util.regex.Pattern");
    }

    @Test
    void verifyExcludedClasses() throws Exception {
        Path file = Paths.get("target/test-classes/org/apache/maven/shared/dependency/analyzer/testcases/analyze");

        Set<String> result =
                analyzer.analyze(file.toUri().toURL(), new ClassesPatterns(Collections.singleton("ClassToExclude")));
        assertThat(result).contains("org.apache.maven.artifact.resolver.ArtifactResolutionRequest");
        assertThat(result).doesNotContain("java.util.regex.Pattern");
        assertThat(result)
                .doesNotContain("org.apache.maven.shared.dependency.analyzer.testcases.analyze.ClassToExclude");
    }
}
