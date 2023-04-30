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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import org.apache.maven.shared.dependency.analyzer.testcases.ArrayCases;
import org.apache.maven.shared.dependency.analyzer.testcases.InnerClassCase;
import org.apache.maven.shared.dependency.analyzer.testcases.MethodHandleCases;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultCollectorTest {
    Set<String> getDependencies(Class<?> inspectClass) throws IOException {
        String className = inspectClass.getName();
        String path = '/' + className.replace('.', '/') + ".class";
        DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
        try (InputStream is = inspectClass.getResourceAsStream(path)) {
            visitor.visitClass(className, is);
        }
        return visitor.getDependencies();
    }

    @Test
    void testJava11Invoke() throws IOException {
        String className = "issue362.Bcel362";
        Path path = Paths.get(
                "src/test/resources/org/apache/maven/shared/dependency/analyzer/commons-bcel-issue362/Bcel362.classx");
        DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
        try (InputStream is = Files.newInputStream(path)) {
            visitor.visitClass(className, is);
        }
    }

    @Test
    void testArrayCases() throws IOException {
        Set<String> dependencies = getDependencies(ArrayCases.class);
        assertThat(dependencies).doesNotContain("[I");
        assertThat(dependencies).allSatisfy(dependency -> assertThat(dependency).doesNotStartWith("["));
        assertThat(dependencies).contains("java.lang.annotation.Annotation").contains("java.lang.reflect.Constructor");
    }

    @Test
    void testNoMethodHandle() throws IOException {
        Set<String> dependencies = getDependencies(MethodHandleCases.class);
        for (String dependency : dependencies) {
            assertThat(dependency).doesNotStartWith("(");
        }
    }

    @Test
    void testInnerClassAsContainer() throws IOException {
        Set<String> dependencies = getDependencies(InnerClassCase.class);
        for (String dependency : dependencies) {
            assertThat(dependency).doesNotContain("$");
        }
        assertThat(dependencies).contains("java.lang.System");
    }
}
