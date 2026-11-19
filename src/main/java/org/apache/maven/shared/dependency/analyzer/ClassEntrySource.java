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
import java.util.stream.Stream;

/**
 * A source of class files for dependency analysis. Implementations may read a JAR file, an exploded
 * directory, a URL, or bytes held in memory. The source must be closed after its entries have been
 * consumed; {@link ClassFileVisitorUtils#accept(ClassEntrySource, ClassFileVisitor)} does that for callers.
 *
 * @since 1.18.0
 */
public interface ClassEntrySource extends AutoCloseable {

    /**
     * Returns the class entries in this source. Package-info and module-info classes, and any entry
     * whose name contains a '-', are excluded by the implementations.
     *
     * @return a stream of class entries
     * @throws IOException if the entries cannot be enumerated
     */
    Stream<ClassEntry> classes() throws IOException;

    /**
     * Releases resources held by this source. The default implementation does nothing.
     *
     * @throws IOException if the resources cannot be released
     */
    @Override
    default void close() throws IOException {
        // no resources by default
    }
}
