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
import javax.inject.Provider;
import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>DefaultProjectDependencyAnalyzer class.</p>
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
@Named
@Singleton
public class DefaultProjectDependencyAnalyzer implements ProjectDependencyAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProjectDependencyAnalyzer.class);

    private static final DependencyFilter NO_ARTIFACT_RESOLUTION = (node, parents) -> false;

    /**
     * ClassAnalyzer
     */
    @Inject
    private ClassAnalyzer classAnalyzer;

    @Inject
    private List<MainDependencyClassesProvider> mainDependencyClassesProviders;

    @Inject
    private List<TestDependencyClassesProvider> testDependencyClassesProviders;

    @Inject
    private ProjectDependenciesResolver projectDependenciesResolver;

    @Inject
    private Provider<MavenSession> mavenSessionProvider;

    @Inject
    private ArtifactHandlerManager artifactHandlerManager;

    /** Constructor used by Sisu. */
    public DefaultProjectDependencyAnalyzer() {}

    DefaultProjectDependencyAnalyzer(
            ProjectDependenciesResolver projectDependenciesResolver,
            Provider<MavenSession> mavenSessionProvider,
            ArtifactHandlerManager artifactHandlerManager) {
        this.projectDependenciesResolver = projectDependenciesResolver;
        this.mavenSessionProvider = mavenSessionProvider;
        this.artifactHandlerManager = artifactHandlerManager;
    }

    /** {@inheritDoc} */
    @Override
    public ProjectDependencyAnalysis analyze(MavenProject project, Collection<String> excludedClasses)
            throws ProjectDependencyAnalyzerException {
        try {
            ClassesPatterns excludedClassesPatterns = new ClassesPatterns(excludedClasses);
            Map<Artifact, Set<String>> artifactClassMap = buildArtifactClassMap(project, excludedClassesPatterns);
            Map<String, Artifact> classToArtifactMap = buildClassToArtifactMap(artifactClassMap);

            Set<DependencyUsage> mainDependencyClasses = new HashSet<>();
            for (MainDependencyClassesProvider provider : mainDependencyClassesProviders) {
                mainDependencyClasses.addAll(provider.getDependencyClasses(project, excludedClassesPatterns));
            }

            Set<DependencyUsage> testDependencyClasses = new HashSet<>();
            for (TestDependencyClassesProvider provider : testDependencyClassesProviders) {
                testDependencyClasses.addAll(provider.getDependencyClasses(project, excludedClassesPatterns));
            }

            Set<DependencyUsage> dependencyClasses = new HashSet<>();
            dependencyClasses.addAll(mainDependencyClasses);
            dependencyClasses.addAll(testDependencyClasses);

            Set<DependencyUsage> testOnlyDependencyClasses =
                    buildTestOnlyDependencyClasses(mainDependencyClasses, testDependencyClasses);

            Map<Artifact, Set<DependencyUsage>> usedArtifacts =
                    buildUsedArtifacts(classToArtifactMap, dependencyClasses);
            Set<Artifact> mainUsedArtifacts = buildUsedArtifacts(classToArtifactMap, mainDependencyClasses)
                    .keySet();

            Set<Artifact> testArtifacts = buildUsedArtifacts(classToArtifactMap, testOnlyDependencyClasses)
                    .keySet();
            Set<Artifact> testOnlyArtifacts = removeAll(testArtifacts, mainUsedArtifacts);

            Set<Artifact> declaredArtifacts = buildDeclaredArtifacts(project, artifactHandlerManager);
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

            Set<Artifact> testArtifactsWithNonTestScope = getTestArtifactsWithNonTestScope(project, testOnlyArtifacts);

            return new ProjectDependencyAnalysis(
                    usedDeclaredArtifactsWithClasses, usedUndeclaredArtifactsWithClasses,
                    unusedDeclaredArtifacts, testArtifactsWithNonTestScope);
        } catch (IOException exception) {
            throw new ProjectDependencyAnalyzerException("Cannot analyze dependencies", exception);
        }
    }

    /**
     * This method defines a new way to remove the artifacts by using the conflict
     * id. We don't care about the version
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

    Set<Artifact> getTestArtifactsWithNonTestScope(MavenProject project, Set<Artifact> testOnlyArtifacts) {
        Set<Artifact> nonTestScopeArtifacts = new LinkedHashSet<>();

        for (Artifact artifact : testOnlyArtifacts) {
            if (Artifact.SCOPE_COMPILE.equals(artifact.getScope())) {
                nonTestScopeArtifacts.add(artifact);
            }
        }

        if (nonTestScopeArtifacts.isEmpty()) {
            return nonTestScopeArtifacts;
        }

        RepositorySystemSession repositorySession = getRepositorySystemSession();
        if (repositorySession == null) {
            LOGGER.debug("Cannot refine test-only dependency scopes without a repository session");
            return nonTestScopeArtifacts;
        }

        try {
            // Collect each non-test classpath independently and without the candidates as direct roots. Otherwise a
            // direct declaration can hide the same artifact reached transitively with a different scope.
            Set<String> nonTestDependencyIds = collectDependencyIds(
                    createDependencyGraphProject(project, nonTestScopeArtifacts, NonTestClasspath.COMPILE),
                    repositorySession);
            nonTestDependencyIds.addAll(collectDependencyIds(
                    createDependencyGraphProject(project, nonTestScopeArtifacts, NonTestClasspath.RUNTIME),
                    repositorySession));

            nonTestScopeArtifacts.removeIf(artifact -> nonTestDependencyIds.contains(toVersionlessId(artifact)));
        } catch (DependencyResolutionException exception) {
            LOGGER.debug("Cannot refine test-only dependency scopes using the non-test dependency graphs", exception);
        }

        return nonTestScopeArtifacts;
    }

    private Set<String> collectDependencyIds(MavenProject project, RepositorySystemSession repositorySession)
            throws DependencyResolutionException {
        DependencyResolutionRequest request = new DefaultDependencyResolutionRequest(project, repositorySession);
        request.setResolutionFilter(NO_ARTIFACT_RESOLUTION);
        DependencyResolutionResult result = projectDependenciesResolver.resolve(request);

        Set<String> dependencyIds = new HashSet<>();
        DependencyNode root = result.getDependencyGraph();
        if (root == null) {
            return dependencyIds;
        }

        Deque<DependencyNode> remaining = new ArrayDeque<>(root.getChildren());
        Set<DependencyNode> visited = Collections.newSetFromMap(new IdentityHashMap<DependencyNode, Boolean>());
        while (!remaining.isEmpty()) {
            DependencyNode node = remaining.removeFirst();
            if (visited.add(node)) {
                if (node.getArtifact() != null) {
                    dependencyIds.add(ArtifactIdUtils.toVersionlessId(node.getArtifact()));
                }
                remaining.addAll(node.getChildren());
            }
        }
        return dependencyIds;
    }

    private MavenProject createDependencyGraphProject(
            MavenProject project, Set<Artifact> candidates, NonTestClasspath classpath) {
        Set<String> candidateIds =
                candidates.stream().map(Artifact::getDependencyConflictId).collect(Collectors.toSet());
        List<Dependency> dependencies = project.getDependencies().stream()
                .filter(dependency -> classpath.includes(dependency.getScope()))
                .filter(dependency -> !candidateIds.contains(toDependencyConflictId(dependency)))
                .collect(Collectors.toList());
        return new DependencyGraphProject(project, dependencies);
    }

    private RepositorySystemSession getRepositorySystemSession() {
        MavenSession mavenSession = mavenSessionProvider != null ? mavenSessionProvider.get() : null;
        return mavenSession != null ? mavenSession.getRepositorySession() : null;
    }

    private String toDependencyConflictId(Dependency dependency) {
        return toDependencyConflictId(dependency, artifactHandlerManager.getArtifactHandler(dependency.getType()));
    }

    private static String toDependencyConflictId(Dependency dependency, ArtifactHandler artifactHandler) {
        String classifier = dependency.getClassifier();
        if (classifier == null) {
            classifier = artifactHandler.getClassifier();
        }
        return ArtifactIdUtils.toVersionlessId(
                dependency.getGroupId(), dependency.getArtifactId(), dependency.getType(), classifier);
    }

    private static String toVersionlessId(Artifact artifact) {
        String extension = artifact.getArtifactHandler() != null
                ? artifact.getArtifactHandler().getExtension()
                : artifact.getType();
        return ArtifactIdUtils.toVersionlessId(
                artifact.getGroupId(), artifact.getArtifactId(), extension, artifact.getClassifier());
    }

    private enum NonTestClasspath {
        COMPILE {
            @Override
            boolean includes(String scope) {
                return scope == null
                        || scope.isEmpty()
                        || Artifact.SCOPE_COMPILE.equals(scope)
                        || Artifact.SCOPE_PROVIDED.equals(scope)
                        || Artifact.SCOPE_SYSTEM.equals(scope);
            }
        },
        RUNTIME {
            @Override
            boolean includes(String scope) {
                return scope == null
                        || scope.isEmpty()
                        || Artifact.SCOPE_COMPILE.equals(scope)
                        || Artifact.SCOPE_RUNTIME.equals(scope);
            }
        };

        abstract boolean includes(String scope);
    }

    /**
     * Maps dependency artifacts to their classes.
     *
     * @param project Maven project
     * @param excludedClasses patterns of classes to exclude
     * @return dependency artifacts and their classes
     * @throws IOException if a dependency cannot be read
     */
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

    static Set<Artifact> buildDeclaredArtifacts(MavenProject project, ArtifactHandlerManager artifactHandlerManager) {
        Map<String, Artifact> resolvedArtifacts = project.getArtifacts().stream()
                .collect(Collectors.toMap(
                        Artifact::getDependencyConflictId,
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new));
        Set<Artifact> declaredArtifacts = new LinkedHashSet<>();
        for (Dependency dependency : project.getDependencies()) {
            ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler(dependency.getType());
            String dependencyConflictId = toDependencyConflictId(dependency, artifactHandler);
            Artifact artifact = resolvedArtifacts.get(dependencyConflictId);
            if (artifact == null) {
                artifact = new DefaultArtifact(
                        dependency.getGroupId(),
                        dependency.getArtifactId(),
                        VersionRange.createFromVersion(dependency.getVersion()),
                        dependency.getScope(),
                        dependency.getType(),
                        dependency.getClassifier(),
                        artifactHandler,
                        dependency.isOptional());
            }
            declaredArtifacts.add(artifact);
        }
        return declaredArtifacts;
    }

    private static final class DependencyGraphProject extends MavenProject {
        private DependencyGraphProject(MavenProject project, List<Dependency> dependencies) {
            super(project);
            setDependencies(dependencies);
        }

        @Override
        @SuppressWarnings("deprecation")
        public Set<Artifact> getDependencyArtifacts() {
            // Make ProjectDependenciesResolver collect the filtered model dependencies while retaining all other
            // decorator-visible state copied by MavenProject(MavenProject).
            return null;
        }
    }

    static Map<Artifact, Set<DependencyUsage>> buildUsedArtifacts(
            Map<String, Artifact> classToArtifactMap, Set<DependencyUsage> dependencyClasses) {
        Map<Artifact, Set<DependencyUsage>> usedArtifacts = new HashMap<>();

        for (DependencyUsage classUsage : dependencyClasses) {
            Artifact artifact = classToArtifactMap.get(classUsage.getDependencyClass());

            if (artifact != null && !includedInJDK(artifact)) {
                usedArtifacts.computeIfAbsent(artifact, k -> new HashSet<>()).add(classUsage);
            }
        }

        return usedArtifacts;
    }

    // MSHARED-47 an uncommon case where a commonly used
    // third party dependency was added to the JDK
    static boolean includedInJDK(Artifact artifact) {
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

    static Map<String, Artifact> buildClassToArtifactMap(Map<Artifact, Set<String>> artifactClassMap) {
        Map<String, Artifact> classToArtifactMap = new HashMap<>();

        for (Map.Entry<Artifact, Set<String>> entry : artifactClassMap.entrySet()) {
            Artifact artifact = entry.getKey();
            for (String className : entry.getValue()) {
                classToArtifactMap.putIfAbsent(className, artifact);
            }
        }

        return classToArtifactMap;
    }
}
