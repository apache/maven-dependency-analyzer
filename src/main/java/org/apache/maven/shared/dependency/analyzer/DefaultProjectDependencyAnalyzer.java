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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import org.apache.maven.api.annotations.Nullable;
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

    /**
     * The session of the current build, used to collect the non-test classpaths of the project. It is not
     * available when the analyzer is used outside of a Maven build, in which case the test-only dependencies
     * are reported as they are.
     */
    @Inject
    @Nullable
    private Session session;

    /** Constructor used by the dependency injection container. */
    @Inject
    public DefaultProjectDependencyAnalyzer() {}

    /** Constructor used by tests. */
    DefaultProjectDependencyAnalyzer(
            Session session,
            ClassAnalyzer classAnalyzer,
            List<MainDependencyClassesProvider> mainDependencyClassesProviders,
            List<TestDependencyClassesProvider> testDependencyClassesProviders) {
        this.session = session;
        this.classAnalyzer = classAnalyzer;
        this.mainDependencyClassesProviders = mainDependencyClassesProviders;
        this.testDependencyClassesProviders = testDependencyClassesProviders;
    }

    /** {@inheritDoc} */
    @Override
    public ProjectDependencyAnalysis analyze(
            Project project, DependencyResolverResult dependencies, Collection<String> excludedClasses)
            throws ProjectDependencyAnalyzerException {
        try {
            ClassesPatterns excludedClassesPatterns = new ClassesPatterns(excludedClasses);
            Map<Dependency, Path> resolvedDependencies = dependencies.getDependencies();
            Map<String, String> parentIds = buildParentIds(dependencies.getRoot());
            Map<Dependency, Set<String>> artifactClassMap =
                    buildArtifactClassMap(resolvedDependencies, excludedClassesPatterns);
            Set<Dependency> declaredArtifacts = buildDeclaredArtifacts(project, resolvedDependencies.keySet());
            Map<String, Dependency> classToArtifactMap = buildClassToArtifactMap(artifactClassMap, declaredArtifacts);

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
            creditClasslessAggregators(artifactClassMap, declaredArtifacts, usedArtifacts, parentIds);
            Set<Dependency> mainUsedArtifacts = buildUsedArtifacts(classToArtifactMap, mainDependencyClasses)
                    .keySet();

            Set<Dependency> testArtifacts = buildUsedArtifacts(classToArtifactMap, testOnlyDependencyClasses)
                    .keySet();
            Set<Dependency> testOnlyArtifacts = removeAll(testArtifacts, mainUsedArtifacts);

            Set<Dependency> usedDeclaredArtifacts = retainAll(declaredArtifacts, usedArtifacts.keySet());

            Map<Dependency, Set<DependencyUsage>> usedDeclaredArtifactsWithClasses = new LinkedHashMap<>();
            for (Dependency a : usedDeclaredArtifacts) {
                usedDeclaredArtifactsWithClasses.put(a, usedArtifacts.get(a));
            }

            Map<Dependency, Set<DependencyUsage>> usedUndeclaredArtifactsWithClasses =
                    new LinkedHashMap<>(usedArtifacts);
            Set<Dependency> usedUndeclaredArtifacts =
                    removeAll(usedUndeclaredArtifactsWithClasses.keySet(), declaredArtifacts);

            usedUndeclaredArtifactsWithClasses.keySet().retainAll(usedUndeclaredArtifacts);

            Set<Dependency> unusedDeclaredArtifacts = removeAll(declaredArtifacts, usedArtifacts.keySet());

            Set<Dependency> testArtifactsWithNonTestScope =
                    getTestArtifactsWithNonTestScope(project, testOnlyArtifacts);

            Set<String> usedDependencyClasses = buildUsedDependencyClasses(dependencyClasses);
            Map<String, Set<Dependency>> candidateArtifactsByUsedClass =
                    buildCandidateArtifactsByUsedClass(artifactClassMap, usedDependencyClasses);
            Map<String, Dependency> selectedArtifactByUsedClass =
                    buildSelectedArtifactByUsedClass(classToArtifactMap, usedDependencyClasses);

            return new ProjectDependencyAnalysis(
                    usedDeclaredArtifactsWithClasses,
                    usedUndeclaredArtifactsWithClasses,
                    unusedDeclaredArtifacts,
                    testArtifactsWithNonTestScope,
                    usedDependencyClasses,
                    candidateArtifactsByUsedClass,
                    selectedArtifactByUsedClass);
        } catch (IOException | DependencyResolverException exception) {
            throw new ProjectDependencyAnalyzerException("Cannot analyze dependencies", exception);
        }
    }

    /**
     * Maps every resolved dependency to the versionless id of the dependency it was reached through.
     * The Maven 4 API has no equivalent of {@code Artifact.getDependencyTrail()}, so the parent of each dependency is
     * read from the resolved dependency graph.
     *
     * @param root the root of the resolved dependency graph
     * @return the versionless id of the parent of every dependency, {@code null} for a direct dependency
     */
    private static Map<String, String> buildParentIds(Node root) {
        Map<String, String> parentIds = new HashMap<>();
        if (root != null) {
            collectParentIds(root, null, parentIds);
        }
        return parentIds;
    }

    private static void collectParentIds(Node node, String parentId, Map<String, String> parentIds) {
        for (Node child : node.getChildren()) {
            Dependency dependency = child.getDependency();
            String id = dependency != null ? toVersionlessId(dependency) : null;
            if (id != null) {
                parentIds.putIfAbsent(id, parentId);
            }
            collectParentIds(child, id != null ? id : parentId, parentIds);
        }
    }

    /**
     * This method defines a new way to remove the artifacts by using the versionless
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

    /**
     * Keeps the artifacts of the given set that are also part of the given set, comparing them by their versionless
     * id.
     *
     * @param start  initial set
     * @param keep set to keep
     * @return set with only the artifacts of keep
     */
    private static Set<Dependency> retainAll(Set<Dependency> start, Set<Dependency> keep) {
        Set<String> keepIds = keep.stream()
                .map(DefaultProjectDependencyAnalyzer::toVersionlessId)
                .collect(Collectors.toSet());
        Set<Dependency> results = new LinkedHashSet<>(start.size());

        for (Dependency artifact : start) {
            if (keepIds.contains(toVersionlessId(artifact))) {
                results.add(artifact);
            }
        }

        return results;
    }

    Set<Dependency> getTestArtifactsWithNonTestScope(Project project, Set<Dependency> testOnlyArtifacts) {
        Set<Dependency> nonTestScopeArtifacts = new LinkedHashSet<>();

        for (Dependency artifact : testOnlyArtifacts) {
            if (artifact.getScope() == DependencyScope.COMPILE) {
                nonTestScopeArtifacts.add(artifact);
            }
        }

        if (nonTestScopeArtifacts.isEmpty()) {
            return nonTestScopeArtifacts;
        }

        if (session == null) {
            LOGGER.debug("Cannot refine test-only dependency scopes without a session");
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
                Set<String> classes = classAnalyzer.analyze(path, excludedClasses);

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

    static Set<Dependency> buildDeclaredArtifacts(Project project, Collection<Dependency> resolvedDependencies) {
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
                artifact = new DeclaredDependency(coordinates);
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

    static Set<String> buildUsedDependencyClasses(Set<DependencyUsage> dependencyClasses) {
        Set<String> usedDependencyClasses = new LinkedHashSet<>();
        for (DependencyUsage usage : dependencyClasses) {
            usedDependencyClasses.add(usage.getDependencyClass());
        }
        return usedDependencyClasses;
    }

    static Map<String, Set<Dependency>> buildCandidateArtifactsByUsedClass(
            Map<Dependency, Set<String>> artifactClassMap, Set<String> usedDependencyClasses) {
        Map<String, Set<Dependency>> candidatesByClass = new LinkedHashMap<>();
        for (String className : usedDependencyClasses) {
            Set<Dependency> candidates = new LinkedHashSet<>();
            for (Map.Entry<Dependency, Set<String>> entry : artifactClassMap.entrySet()) {
                if (entry.getValue().contains(className)) {
                    candidates.add(entry.getKey());
                }
            }
            candidatesByClass.put(className, candidates);
        }
        return candidatesByClass;
    }

    static Map<String, Dependency> buildSelectedArtifactByUsedClass(
            Map<String, Dependency> classToArtifactMap, Set<String> usedDependencyClasses) {
        Map<String, Dependency> selectedByClass = new LinkedHashMap<>();
        for (String className : usedDependencyClasses) {
            Dependency artifact = classToArtifactMap.get(className);
            if (artifact != null) {
                selectedByClass.put(className, artifact);
            }
        }
        return selectedByClass;
    }

    /**
     * Treats a declared artifact without classes of its own (for example {@code junit-jupiter}, which only carries
     * {@code module-info.class}) as used when one of the artifacts it brings in directly is used. The used artifacts
     * that are brought in by such an aggregator and are not declared themselves are then no longer reported as used
     * undeclared. The direct dependencies are taken from the dependency graph of the resolved artifacts, so only the
     * path that survived dependency mediation is considered.
     *
     * @param artifactClassMap the classes of each resolved artifact
     * @param declaredArtifacts the artifacts declared by the project
     * @param usedArtifacts the used artifacts, updated in place
     * @param parentIds the parent of every resolved dependency, by versionless id
     */
    static void creditClasslessAggregators(
            Map<Dependency, Set<String>> artifactClassMap,
            Set<Dependency> declaredArtifacts,
            Map<Dependency, Set<DependencyUsage>> usedArtifacts,
            Map<String, String> parentIds) {
        Set<String> declaredIds = declaredArtifacts.stream()
                .map(DefaultProjectDependencyAnalyzer::toVersionlessId)
                .collect(Collectors.toSet());

        for (Map.Entry<Dependency, Set<String>> entry : artifactClassMap.entrySet()) {
            Dependency aggregator = entry.getKey();
            String aggregatorId = toVersionlessId(aggregator);
            if (!entry.getValue().isEmpty()
                    || !declaredIds.contains(aggregatorId)
                    || usedArtifacts.containsKey(aggregator)) {
                continue;
            }

            Set<DependencyUsage> usages = new HashSet<>();
            List<Dependency> usedChildren = new ArrayList<>();
            for (Dependency used : usedArtifacts.keySet()) {
                if (aggregatorId.equals(parentIds.get(toVersionlessId(used)))) {
                    usedChildren.add(used);
                    usages.addAll(usedArtifacts.get(used));
                }
            }
            if (usedChildren.isEmpty()) {
                continue;
            }

            usedArtifacts.put(aggregator, usages);
            for (Dependency child : usedChildren) {
                if (!declaredIds.contains(toVersionlessId(child))) {
                    usedArtifacts.remove(child);
                }
            }
        }
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

    /**
     * Maps each class to the artifact that provides it. When several artifacts contain the class, a declared
     * artifact wins over an undeclared one, and otherwise the first one wins. If two declared artifacts contain the
     * class, only the first of them is credited. Scope is not considered: a declared test-scoped artifact that
     * contains the class is credited even for main code, so an undeclared compile-scoped artifact with the same
     * class is not reported as used.
     *
     * @param artifactClassMap the classes of each artifact
     * @param declaredArtifacts the artifacts declared by the project
     * @return the providing artifact per class
     */
    static Map<String, Dependency> buildClassToArtifactMap(Map<Dependency, Set<String>> artifactClassMap) {
        return buildClassToArtifactMap(artifactClassMap, Collections.emptySet());
    }

    static Map<String, Dependency> buildClassToArtifactMap(
            Map<Dependency, Set<String>> artifactClassMap, Set<Dependency> declaredArtifacts) {
        Set<String> declaredIds = declaredArtifacts.stream()
                .map(DefaultProjectDependencyAnalyzer::toVersionlessId)
                .collect(Collectors.toSet());
        Map<String, Dependency> classToArtifactMap = new HashMap<>();

        for (Map.Entry<Dependency, Set<String>> entry : artifactClassMap.entrySet()) {
            Dependency artifact = entry.getKey();
            if (declaredIds.contains(toVersionlessId(artifact))) {
                for (String className : entry.getValue()) {
                    classToArtifactMap.putIfAbsent(className, artifact);
                }
            }
        }

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

        DeclaredDependency(DependencyCoordinates coordinates) {
            this.coordinates = coordinates;
            String versionString = String.valueOf(coordinates.getVersionConstraint());
            this.version = new SimpleVersion(versionString);
            this.snapshot = versionString.endsWith("SNAPSHOT");
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

    /**
     * The version of a declared dependency. The Maven 4 API has no factory for a {@link Version} that does not
     * need a session, so the declared version is kept as it was written in the model.
     */
    static final class SimpleVersion implements Version {
        private final String id;

        SimpleVersion(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return id;
        }

        @Override
        public int compareTo(Version other) {
            return id.compareTo(other.toString());
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SimpleVersion && id.equals(obj.toString());
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
