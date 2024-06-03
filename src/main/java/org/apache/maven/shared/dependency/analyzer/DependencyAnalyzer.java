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
import java.util.Set;
import java.util.stream.Collectors;

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
     * @return the set of class names referenced by the library, paired with the
     * classes declaring those references.
     * @throws IOException if an error occurs reading a JAR or .class file
     */
    Set<DependencyUsage> analyzeUsages(URL url, ClassesPatterns excludeClasses) throws IOException;
}
