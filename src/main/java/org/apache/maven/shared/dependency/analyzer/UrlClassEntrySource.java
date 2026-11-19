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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * A {@link ClassEntrySource} backed by a URL. {@code file:} URLs delegate to
 * {@link PathClassEntrySource}; other protocols are read into memory and wrapped in a
 * {@link BytesClassEntrySource}.
 *
 * @since 1.18.0
 */
public final class UrlClassEntrySource implements ClassEntrySource {

    private final URL url;
    private ClassEntrySource delegate;

    /**
     * Creates a source for a URL.
     *
     * @param url the URL to read, must not be {@code null}
     */
    public UrlClassEntrySource(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("url is required");
        }
        this.url = url;
    }

    @Override
    public Stream<ClassEntry> classes() throws IOException {
        if ("file".equalsIgnoreCase(url.getProtocol())) {
            try {
                Path path = Paths.get(url.toURI());
                if (!Files.exists(path)) {
                    // The URL-based API silently ignored a missing path, so keep that behavior.
                    delegate = new ClassEntrySource() {
                        @Override
                        public Stream<ClassEntry> classes() {
                            return Stream.empty();
                        }
                    };
                } else if (Files.isRegularFile(path)
                        && path.getFileName().toString().endsWith(".jar")) {
                    // Preserve the JarInputStream behavior of the original URL-based API, which
                    // detects corrupt archives that ZipFile silently ignores.
                    try (InputStream in = url.openStream()) {
                        delegate = new BytesClassEntrySource(ClassEntries.readAll(in));
                    }
                } else {
                    delegate = new PathClassEntrySource(path);
                }
            } catch (URISyntaxException e) {
                throw new IOException("Invalid file URL: " + url, e);
            }
        } else {
            try (InputStream in = url.openStream()) {
                delegate = new BytesClassEntrySource(ClassEntries.readAll(in));
            }
        }
        return delegate.classes();
    }

    @Override
    public void close() throws IOException {
        if (delegate != null) {
            delegate.close();
        }
    }
}
