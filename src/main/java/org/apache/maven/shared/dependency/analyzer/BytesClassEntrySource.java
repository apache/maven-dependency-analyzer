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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;

/**
 * A {@link ClassEntrySource} backed by the bytes of a JAR held in memory.
 *
 * @since 1.18.0
 */
public final class BytesClassEntrySource implements ClassEntrySource {

    private final byte[] bytes;

    /**
     * Creates a source over the bytes of a JAR file.
     *
     * @param bytes the JAR contents, must not be {@code null}
     */
    public BytesClassEntrySource(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes are required");
        }
        this.bytes = bytes;
    }

    @Override
    public Stream<ClassEntry> classes() throws IOException {
        List<ClassEntry> entries = new ArrayList<>();
        try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(bytes))) {
            JarEntry entry;
            while ((entry = in.getNextJarEntry()) != null) {
                if (ClassEntries.isClassEntry(entry.getName())) {
                    byte[] content = ClassEntries.readAll(in);
                    entries.add(new ClassEntries.StreamClassEntry(
                            entry.getName(), () -> new ByteArrayInputStream(content)));
                }
            }
        }
        return entries.stream();
    }
}
