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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A {@link ClassEntrySource} backed by a local JAR file or an exploded directory.
 *
 * @since 1.18.0
 */
public final class PathClassEntrySource implements ClassEntrySource {

    private final Path path;
    private ZipFile zipFile;

    /**
     * Creates a source for a JAR file or directory.
     *
     * @param path the path to read, must not be {@code null}
     */
    public PathClassEntrySource(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("path is required");
        }
        this.path = path;
    }

    /**
     * Returns the path being read.
     *
     * @return the path
     */
    public Path getPath() {
        return path;
    }

    @Override
    public Stream<ClassEntry> classes() throws IOException {
        if (Files.isDirectory(path)) {
            return directoryEntries();
        }
        if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
            return jarEntries();
        }
        throw new IllegalArgumentException("Cannot create a class entry source from path: " + path);
    }

    private Stream<ClassEntry> directoryEntries() throws IOException {
        List<Path> classFiles;
        try (Stream<Path> walk = Files.walk(path)) {
            classFiles = walk.filter(Files::isRegularFile)
                    .filter(file -> ClassEntries.isClassEntry(file.getFileName().toString()))
                    .collect(Collectors.toList());
        }
        List<ClassEntry> entries = new ArrayList<>(classFiles.size());
        for (Path file : classFiles) {
            String name = path.relativize(file).toString().replace(java.io.File.separatorChar, '/');
            entries.add(new ClassEntries.StreamClassEntry(name, () -> Files.newInputStream(file)));
        }
        return entries.stream();
    }

    private Stream<ClassEntry> jarEntries() throws IOException {
        final ZipFile jar = new ZipFile(path.toFile());
        zipFile = jar;
        List<ClassEntry> entries = new ArrayList<>();
        Enumeration<? extends ZipEntry> enumeration = jar.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            if (ClassEntries.isClassEntry(entry.getName())) {
                entries.add(new ClassEntries.StreamClassEntry(entry.getName(), () -> jar.getInputStream(entry)));
            }
        }
        return entries.stream();
    }

    @Override
    public void close() throws IOException {
        if (zipFile != null) {
            zipFile.close();
            zipFile = null;
        }
    }
}
