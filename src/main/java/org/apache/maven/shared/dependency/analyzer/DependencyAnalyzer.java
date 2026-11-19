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
import java.net.URL;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.shared.dependency.analyzer.asm.DependencyClassFileVisitor;

/**
 * Gets the set of classes referenced by a library given either as a jar file or an exploded directory.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public interface DependencyAnalyzer {

    /**
     * <p>analyze.</p>
     *
     * @param url            the JAR file or directory to analyze
     * @return the set of class names referenced by the library
     * @throws IOException if an error occurs reading a JAR or .class file
     */
    default Set<String> analyze(URL url) throws IOException {
        return analyze(url, new ClassesPatterns());
    }

    /**
     * <p>analyze.</p>
     *
     * @param url            the JAR file or directory to analyze
     * @param excludeClasses a class list to exclude
     * @return the set of class names referenced by the library
     * @throws IOException if an error occurs reading a JAR or .class file
     */
    default Set<String> analyze(URL url, ClassesPatterns excludeClasses) throws IOException {
        return analyzeUsages(url, excludeClasses).stream()
                .map(DependencyUsage::getDependencyClass)
                .collect(Collectors.toSet());
    }

    /**
     * <p>analyzeUsages.</p>
     *
     * @param url the JAR file or directory to analyze
     * @param excludeClasses patterns of classes to exclude
     * @return the set of class names referenced by the library, paired with the
     * classes declaring those references.
     * @throws IOException if an error occurs reading a JAR or .class file
     */
    Set<DependencyUsage> analyzeUsages(URL url, ClassesPatterns excludeClasses) throws IOException;

    /**
     * Analyzes a JAR file or directory identified by a local path.
     *
     * @param path the JAR file or directory to analyze
     * @return the set of class names referenced by the library
     * @throws IOException if an error occurs reading a JAR or .class file
     * @since 1.18.0
     */
    default Set<String> analyze(Path path) throws IOException {
        return analyze(path, new ClassesPatterns());
    }

    /**
     * Analyzes a JAR file or directory identified by a local path, excluding matching classes.
     *
     * @param path the JAR file or directory to analyze
     * @param excludeClasses a class list to exclude
     * @return the set of class names referenced by the library
     * @throws IOException if an error occurs reading a JAR or .class file
     * @since 1.18.0
     */
    default Set<String> analyze(Path path, ClassesPatterns excludeClasses) throws IOException {
        return analyzeUsages(path, excludeClasses).stream()
                .map(DependencyUsage::getDependencyClass)
                .collect(Collectors.toSet());
    }

    /**
     * Returns referenced classes paired with their declaring classes for a local path.
     *
     * @param path the JAR file or directory to analyze
     * @param excludeClasses patterns of classes to exclude
     * @return the referenced classes and their declaring classes
     * @throws IOException if an error occurs reading a JAR or .class file
     * @since 1.18.0
     */
    default Set<DependencyUsage> analyzeUsages(Path path, ClassesPatterns excludeClasses) throws IOException {
        DependencyClassFileVisitor visitor = new DependencyClassFileVisitor(excludeClasses);
        ClassFileVisitorUtils.accept(new PathClassEntrySource(path), visitor);
        return visitor.getDependencyUsages();
    }
}
