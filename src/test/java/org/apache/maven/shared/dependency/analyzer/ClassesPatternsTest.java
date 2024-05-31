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

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassesPatternsTest {

    @Test
    void classPatternsTest() {
        ClassesPatterns classesPatterns = new ClassesPatterns(Arrays.asList("Test1.*", "io.example.test.Test2"));

        assertTrue(classesPatterns.isMatch("Test1.Test2"));
        assertFalse(classesPatterns.isMatch("Test2.Test2"));
        assertTrue(classesPatterns.isMatch("io.example.test.Test2"));
    }

    @Test
    void emptyClassPatternsTest() {
        ClassesPatterns classesPatterns = new ClassesPatterns();

        assertFalse(classesPatterns.isMatch("Test"));
    }

    @Test
    void nullClassPatternsTest() {
        ClassesPatterns classesPatterns = new ClassesPatterns(null);

        assertFalse(classesPatterns.isMatch("Test"));
    }
}
