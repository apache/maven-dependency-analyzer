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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests <code>ClassFileVisitorUtils</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see ClassFileVisitorUtils
 */
class ClassFileVisitorUtilsTest {

    @TempDir
    private Path tempDir;

    private TestVisitor visitor = new TestVisitor();

    private static class TestVisitor implements ClassFileVisitor {
        final List<String> classNames = new ArrayList<>();

        final List<String> data = new ArrayList<>();

        @Override
        public void visitClass(String className, InputStream in) {
            classNames.add(className);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            reader.lines().forEach(data::add);
        }
    }

    @Test
    void acceptJar() throws Exception {
        Path path = Files.createTempFile(tempDir, "test", ".jar");

        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path))) {
            addZipEntry(out, "a/b/c.class", "class a.b.c");
            addZipEntry(out, "x/y/z.class", "class x.y.z");
        }

        ClassFileVisitorUtils.accept(path.toUri().toURL(), visitor);

        assertThat(visitor.classNames).contains("a.b.c");
        assertThat(visitor.classNames).contains("x.y.z");
        assertThat(visitor.data).contains("class a.b.c");
        assertThat(visitor.data).contains("class x.y.z");
    }

    @Test
    void acceptJarWithNonClassEntry() throws Exception {
        Path path = Files.createTempFile(tempDir, "test", ".jar");

        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path))) {
            addZipEntry(out, "a/b/c.jpg", "jpeg a.b.c");
        }

        ClassFileVisitorUtils.accept(path.toUri().toURL(), visitor);

        assertThat(visitor.classNames).isEmpty();
    }

    @Test
    void acceptDir() throws Exception {
        Path dir = Files.createTempDirectory(tempDir, "d-a-test");

        Path abDir = Files.createDirectories(dir.resolve("a/b"));
        writeToFile(abDir, "c.class", "class a.b.c");

        Path xyDir = Files.createDirectories(dir.resolve("x/y"));
        writeToFile(xyDir, "z.class", "class x.y.z");

        ClassFileVisitorUtils.accept(dir.toUri().toURL(), visitor);

        assertThat(visitor.classNames).contains("a.b.c");
        assertThat(visitor.classNames).contains("x.y.z");
        assertThat(visitor.data).contains("class a.b.c");
        assertThat(visitor.data).contains("class x.y.z");
    }

    @Test
    void acceptDirWithNonClassFile() throws Exception {
        Path dir = Files.createTempDirectory(tempDir, "d-a-test");

        Path abDir = Files.createDirectories(dir.resolve("a/b"));
        writeToFile(abDir, "c.jpg", "jpeg a.b.c");

        ClassFileVisitorUtils.accept(dir.toUri().toURL(), visitor);

        assertThat(visitor.classNames).isEmpty();
    }

    @Test
    void acceptWithFile() throws Exception {
        Path path = Files.createTempFile(tempDir, "test", ".class");
        URL url = path.toUri().toURL();

        try {
            ClassFileVisitorUtils.accept(url, visitor);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessage("Cannot accept visitor on URL: " + url);
        }
    }

    @Test
    void acceptWithUnsupportedScheme() throws Exception {
        URL url = new URL("http://localhost/");

        try {
            ClassFileVisitorUtils.accept(url, visitor);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessage("Cannot accept visitor on URL: " + url);
        }
    }

    private void writeToFile(Path parent, String file, String data) throws IOException {
        Files.write(parent.resolve(file), data.getBytes(StandardCharsets.UTF_8));
    }

    private void addZipEntry(JarOutputStream out, String fileName, String content) throws IOException {
        out.putNextEntry(new ZipEntry(fileName));
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
    }
}
