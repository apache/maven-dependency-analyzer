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

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

import org.apache.maven.shared.dependency.analyzer.ClassesPatterns;
import org.apache.maven.shared.dependency.analyzer.DependencyAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ASMDependencyAnalyzerTest {
    private final DependencyAnalyzer analyzer = new ASMDependencyAnalyzer();

    @Test
    void test() throws Exception {
        URL jarUrl = this.getClass().getResource("/org/objectweb/asm/ClassReader.class");
        assertNotNull(jarUrl);
        String fileUrl =
                jarUrl.toString().substring("jar:".length(), jarUrl.toString().indexOf("!/"));

        Set<String> result = analyzer.analyze(new URL(fileUrl));

        assertFalse(result.isEmpty());
    }

    @Test
    void verifyImplicitClassReferenceIncludedInUsedClasses() throws Exception {
        Path file = Paths.get("target/test-classes/org/apache/maven/shared/dependency/analyzer/testcases/analyze");

        Set<String> result = analyzer.analyze(file.toUri().toURL());
        assertTrue(result.contains("org.apache.maven.artifact.resolver.ArtifactResolutionRequest"));
        assertTrue(result.contains("java.util.regex.Pattern"));
    }

    @Test
    void verifyExcludedClasses() throws Exception {
        Path file = Paths.get("target/test-classes/org/apache/maven/shared/dependency/analyzer/testcases/analyze");

        Set<String> result =
                analyzer.analyze(file.toUri().toURL(), new ClassesPatterns(Collections.singleton("ClassToExclude")));
        assertTrue(result.contains("org.apache.maven.artifact.resolver.ArtifactResolutionRequest"));
        assertFalse(result.contains("java.util.regex.Pattern"));
        assertFalse(result.contains("org.apache.maven.shared.dependency.analyzer.testcases.analyze.ClassToExclude"));
    }

    @Test
    void packageAnnotationInDirectoryIsAnalyzed(@TempDir Path tempDir) throws Exception {
        Path src = Files.createDirectories(tempDir.resolve("src"));
        Path annotationClasses = Files.createDirectories(tempDir.resolve("annotation-classes"));
        Path classes = Files.createDirectories(tempDir.resolve("classes"));

        write(
                src.resolve("ann/PackageAnnotation.java"),
                "package ann;\n"
                        + "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)\n"
                        + "public @interface PackageAnnotation {}\n");
        write(src.resolve("pkg/package-info.java"), "@ann.PackageAnnotation\npackage pkg;\n");

        compile(annotationClasses, null, src.resolve("ann/PackageAnnotation.java"));
        compile(classes, annotationClasses, src.resolve("pkg/package-info.java"));
        assertTrue(Files.isRegularFile(classes.resolve("pkg/package-info.class")));
        assertFalse(Files.exists(classes.resolve("ann")));

        Set<String> result = analyzer.analyze(classes.toUri().toURL());

        assertTrue(result.contains("ann.PackageAnnotation"), "dependencies found: " + result);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes("UTF-8"));
    }

    private static void compile(Path outputDir, Path classpath, Path source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests need a JDK");
        int status = classpath == null
                ? compiler.run(null, null, null, "-d", outputDir.toString(), source.toString())
                : compiler.run(
                        null, null, null, "-d", outputDir.toString(), "-cp", classpath.toString(), source.toString());
        assertEquals(0, status, "compilation of " + source);
    }
}
