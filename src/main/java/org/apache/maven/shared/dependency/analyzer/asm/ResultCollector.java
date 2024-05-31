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
package org.apache.maven.shared.dependency.analyzer.asm;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.shared.dependency.analyzer.DependencyUsage;
import org.objectweb.asm.Type;

/**
 * <p>ResultCollector class.</p>
 *
 * @author Kristian Rosenvold
 */
public class ResultCollector {

    private final Set<DependencyUsage> classUsages = new HashSet<>();

    /**
     * <p>getDependencies.</p>
     *
     * @return a {@link java.util.Set} object.
     */
    public Set<String> getDependencies() {
        return getDependencyUsages().stream()
                .map(DependencyUsage::getDependencyClass)
                .collect(Collectors.toSet());
    }

    /**
     * <p>getDependencyUsages.</p>
     *
     * @return a {@link java.util.Set} object.
     */
    public Set<DependencyUsage> getDependencyUsages() {
        return classUsages;
    }

    /**
     * <p>addName.</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public void addName(final String usedByClass, String name) {
        if (name == null) {
            return;
        }

        // decode arrays
        if (name.charAt(0) == '[') {
            int i = 0;
            do {
                ++i;
            } while (name.charAt(i) == '['); // could have array of array ...
            if (name.charAt(i) != 'L') {
                // ignore array of scalar types
                return;
            }
            name = name.substring(i + 1, name.length() - 1);
        }

        // decode internal representation
        add(usedByClass, name.replace('/', '.'));
    }

    void addDesc(final String usedByClass, final String desc) {
        addType(usedByClass, Type.getType(desc));
    }

    void addType(final String usedByClass, final Type t) {
        switch (t.getSort()) {
            case Type.ARRAY:
                addType(usedByClass, t.getElementType());
                break;

            case Type.METHOD:
                addMethodDesc(t.getDescriptor());
                break;

            case Type.OBJECT:
                addName(usedByClass, t.getClassName());
                break;
            default:
        }
    }

    /**
     * <p>add.</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public void add(final String usedByClass, final String name) {
        // inner classes have equivalent compilation requirement as container class
        if (name.indexOf('$') < 0) {
            classUsages.add(new DependencyUsage(name, usedByClass));
        }
    }

    void addNames(final String usedByClass, final String[] names) {
        if (names == null) {
            return;
        }

        for (String name : names) {
            addName(usedByClass, name);
        }
    }

    void addMethodDesc(final String usedByClass, final String desc) {
        addType(usedByClass, Type.getReturnType(desc));

        Type[] types = Type.getArgumentTypes(desc);

        for (Type type : types) {
            addType(usedByClass, type);
        }
    }
}
