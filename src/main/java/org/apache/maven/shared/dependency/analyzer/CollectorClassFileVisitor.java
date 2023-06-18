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

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Simply collects the set of visited classes.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see #getClasses()
 */
public class CollectorClassFileVisitor implements ClassFileVisitor {
    private final Set<String> classes;

    /**
     * <p>Constructor for CollectorClassFileVisitor.</p>
     */
    public CollectorClassFileVisitor() {
        classes = new HashSet<>();
    }

    /** {@inheritDoc} */
    @Override
    public void visitClass(String className, InputStream in) {
        // inner classes have equivalent compilation requirement as container class
        if (className.indexOf('$') < 0) {
            classes.add(className);
        }
    }

    /**
     * <p>Getter for the field <code>classes</code>.</p>
     *
     * @return a {@link java.util.Set} object.
     */
    public Set<String> getClasses() {
        return classes;
    }
}
