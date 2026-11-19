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

/**
 * Helpers for converting JVM internal class names to source-style names.
 *
 * @since 1.18.0
 */
public final class ClassNames {

    private ClassNames() {
        // utility class
    }

    /**
     * Converts an internal class name to its outer class name. Slashes are replaced with dots and
     * any inner-class suffix starting at the first {@code $} is removed. Class resource names
     * ending in {@code .class} must have that suffix removed by the caller before calling this method.
     *
     * @param name an internal class name, for example {@code org/apache/Foo$Bar}, or {@code null}
     * @return the normalized name, for example {@code org.apache.Foo}, or {@code null} when the input is {@code null}
     */
    public static String normalizeInternalName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.replace('/', '.');
        int innerClass = normalized.indexOf('$');
        return innerClass < 0 ? normalized : normalized.substring(0, innerClass);
    }
}
