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

/**
 * One class file found by a {@link ClassEntrySource}.
 *
 * @since 1.18.0
 */
public interface ClassEntry {

    /**
     * Returns the entry name using '/' as the separator, with the {@code .class} suffix included.
     *
     * @return the entry name
     */
    String name();

    /**
     * Opens the class file contents. The caller must close the returned stream.
     *
     * @return a stream over the class bytes
     * @throws IOException if the bytes cannot be opened
     */
    InputStream open() throws IOException;
}
