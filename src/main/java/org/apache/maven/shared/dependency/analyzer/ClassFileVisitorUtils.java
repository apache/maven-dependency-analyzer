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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Iterator;

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
     * Visits the classes exposed by a {@link ClassEntrySource} and closes the source afterwards.
     * This is the common implementation for local paths, URLs and in-memory archives.
     *
     * @param source the class entry source to visit
     * @param visitor a {@link org.apache.maven.shared.dependency.analyzer.ClassFileVisitor} object
     * @throws java.io.IOException I/O error or corrupt class file
     * @since 1.18.0
     */
    public static void accept(ClassEntrySource source, ClassFileVisitor visitor) throws IOException {
        try (ClassEntrySource closeable = source) {
            Iterator<ClassEntry> iterator = source.classes().iterator();
            while (iterator.hasNext()) {
                ClassEntry entry = iterator.next();
                try (InputStream in = entry.open()) {
                    visitClass(entry.name(), in, visitor, '/');
                }
            }
        }
    }

    /**
     * Visits the classes of a local JAR file or directory.
     *
     * @param path the JAR file or directory to visit
     * @param visitor a {@link org.apache.maven.shared.dependency.analyzer.ClassFileVisitor} object
     * @throws java.io.IOException I/O error or corrupt class file
     * @since 1.18.0
     */
    public static void accept(Path path, ClassFileVisitor visitor) throws IOException {
        accept(new PathClassEntrySource(path), visitor);
    }

    /**
     * @param url the URL of the jar file or directory to visit
     * @param visitor a {@link org.apache.maven.shared.dependency.analyzer.ClassFileVisitor} object
     * @throws java.io.IOException I/O error or corrupt class file
     */
    public static void accept(URL url, ClassFileVisitor visitor) throws IOException {
        if (!url.getPath().endsWith(".jar") && !"file".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException("Cannot accept visitor on URL: " + url);
        }
        accept(new UrlClassEntrySource(url), visitor);
    }

    // private methods --------------------------------------------------------

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
