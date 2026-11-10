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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.shared.dependency.analyzer.asm.VisitClassException;

/**
 * Utility to visit classes in a library given either as a jar file or an exploded directory.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public final class ClassFileVisitorUtils {

    private ClassFileVisitorUtils() {
        // private constructor for utility class
    }

    /**
     * @param url the URL of the jar file or directory to visit
     * @param visitor a {@link org.apache.maven.shared.dependency.analyzer.ClassFileVisitor} object
     * @throws java.io.IOException I/O error or corrupt class file
     */
    public static void accept(URL url, ClassFileVisitor visitor) throws IOException {
        if (url.getPath().endsWith(".jar")) {
            acceptJar(url, visitor);
        } else if (url.getProtocol().equalsIgnoreCase("file")) {
            try {
                File file = new File(new URI(url.toString()));

                if (file.isDirectory()) {
                    acceptDirectory(file.toPath(), visitor);
                } else if (file.exists()) {
                    throw new IllegalArgumentException("Cannot accept visitor on URL: " + url);
                }
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("Cannot accept visitor on URL: " + url, exception);
            }
        } else {
            throw new IllegalArgumentException("Cannot accept visitor on URL: " + url);
        }
    }

    /**
     * Visits the classes of the given jar file or directory. The Maven 4 API hands out the artifacts of a project as
     * {@link Path}s, so the analysis can be done without converting them to URLs first.
     *
     * @param path the path of the jar file or directory to visit
     * @param visitor a {@link org.apache.maven.shared.dependency.analyzer.ClassFileVisitor} object
     * @throws java.io.IOException I/O error or corrupt class file
     */
    public static void accept(Path path, ClassFileVisitor visitor) throws IOException {
        if (Files.isDirectory(path)) {
            acceptDirectory(path, visitor);
        } else if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
            acceptJarFile(path, visitor);
        } else if (Files.exists(path)) {
            throw new IllegalArgumentException("Cannot accept visitor on path: " + path);
        }
    }

    // private methods --------------------------------------------------------

    private static void acceptJar(URL url, ClassFileVisitor visitor) throws IOException {
        try (JarInputStream in = new JarInputStream(url.openStream())) {
            JarEntry entry;
            while ((entry = in.getNextJarEntry()) != null) {
                String name = entry.getName();
                // ignore files like package-info.class and module-info.class
                if (name.endsWith(".class") && name.indexOf('-') == -1) {
                    // Jars(ZIP) always use / as the separator character
                    visitClass(name, in, visitor, '/');
                }
            }
        }
    }

    private static void acceptJarFile(Path file, ClassFileVisitor visitor) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            for (ZipEntry entry : zip.stream().collect(Collectors.toList())) {
                String name = entry.getName();
                // ignore files like package-info.class and module-info.class
                if (name.endsWith(".class") && name.indexOf('-') == -1) {
                    // Jars(ZIP) always use / as the separator character
                    try (InputStream in = zip.getInputStream(entry)) {
                        visitClass(name, in, visitor, '/');
                    }
                }
            }
        }
    }

    private static void acceptDirectory(Path directory, ClassFileVisitor visitor) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            List<Path> classFiles = walk.filter(
                            path -> path.getFileName().toString().endsWith(".class"))
                    .collect(Collectors.toList());
            for (Path path : classFiles) {
                try (InputStream in = Files.newInputStream(path)) {
                    try {
                        visitClass(directory, path, in, visitor);
                    } catch (IOException e) {
                        throw new IOException(
                                String.format("%s from directory = %s, path = %s", e.getMessage(), directory, path), e);
                    }
                }
            }
        }
    }

    private static void visitClass(Path baseDirectory, Path path, InputStream in, ClassFileVisitor visitor)
            throws IOException {
        // getPath() returns a String, not a java.nio.file.Path
        String stringPath = path.toFile()
                .getPath()
                .substring(baseDirectory.toFile().getPath().length() + 1);
        visitClass(stringPath, in, visitor, File.separatorChar);
    }

    private static void visitClass(String stringPath, InputStream in, ClassFileVisitor visitor, char separator)
            throws IOException {
        String className = stringPath.substring(0, stringPath.length() - 6);

        className = className.replace(separator, '.');

        try {
            visitor.visitClass(className, in);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } catch (VisitClassException e) {
            throw new IOException(e);
        }
    }
}
