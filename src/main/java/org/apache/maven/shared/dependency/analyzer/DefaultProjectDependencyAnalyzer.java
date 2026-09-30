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
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Dependency;
import org.apache.maven.api.DependencyCoordinates;
import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.Node;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.Type;
import org.apache.maven.api.Version;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.services.DependencyResolver;
import org.apache.maven.api.services.DependencyResolverException;
import org.apache.maven.api.services.DependencyResolverRequest;
import org.apache.maven.api.services.DependencyResolverResult;
import org.apache.maven.api.services.ProjectManager;
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

    /**
     * ClassAnalyzer
     */
    @Inject
    private ClassAnalyzer classAnalyzer;

    @Inject
    private List<MainDependencyClassesProvider> mainDependencyClassesProviders;

    @Inject
    private List<TestDependencyClassesProvider> testDependencyClassesProviders;

    /** Constructor used by the dependency injection container. */
    public DefaultProjectDependencyAnalyzer() {}

    /** {@inheritDoc} */
    @Override
    public ProjectDependencyAnalysis analyze(Session session, Project project, Collection<String> excludedClasses)
            throws ProjectDependencyAnalyzerException {
        try {
            ClassesPatterns excludedClassesPatterns = new ClassesPatterns(excludedClasses);
            Map<Dependency, Path> resolvedDependencies = resolveDependencies(session, project);
            Map<Dependency, Set<String>> artifactClassMap =
                    buildArtifactClassMap(resolvedDependencies, excludedClassesPatterns);
            Map<String, Dependency> classToArtifactMap = buildClassToArtifactMap(artifactClassMap);

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

            Map<Dependency, Set<DependencyUsage>> usedArtifacts =
                    buildUsedArtifacts(classToArtifactMap, dependencyClasses);
            Set<Dependency> mainUsedArtifacts = buildUsedArtifacts(classToArtifactMap, mainDependencyClasses)
                    .keySet();

            Set<Dependency> testArtifacts = buildUsedArtifacts(classToArtifactMap, testOnlyDependencyClasses)
                    .keySet();
            Set<Dependency> testOnlyArtifacts = removeAll(testArtifacts, mainUsedArtifacts);

            Set<Dependency> declaredArtifacts = buildDeclaredArtifacts(session, project, resolvedDependencies.keySet());
            Set<Dependency> usedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
            usedDeclaredArtifacts.retainAll(usedArtifacts.keySet());

            Map<Dependency, Set<DependencyUsage>> usedDeclaredArtifactsWithClasses = new LinkedHashMap<>();
            for (Dependency a : usedDeclaredArtifacts) {
                usedDeclaredArtifactsWithClasses.put(a, usedArtifacts.get(a));
            }

            Map<Dependency, Set<DependencyUsage>> usedUndeclaredArtifactsWithClasses =
                    new LinkedHashMap<>(usedArtifacts);
            Set<Dependency> usedUndeclaredArtifacts =
                    removeAll(usedUndeclaredArtifactsWithClasses.keySet(), declaredArtifacts);

            usedUndeclaredArtifactsWithClasses.keySet().retainAll(usedUndeclaredArtifacts);

            Set<Dependency> unusedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
            unusedDeclaredArtifacts = removeAll(unusedDeclaredArtifacts, usedArtifacts.keySet());

            Set<Dependency> testArtifactsWithNonTestScope =
                    getTestArtifactsWithNonTestScope(session, project, testOnlyArtifacts);

            return new ProjectDependencyAnalysis(
                    usedDeclaredArtifactsWithClasses, usedUndeclaredArtifactsWithClasses,
                    unusedDeclaredArtifacts, testArtifactsWithNonTestScope);
        } catch (IOException | DependencyResolverException exception) {
            throw new ProjectDependencyAnalyzerException("Cannot analyze dependencies", exception);
        }
    }

    /**
     * Resolves all dependencies of the project needed to compile and run its tests.
     * The Maven 4 API has no equivalent of {@code MavenProject.getArtifacts()}, so the analyzer resolves them itself.
     */
    private static Map<Dependency, Path> resolveDependencies(Session session, Project project) {
        DependencyResolverResult result =
                session.getService(DependencyResolver.class).resolve(session, project, PathScope.TEST_RUNTIME);
        return result.getDependencies();
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
    private static Set<Dependency> removeAll(Set<Dependency> start, Set<Dependency> remove) {
        Set<String> removeIds = remove.stream()
                .map(DefaultProjectDependencyAnalyzer::toVersionlessId)
                .collect(Collectors.toSet());
        Set<Dependency> results = new LinkedHashSet<>(start.size());

        for (Dependency artifact : start) {
            if (!removeIds.contains(toVersionlessId(artifact))) {
                results.add(artifact);
            }
        }

        return results;
    }

    Set<Dependency> getTestArtifactsWithNonTestScope(
            Session session, Project project, Set<Dependency> testOnlyArtifacts) {
        Set<Dependency> nonTestScopeArtifacts = new LinkedHashSet<>();

        for (Dependency artifact : testOnlyArtifacts) {
            if (artifact.getScope() == DependencyScope.COMPILE) {
                nonTestScopeArtifacts.add(artifact);
            }
        }

        if (nonTestScopeArtifacts.isEmpty()) {
            return nonTestScopeArtifacts;
        }

        try {
            // Collect each non-test classpath independently and without the candidates as direct roots. Otherwise a
            // direct declaration can hide the same artifact reached transitively with a different scope.
            Set<String> candidateIds = nonTestScopeArtifacts.stream()
                    .map(DefaultProjectDependencyAnalyzer::toVersionlessId)
                    .collect(Collectors.toSet());
            Set<String> nonTestDependencyIds =
                    collectDependencyIds(session, project, candidateIds, NonTestClasspath.COMPILE);
            nonTestDependencyIds.addAll(collectDependencyIds(session, project, candidateIds, NonTestClasspath.RUNTIME));

            nonTestScopeArtifacts.removeIf(artifact -> nonTestDependencyIds.contains(toVersionlessId(artifact)));
        } catch (DependencyResolverException exception) {
            LOGGER.debug("Cannot refine test-only dependency scopes using the non-test dependency graphs", exception);
        }

        return nonTestScopeArtifacts;
    }

    private Set<String> collectDependencyIds(
            Session session, Project project, Set<String> candidateIds, NonTestClasspath classpath) {
        // The Maven 4 API collects from an explicit list of coordinates, so the model does not have to be copied and
        // patched as it was with MavenProject.
        List<DependencyCoordinates> dependencies = project.getDependencies().stream()
                .filter(dependency -> classpath.includes(dependency.getScope()))
                .filter(dependency -> !candidateIds.contains(toVersionlessId(dependency)))
                .collect(Collectors.toList());
        DependencyResolverRequest request = DependencyResolverRequest.builder()
                .session(session)
                .requestType(DependencyResolverRequest.RequestType.COLLECT)
                .pathScope(classpath.pathScope())
                .rootArtifact(project.getPomArtifact())
                .dependencies(dependencies)
                .managedDependencies(project.getManagedDependencies())
                .repositories(session.getService(ProjectManager.class).getRemoteProjectRepositories(project))
                .build();
        DependencyResolverResult result =
                session.getService(DependencyResolver.class).collect(request);

        Set<String> dependencyIds = new HashSet<>();
        Node root = result.getRoot();
        if (root == null) {
            return dependencyIds;
        }
        Deque<Node> remaining = new ArrayDeque<>(root.getChildren());
        Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!remaining.isEmpty()) {
            Node node = remaining.removeFirst();
            if (visited.add(node)) {
                if (node.getArtifact() != null) {
                    dependencyIds.add(toVersionlessId(node.getArtifact()));
                }
                remaining.addAll(node.getChildren());
            }
        }
        return dependencyIds;
    }

    private static String toVersionlessId(Artifact artifact) {
        return toVersionlessId(
                artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier());
    }

    private static String toVersionlessId(DependencyCoordinates coordinates) {
        Type type = coordinates.getType();
        String classifier = coordinates.getClassifier();
        if (classifier == null || classifier.isEmpty()) {
            classifier = type.getClassifier();
        }
        return toVersionlessId(coordinates.getGroupId(), coordinates.getArtifactId(), type.getExtension(), classifier);
    }

    private static String toVersionlessId(String groupId, String artifactId, String extension, String classifier) {
        StringBuilder id = new StringBuilder();
        id.append(groupId).append(':').append(artifactId).append(':').append(extension);
        if (classifier != null && !classifier.isEmpty()) {
            id.append(':').append(classifier);
        }
        return id.toString();
    }

    private enum NonTestClasspath {
        COMPILE {
            @Override
            PathScope pathScope() {
                return PathScope.MAIN_COMPILE;
            }

            @Override
            boolean includes(DependencyScope scope) {
                return scope == null
                        || scope == DependencyScope.UNDEFINED
                        || scope == DependencyScope.COMPILE
                        || scope == DependencyScope.PROVIDED
                        || scope == DependencyScope.SYSTEM;
            }
        },
        RUNTIME {
            @Override
            PathScope pathScope() {
                return PathScope.MAIN_RUNTIME;
            }

            @Override
            boolean includes(DependencyScope scope) {
                return scope == null
                        || scope == DependencyScope.UNDEFINED
                        || scope == DependencyScope.COMPILE
                        || scope == DependencyScope.RUNTIME;
            }
        };

        abstract PathScope pathScope();

        abstract boolean includes(DependencyScope scope);
    }

    /**
     * Maps dependency artifacts to their classes.
     *
     * @param resolvedDependencies resolved dependencies and the paths of their files or directories
     * @param excludedClasses patterns of classes to exclude
     * @return dependency artifacts and their classes
     * @throws IOException if a dependency cannot be read
     */
    protected Map<Dependency, Set<String>> buildArtifactClassMap(
            Map<Dependency, Path> resolvedDependencies, ClassesPatterns excludedClasses) throws IOException {
        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();

        for (Map.Entry<Dependency, Path> entry : resolvedDependencies.entrySet()) {
            Dependency artifact = entry.getKey();
            Path path = entry.getValue();

            if (path != null && path.getFileName().toString().endsWith(".jar")) {
                // optimized solution for the jar case

                try (JarFile jarFile = new JarFile(path.toFile())) {
                    Enumeration<JarEntry> jarEntries = jarFile.entries();

                    Set<String> classes = new HashSet<>();

                    while (jarEntries.hasMoreElements()) {
                        String jarEntry = jarEntries.nextElement().getName();
                        if (jarEntry.endsWith(".class")) {
                            String className = jarEntry.replace('/', '.');
                            className = className.substring(0, className.length() - ".class".length());
                            if (!excludedClasses.isMatch(className)) {
                                classes.add(className);
                            }
                        }
                    }

                    artifactClassMap.put(artifact, classes);
                }
            } else if (path != null && Files.isDirectory(path)) {
                URL url = path.toUri().toURL();
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

    static Set<Dependency> buildDeclaredArtifacts(
            Session session, Project project, Collection<Dependency> resolvedDependencies) {
        Map<String, Dependency> resolvedArtifacts = resolvedDependencies.stream()
                .collect(Collectors.toMap(
                        DefaultProjectDependencyAnalyzer::toVersionlessId,
                        dependency -> dependency,
                        (first, second) -> first,
                        LinkedHashMap::new));
        Set<Dependency> declaredArtifacts = new LinkedHashSet<>();
        for (DependencyCoordinates coordinates : project.getDependencies()) {
            Dependency artifact = resolvedArtifacts.get(toVersionlessId(coordinates));
            if (artifact == null) {
                artifact = new DeclaredDependency(session, coordinates);
            }
            declaredArtifacts.add(artifact);
        }
        return declaredArtifacts;
    }

    static Map<Dependency, Set<DependencyUsage>> buildUsedArtifacts(
            Map<String, Dependency> classToArtifactMap, Set<DependencyUsage> dependencyClasses) {
        Map<Dependency, Set<DependencyUsage>> usedArtifacts = new HashMap<>();

        for (DependencyUsage classUsage : dependencyClasses) {
            Dependency artifact = classToArtifactMap.get(classUsage.getDependencyClass());

            if (artifact != null && !includedInJDK(artifact)) {
                usedArtifacts.computeIfAbsent(artifact, k -> new HashSet<>()).add(classUsage);
            }
        }

        return usedArtifacts;
    }

    // MSHARED-47 an uncommon case where a commonly used
    // third party dependency was added to the JDK
    static boolean includedInJDK(Dependency artifact) {
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

    static Map<String, Dependency> buildClassToArtifactMap(Map<Dependency, Set<String>> artifactClassMap) {
        Map<String, Dependency> classToArtifactMap = new HashMap<>();

        for (Map.Entry<Dependency, Set<String>> entry : artifactClassMap.entrySet()) {
            Dependency artifact = entry.getKey();
            for (String className : entry.getValue()) {
                classToArtifactMap.putIfAbsent(className, artifact);
            }
        }

        return classToArtifactMap;
    }

    /**
     * A declared dependency that was not resolved, for example because it was relocated. The Maven 4 API has no
     * factory for a {@link Dependency} from {@link DependencyCoordinates}, only for {@code Artifact}, so the analysis
     * result is given this minimal implementation.
     */
    static final class DeclaredDependency implements Dependency {
        private final DependencyCoordinates coordinates;
        private final Version version;
        private final boolean snapshot;

        DeclaredDependency(Session session, DependencyCoordinates coordinates) {
            this.coordinates = coordinates;
            String versionString = String.valueOf(coordinates.getVersionConstraint());
            this.version = session.parseVersion(versionString);
            this.snapshot = session.isVersionSnapshot(versionString);
        }

        @Override
        public Type getType() {
            return coordinates.getType();
        }

        @Override
        public DependencyScope getScope() {
            DependencyScope scope = coordinates.getScope();
            return scope == null || scope == DependencyScope.UNDEFINED ? DependencyScope.COMPILE : scope;
        }

        @Override
        public boolean isOptional() {
            return Boolean.TRUE.equals(coordinates.getOptional());
        }

        @Override
        public DependencyCoordinates toCoordinates() {
            return coordinates;
        }

        @Override
        public String getGroupId() {
            return coordinates.getGroupId();
        }

        @Override
        public String getArtifactId() {
            return coordinates.getArtifactId();
        }

        @Override
        public Version getVersion() {
            return version;
        }

        @Override
        public Version getBaseVersion() {
            return version;
        }

        @Override
        public String getClassifier() {
            String classifier = coordinates.getClassifier();
            if (classifier == null || classifier.isEmpty()) {
                classifier = getType().getClassifier();
            }
            // Artifact.key() does not accept null
            return classifier == null ? "" : classifier;
        }

        @Override
        public String getExtension() {
            return getType().getExtension();
        }

        @Override
        public boolean isSnapshot() {
            return snapshot;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof DeclaredDependency)) {
                return false;
            }
            DeclaredDependency other = (DeclaredDependency) obj;
            return key().equals(other.key()) && getScope() == other.getScope();
        }

        @Override
        public int hashCode() {
            return Objects.hash(key(), getScope());
        }

        @Override
        public String toString() {
            return key() + ":" + getScope().id();
        }
    }
}
