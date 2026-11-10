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

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Set;

import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.shared.dependency.analyzer.ClassEntrySource;
import org.apache.maven.shared.dependency.analyzer.ClassFileVisitorUtils;
import org.apache.maven.shared.dependency.analyzer.ClassesPatterns;
import org.apache.maven.shared.dependency.analyzer.DependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DependencyUsage;
import org.apache.maven.shared.dependency.analyzer.PathClassEntrySource;
import org.apache.maven.shared.dependency.analyzer.UrlClassEntrySource;

/**
 * ASMDependencyAnalyzer
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
@Named
@Singleton
public class ASMDependencyAnalyzer implements DependencyAnalyzer {

    /**
     * Analyzes usages in the classes exposed by a source. Uses the same visitor as the URL form.
     *
     * @param source the class entry source to analyze
     * @param excludeClasses patterns of classes to exclude
     * @return the referenced classes and their declaring classes
     * @throws IOException if the source cannot be read
     * @since 1.18.0
     */
    public Set<DependencyUsage> analyzeUsages(ClassEntrySource source, ClassesPatterns excludeClasses)
            throws IOException {
        DependencyClassFileVisitor visitor = new DependencyClassFileVisitor(excludeClasses);

        ClassFileVisitorUtils.accept(source, visitor);

        return visitor.getDependencyUsages();
    }

    @Override
    public Set<DependencyUsage> analyzeUsages(URL url, ClassesPatterns excludeClasses) throws IOException {
        return analyzeUsages(new UrlClassEntrySource(url), excludeClasses);
    }

    /** {@inheritDoc} */
    @Override
    public Set<DependencyUsage> analyzeUsages(Path path, ClassesPatterns excludeClasses) throws IOException {
        return analyzeUsages(new PathClassEntrySource(path), excludeClasses);
    }
}
