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

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests <code>CollectorClassFileVisitor</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see CollectorClassFileVisitor
 */
class CollectorClassFileVisitorTest {
    private CollectorClassFileVisitor visitor;

    @BeforeEach
    void setUp() {
        visitor = new CollectorClassFileVisitor();
    }

    @Test
    void testVisitClass() {
        visitor.visitClass("a.b.c", null);
        visitor.visitClass("x.y.z", null);

        Set<String> expected = new HashSet<>();
        expected.add("a.b.c");
        expected.add("x.y.z");

        assertThat(visitor.getClasses()).isEqualTo(expected);
    }
}
