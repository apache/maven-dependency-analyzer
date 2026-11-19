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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class ClassEntries {

    private ClassEntries() {
        // utility class
    }

    static boolean isClassEntry(String name) {
        // the '-' check excludes package-info.class and module-info.class
        return name.endsWith(".class") && name.indexOf('-') == -1;
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    static final class StreamClassEntry implements ClassEntry {
        private final String name;
        private final InputStreamSupplier supplier;

        StreamClassEntry(String name, InputStreamSupplier supplier) {
            this.name = name;
            this.supplier = supplier;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public InputStream open() throws IOException {
            return supplier.get();
        }
    }

    interface InputStreamSupplier {
        InputStream get() throws IOException;
    }
}
