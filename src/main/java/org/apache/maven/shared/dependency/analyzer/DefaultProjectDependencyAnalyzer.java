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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

/**
 * <p>DefaultProjectDependencyAnalyzer class.</p>
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
@Named
@Singleton
public class DefaultProjectDependencyAnalyzer implements ProjectDependencyAnalyzer {
    /**
     * ClassAnalyzer
     */
    @Inject
    private ClassAnalyzer classAnalyzer;

    /**
     * DependencyAnalyzer
     */
    @Inject
    private DependencyAnalyzer dependencyAnalyzer;

    /** {@inheritDoc} */
    @Override
    public ProjectDependencyAnalysis analyze(MavenProject project, Collection<String> excludedClasses)
            throws ProjectDependencyAnalyzerException {
        try {
            ClassesPatterns excludedClassesPatterns = new ClassesPatterns(excludedClasses);
            Map<Artifact, Set<String>> artifactClassMap = buildArtifactClassMap(project, excludedClassesPatterns);

            Set<DependencyUsage> mainDependencyClasses = buildMainDependencyClasses(project, excludedClassesPatterns);
            Set<DependencyUsage> testDependencyClasses = buildTestDependencyClasses(project, excludedClassesPatterns);

            Set<DependencyUsage> dependencyClasses = new HashSet<>();
            dependencyClasses.addAll(mainDependencyClasses);
            dependencyClasses.addAll(testDependencyClasses);

            Set<DependencyUsage> testOnlyDependencyClasses =
                    buildTestOnlyDependencyClasses(mainDependencyClasses, testDependencyClasses);

            Map<Artifact, Set<DependencyUsage>> usedArtifacts = buildUsedArtifacts(artifactClassMap, dependencyClasses);
            Set<Artifact> mainUsedArtifacts =
                    buildUsedArtifacts(artifactClassMap, mainDependencyClasses).keySet();

            Set<Artifact> testArtifacts = buildUsedArtifacts(artifactClassMap, testOnlyDependencyClasses)
                    .keySet();
            Set<Artifact> testOnlyArtifacts = removeAll(testArtifacts, mainUsedArtifacts);

            Set<Artifact> declaredArtifacts = buildDeclaredArtifacts(project);
            Set<Artifact> usedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
            usedDeclaredArtifacts.retainAll(usedArtifacts.keySet());

            Map<Artifact, Set<DependencyUsage>> usedDeclaredArtifactsWithClasses = new LinkedHashMap<>();
            for (Artifact a : usedDeclaredArtifacts) {
                usedDeclaredArtifactsWithClasses.put(a, usedArtifacts.get(a));
            }

            Map<Artifact, Set<DependencyUsage>> usedUndeclaredArtifactsWithClasses = new LinkedHashMap<>(usedArtifacts);
            Set<Artifact> usedUndeclaredArtifacts =
                    removeAll(usedUndeclaredArtifactsWithClasses.keySet(), declaredArtifacts);

            usedUndeclaredArtifactsWithClasses.keySet().retainAll(usedUndeclaredArtifacts);

            Set<Artifact> unusedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
            unusedDeclaredArtifacts = removeAll(unusedDeclaredArtifacts, usedArtifacts.keySet());

            Set<Artifact> testArtifactsWithNonTestScope = getTestArtifactsWithNonTestScope(testOnlyArtifacts);

            return new ProjectDependencyAnalysis(
                    usedDeclaredArtifactsWithClasses, usedUndeclaredArtifactsWithClasses,
                    unusedDeclaredArtifacts, testArtifactsWithNonTestScope);
        } catch (IOException exception) {
            throw new ProjectDependencyAnalyzerException("Cannot analyze dependencies", exception);
        }
    }

    /**
     * This method defines a new way to remove the artifacts by using the conflict id. We don't care about the version
     * here because there can be only 1 for a given artifact anyway.
     *
     * @param start  initial set
     * @param remove set to exclude
     * @return set with remove excluded
     */
    private static Set<Artifact> removeAll(Set<Artifact> start, Set<Artifact> remove) {
        Set<Artifact> results = new LinkedHashSet<>(start.size());

        for (Artifact artifact : start) {
            boolean found = false;

            for (Artifact artifact2 : remove) {
                if (artifact.getDependencyConflictId().equals(artifact2.getDependencyConflictId())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                results.add(artifact);
            }
        }

        return results;
    }

    private static Set<Artifact> getTestArtifactsWithNonTestScope(Set<Artifact> testOnlyArtifacts) {
        Set<Artifact> nonTestScopeArtifacts = new LinkedHashSet<>();

        for (Artifact artifact : testOnlyArtifacts) {
            if (artifact.getScope().equals("compile")) {
                nonTestScopeArtifacts.add(artifact);
            }
        }

        return nonTestScopeArtifacts;
    }

    protected Map<Artifact, Set<String>> buildArtifactClassMap(MavenProject project, ClassesPatterns excludedClasses)
            throws IOException {
        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();

        Set<Artifact> dependencyArtifacts = project.getArtifacts();

        for (Artifact artifact : dependencyArtifacts) {
            File file = artifact.getFile();

            if (file != null && file.getName().endsWith(".jar")) {
                // optimized solution for the jar case

                try (JarFile jarFile = new JarFile(file)) {
                    Enumeration<JarEntry> jarEntries = jarFile.entries();

                    Set<String> classes = new HashSet<>();

                    while (jarEntries.hasMoreElements()) {
                        String entry = jarEntries.nextElement().getName();
                        if (entry.endsWith(".class")) {
                            String className = entry.replace('/', '.');
                            className = className.substring(0, className.length() - ".class".length());
                            if (!excludedClasses.isMatch(className)) {
                                classes.add(className);
                            }
                        }
                    }

                    artifactClassMap.put(artifact, classes);
                }
            } else if (file != null && file.isDirectory()) {
                URL url = file.toURI().toURL();
                Set<String> classes = classAnalyzer.analyze(url, excludedClasses);

                artifactClassMap.put(artifact, classes);
            }
        }

        return artifactClassMap;
    }

    private static Set<DependencyUsage> buildTestOnlyDependencyClasses(
            Set<DependencyUsage> mainDependencyClasses, Set<DependencyUsage> testDependencyClasses) {
        Set<DependencyUsage> testOnlyDependencyClasses = new HashSet<>(testDependencyClasses);
        Set<String> mainDepClassNames = mainDependencyClasses.stream()
                .map(DependencyUsage::getDependencyClass)
                .collect(Collectors.toSet());
        testOnlyDependencyClasses.removeIf(u -> mainDepClassNames.contains(u.getDependencyClass()));
        return testOnlyDependencyClasses;
    }

    private Set<DependencyUsage> buildMainDependencyClasses(MavenProject project, ClassesPatterns excludedClasses)
            throws IOException {
        String outputDirectory = project.getBuild().getOutputDirectory();
        return buildDependencyClasses(outputDirectory, excludedClasses);
    }

    private Set<DependencyUsage> buildTestDependencyClasses(MavenProject project, ClassesPatterns excludedClasses)
            throws IOException {
        String testOutputDirectory = project.getBuild().getTestOutputDirectory();
        return buildDependencyClasses(testOutputDirectory, excludedClasses);
    }

    private Set<DependencyUsage> buildDependencyClasses(String path, ClassesPatterns excludedClasses)
            throws IOException {
        URL url = new File(path).toURI().toURL();

        return dependencyAnalyzer.analyzeUsages(url, excludedClasses);
    }

    private static Set<Artifact> buildDeclaredArtifacts(MavenProject project) {
        Set<Artifact> declaredArtifacts = project.getDependencyArtifacts();

        if (declaredArtifacts == null) {
            declaredArtifacts = Collections.emptySet();
        }

        return declaredArtifacts;
    }

    private static Map<Artifact, Set<DependencyUsage>> buildUsedArtifacts(
            Map<Artifact, Set<String>> artifactClassMap, Set<DependencyUsage> dependencyClasses) {
        Map<Artifact, Set<DependencyUsage>> usedArtifacts = new HashMap<>();

        for (DependencyUsage classUsage : dependencyClasses) {
            Artifact artifact = findArtifactForClassName(artifactClassMap, classUsage.getDependencyClass());

            if (artifact != null && !includedInJDK(artifact)) {
                Set<DependencyUsage> classesFromArtifact = usedArtifacts.get(artifact);
                if (classesFromArtifact == null) {
                    classesFromArtifact = new HashSet<>();
                    usedArtifacts.put(artifact, classesFromArtifact);
                }
                classesFromArtifact.add(classUsage);
            }
        }

        return usedArtifacts;
    }

    // MSHARED-47 an uncommon case where a commonly used
    // third party dependency was added to the JDK
    private static boolean includedInJDK(Artifact artifact) {
        if ("xml-apis".equals(artifact.getGroupId())) {
            if ("xml-apis".equals(artifact.getArtifactId())) {
                return true;
            }
        } else if ("xerces".equals(artifact.getGroupId())) {
            if ("xmlParserAPIs".equals(artifact.getArtifactId())) {
                return true;
            }
        }
        return false;
    }

    private static Artifact findArtifactForClassName(Map<Artifact, Set<String>> artifactClassMap, String className) {
        for (Map.Entry<Artifact, Set<String>> entry : artifactClassMap.entrySet()) {
            if (entry.getValue().contains(className)) {
                return entry.getKey();
            }
        }

        return null;
    }
}
