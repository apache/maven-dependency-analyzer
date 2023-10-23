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

    private static String ROOT = "src/test/resources/org/apache/maven/shared/dependency/analyzer";

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
        Path path = Paths.get(
                "src/test/resources/org/apache/maven/shared/dependency/analyzer/commons-bcel-issue362/Bcel362.classx");
        DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
        try (InputStream is = Files.newInputStream(path)) {
            visitor.visitClass("issue362.Bcel362", is);
        }
    }

    @Test
    public void testOssFuzz51980() throws IOException {
        // Add a non-"class" suffix so that surefire does not try to read the file and fail the build
        visitClass(ROOT + "/ossfuzz/issue51980/Test.class.clazz");
    }

    @Test
    public void testOssFuzz51989() throws IOException {
        visitClass(ROOT + "/ossfuzz/issue51989/Test.class.clazz");
    }

    @Test
    public void testOssFuzz52168() throws IOException {
        visitClass(ROOT + "/ossfuzz/issue52168/Test.class.clazz");
    }

    @Test
    public void testOssFuzz53543() throws IOException {
        visitClass(ROOT + "/ossfuzz/issue53543/Test.class.clazz");
    }

    @Test
    public void testOssFuzz53544a() throws IOException {
        visitClass(ROOT + "/ossfuzz/issue53544a/Test.class.clazz");
    }

    @Test
    public void testOssFuzz53620() throws IOException {
        visitClass(ROOT + "/ossfuzz/issue53620/Test.class.clazz");
    }

    @Test
    public void testOssFuzz53676() throws IOException {
        visitClass(ROOT + "/ossfuzz/issue53676/Test.class.clazz");
    }

    @Test
    public void testOssFuzz54199() throws IOException {
        visitClass(ROOT + "/ossfuzz/issue54119/Test.class.clazz");
    }

    @Test
    public void testOssFuzz54254() throws IOException {
        visitClass(ROOT + "/ossfuzz/issue54254/Test.class.clazz");
    }

    private void visitClass(String location) throws IOException {
        Path path = Paths.get(location);
        DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
        try (InputStream is = Files.newInputStream(path)) {
            visitor.visitClass("Test", is);
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
