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
package org.apache.maven.shared.dependency.analyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.maven.shared.dependency.analyzer.asm.ASMDependencyAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassEntrySourceTest {

    @TempDir
    private Path tempDir;

    @Test
    void allSourcesProduceTheSameClassNames() throws Exception {
        Path jar = createJar();
        Set<String> expected = new HashSet<>(Arrays.asList("a.b.c", "x.y.z"));

        assertEquals(expected, new HashSet<>(classNames(new PathClassEntrySource(jar))));
        assertEquals(
                expected,
                new HashSet<>(classNames(new UrlClassEntrySource(jar.toUri().toURL()))));
        assertEquals(expected, new HashSet<>(classNames(new BytesClassEntrySource(Files.readAllBytes(jar)))));
    }

    @Test
    void directorySourceProducesTheSameClassNames() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("classes/a/b"));
        Files.write(dir.resolve("c.class"), "class a.b.c".getBytes("UTF-8"));

        Set<String> expected = Collections.singleton("a.b.c");
        assertEquals(
                expected,
                new HashSet<>(
                        classNames(new PathClassEntrySource(dir.getParent().getParent()))));
    }

    @Test
    void analyzerOverloadsMatchUrlForms() throws Exception {
        Path classes = Paths.get("target/test-classes/org/apache/maven/shared/dependency/analyzer/testcases/analyze");
        ClassesPatterns patterns = new ClassesPatterns(Collections.singleton("ClassToExclude"));

        DefaultClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();
        assertEquals(
                classAnalyzer.analyze(classes.toUri().toURL(), patterns),
                classAnalyzer.analyze(new PathClassEntrySource(classes), patterns));
        assertEquals(classAnalyzer.analyze(classes.toUri().toURL()), classAnalyzer.analyze(classes));

        ASMDependencyAnalyzer dependencyAnalyzer = new ASMDependencyAnalyzer();
        assertEquals(
                dependencyAnalyzer.analyzeUsages(classes.toUri().toURL(), patterns),
                dependencyAnalyzer.analyzeUsages(new PathClassEntrySource(classes), patterns));
        assertEquals(
                dependencyAnalyzer.analyzeUsages(classes.toUri().toURL(), patterns),
                dependencyAnalyzer.analyzeUsages(classes, patterns));

        List<String> viaUrl = new ArrayList<>();
        List<String> viaPath = new ArrayList<>();
        ClassFileVisitorUtils.accept(classes.toUri().toURL(), (name, in) -> viaUrl.add(name));
        ClassFileVisitorUtils.accept(classes, (name, in) -> viaPath.add(name));
        assertEquals(viaUrl, viaPath);
    }

    @Test
    void excludesInfoClasses() throws Exception {
        Path jar = createJarWithInfoClasses();

        List<String> names = classNames(new PathClassEntrySource(jar));
        assertTrue(names.contains("a.b.c"));
        assertEquals(1, names.size());
    }

    @Test
    void directorySourceKeepsInfoClasses() throws Exception {
        Path classes = Files.createDirectories(tempDir.resolve("project-classes/a/b"));
        Files.write(classes.resolve("c.class"), "class a.b.c".getBytes("UTF-8"));
        Files.write(classes.resolve("package-info.class"), "package".getBytes("UTF-8"));
        Files.write(classes.getParent().getParent().resolve("module-info.class"), "module".getBytes("UTF-8"));

        Set<String> expected = new HashSet<>(Arrays.asList("a.b.c", "a.b.package-info", "module-info"));
        assertEquals(
                expected,
                new HashSet<>(
                        classNames(new PathClassEntrySource(classes.getParent().getParent()))));
    }

    private Path createJar() throws IOException {
        Path jar = Files.createTempFile(tempDir, "classes", ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "a/b/c.class", "class a.b.c");
            addEntry(out, "x/y/z.class", "class x.y.z");
        }
        return jar;
    }

    private Path createJarWithInfoClasses() throws IOException {
        Path jar = Files.createTempFile(tempDir, "info", ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "a/b/c.class", "class a.b.c");
            addEntry(out, "module-info.class", "module");
            addEntry(out, "a/b/package-info.class", "package");
        }
        return jar;
    }

    private static void addEntry(JarOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes("UTF-8"));
        out.closeEntry();
    }

    private static List<String> classNames(ClassEntrySource source) throws IOException {
        List<String> names = new ArrayList<>();
        ClassFileVisitorUtils.accept(source, (name, in) -> names.add(name));
        return names;
    }
}
