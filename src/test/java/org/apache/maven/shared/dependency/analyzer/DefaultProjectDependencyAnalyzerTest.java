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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.DependencyCoordinates;
import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.Node;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.Type;
import org.apache.maven.api.services.DependencyResolver;
import org.apache.maven.api.services.DependencyResolverException;
import org.apache.maven.api.services.DependencyResolverRequest;
import org.apache.maven.api.services.DependencyResolverResult;
import org.apache.maven.api.services.ProjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests <code>DefaultProjectDependencyAnalyzer</code>.
 *
 * @see DefaultProjectDependencyAnalyzer
 */
class DefaultProjectDependencyAnalyzerTest {

    @TempDir
    private Path tempDir;

    private Session session;

    private DependencyResolver dependencyResolver;

    private Project project;

    private DefaultProjectDependencyAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        session = mock(Session.class);
        dependencyResolver = mock(DependencyResolver.class);
        ProjectManager projectManager = mock(ProjectManager.class);
        project = mock(Project.class);
        when(session.getService(DependencyResolver.class)).thenReturn(dependencyResolver);
        when(session.getService(ProjectManager.class)).thenReturn(projectManager);
        analyzer = new DefaultProjectDependencyAnalyzer(
                session,
                mock(ClassAnalyzer.class),
                Collections.singletonList(mock(MainDependencyClassesProvider.class)),
                Collections.singletonList(mock(TestDependencyClassesProvider.class)));
    }

    @Test
    void testBuildClassToArtifactMap() {
        Dependency artifact1 = aTestArtifact("artifact1");
        Dependency artifact2 = aTestArtifact("artifact2");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, Collections.singleton("class1"));
        artifactClassMap.put(artifact2, Collections.singleton("class2"));

        Map<String, Dependency> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);

        assertEquals(2, result.size());
        assertEquals(artifact1, result.get("class1"));
        assertEquals(artifact2, result.get("class2"));
    }

    @Test
    void testBuildClassToArtifactMapWithDuplicates() {
        Dependency artifact1 = aTestArtifact("artifact1");
        Dependency artifact2 = aTestArtifact("artifact2");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, Collections.singleton("duplicateClass"));
        artifactClassMap.put(artifact2, Collections.singleton("duplicateClass"));

        Map<String, Dependency> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);

        assertEquals(1, result.size());
        // Should favor the first artifact encountered
        assertEquals(artifact1, result.get("duplicateClass"));
    }

    @Test
    void testBuildClassToArtifactMapWithDuplicatesPrefersDeclared() {
        Dependency undeclared = aTestArtifact("undeclared");
        Dependency declared = aTestArtifact("declared");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(undeclared, new HashSet<>(Arrays.asList("duplicateClass", "otherClass")));
        artifactClassMap.put(declared, Collections.singleton("duplicateClass"));

        Map<String, Dependency> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(
                artifactClassMap, Collections.singleton(declared));

        assertEquals(2, result.size());
        assertEquals(declared, result.get("duplicateClass"));
        assertEquals(undeclared, result.get("otherClass"));
    }

    @Test
    void testBuildClassToArtifactMapWithDuplicatesAndNoneDeclaredKeepsFirst() {
        Dependency artifact1 = aTestArtifact("artifact1");
        Dependency artifact2 = aTestArtifact("artifact2");
        Dependency declaredButAbsent = aTestArtifact("absent");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, Collections.singleton("duplicateClass"));
        artifactClassMap.put(artifact2, Collections.singleton("duplicateClass"));

        Map<String, Dependency> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(
                artifactClassMap, Collections.singleton(declaredButAbsent));

        assertEquals(artifact1, result.get("duplicateClass"));
    }

    @Test
    void testDuplicateClassIsCreditedToDeclaredArtifactOnly() {
        Dependency plexusUtils = aTestArtifact("plexus-utils");
        Dependency plexusXml = aTestArtifact("plexus-xml");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(plexusUtils, Collections.singleton("Xpp3Dom"));
        artifactClassMap.put(plexusXml, Collections.singleton("Xpp3Dom"));

        Map<String, Dependency> classToArtifactMap = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(
                artifactClassMap, Collections.singleton(plexusXml));
        Map<Dependency, Set<DependencyUsage>> used = DefaultProjectDependencyAnalyzer.buildUsedArtifacts(
                classToArtifactMap, Collections.singleton(new DependencyUsage("Xpp3Dom", "Main")));

        assertIterableEquals(Collections.singletonList(plexusXml), used.keySet());
    }

    @Test
    void testClasslessDeclaredAggregatorIsUsedWhenItsDirectDependencyIsUsed() {
        Dependency jupiter = aTestArtifact("junit-jupiter");
        Dependency api = aTestArtifact("junit-jupiter-api");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(jupiter, Collections.emptySet());
        artifactClassMap.put(api, Collections.singleton("org.junit.jupiter.api.Test"));

        Map<Dependency, Set<DependencyUsage>> used = new LinkedHashMap<>();
        used.put(api, Collections.singleton(new DependencyUsage("org.junit.jupiter.api.Test", "Main")));

        DefaultProjectDependencyAnalyzer.creditClasslessAggregators(
                artifactClassMap, Collections.singleton(jupiter), used, parentOf(jupiter, api));

        assertIterableEquals(Collections.singletonList(jupiter), used.keySet());
        assertEquals(1, used.get(jupiter).size());
    }

    @Test
    void testClasslessDeclaredAggregatorStaysUnusedWhenNothingItBringsInIsUsed() {
        Dependency jupiter = aTestArtifact("junit-jupiter");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(jupiter, Collections.emptySet());

        Map<Dependency, Set<DependencyUsage>> used = new LinkedHashMap<>();

        DefaultProjectDependencyAnalyzer.creditClasslessAggregators(
                artifactClassMap, Collections.singleton(jupiter), used, Collections.emptyMap());

        assertTrue(used.isEmpty());
    }

    @Test
    void testDeclaredChildOfClasslessAggregatorKeepsItsOwnCredit() {
        Dependency jupiter = aTestArtifact("junit-jupiter");
        Dependency api = aTestArtifact("junit-jupiter-api");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(jupiter, Collections.emptySet());
        artifactClassMap.put(api, Collections.singleton("org.junit.jupiter.api.Test"));

        Map<Dependency, Set<DependencyUsage>> used = new LinkedHashMap<>();
        used.put(api, Collections.singleton(new DependencyUsage("org.junit.jupiter.api.Test", "Main")));

        DefaultProjectDependencyAnalyzer.creditClasslessAggregators(
                artifactClassMap, new HashSet<>(Arrays.asList(jupiter, api)), used, parentOf(jupiter, api));

        assertEquals(new HashSet<>(Arrays.asList(jupiter, api)), new HashSet<>(used.keySet()));
    }

    @Test
    void testAggregatorWithClassesIsNotTouched() {
        Dependency lib = aTestArtifact("lib");
        Dependency child = aTestArtifact("child");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(lib, Collections.singleton("lib.Foo"));
        artifactClassMap.put(child, Collections.singleton("child.Bar"));

        Map<Dependency, Set<DependencyUsage>> used = new LinkedHashMap<>();
        used.put(child, Collections.singleton(new DependencyUsage("child.Bar", "Main")));

        DefaultProjectDependencyAnalyzer.creditClasslessAggregators(
                artifactClassMap, Collections.singleton(lib), used, parentOf(lib, child));

        assertIterableEquals(Collections.singletonList(child), used.keySet());
    }

    @Test
    void testBuildClassToArtifactMapWithMultipleClasses() {
        Dependency artifact1 = aTestArtifact("artifact1");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, new HashSet<>(Arrays.asList("class1", "class2")));

        Map<String, Dependency> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);

        assertEquals(2, result.size());
        assertEquals(artifact1, result.get("class1"));
        assertEquals(artifact1, result.get("class2"));
    }

    @Test
    void testBuildUsedArtifacts() {
        Dependency artifact1 = aTestArtifact("artifact1");
        Map<String, Dependency> classToArtifactMap = Collections.singletonMap("class1", artifact1);
        Set<DependencyUsage> dependencyClasses = Collections.singleton(new DependencyUsage("class1", "main"));

        Map<Dependency, Set<DependencyUsage>> result =
                DefaultProjectDependencyAnalyzer.buildUsedArtifacts(classToArtifactMap, dependencyClasses);

        assertEquals(1, result.size());
        assertEquals(1, result.get(artifact1).size());
        assertEquals("class1", result.get(artifact1).iterator().next().getDependencyClass());
    }

    @Test
    void testBuildUsedArtifactsWithMultipleClasses() {
        Dependency artifact1 = aTestArtifact("artifact1");
        Map<String, Dependency> classToArtifactMap = Collections.singletonMap("class1", artifact1);
        Set<DependencyUsage> dependencyClasses = new HashSet<>(
                Arrays.asList(new DependencyUsage("class1", "main"), new DependencyUsage("class1", "test")));

        Map<Dependency, Set<DependencyUsage>> result =
                DefaultProjectDependencyAnalyzer.buildUsedArtifacts(classToArtifactMap, dependencyClasses);

        assertEquals(1, result.size());
        assertEquals(2, result.get(artifact1).size());
    }

    @Test
    void testBuildUsedArtifactsWithJDKExcluded() {
        Dependency artifact1 = aTestArtifact("xml-apis", "xml-apis");
        Map<String, Dependency> classToArtifactMap = Collections.singletonMap("class1", artifact1);
        Set<DependencyUsage> dependencyClasses = Collections.singleton(new DependencyUsage("class1", "main"));

        Map<Dependency, Set<DependencyUsage>> result =
                DefaultProjectDependencyAnalyzer.buildUsedArtifacts(classToArtifactMap, dependencyClasses);

        // Being in JDK, it should be excluded from used artifacts
        assertTrue(result.isEmpty());
    }

    @Test
    void testIncludedInJDK() {
        assertTrue(DefaultProjectDependencyAnalyzer.includedInJDK(aTestArtifact("xml-apis", "xml-apis")));
        assertTrue(DefaultProjectDependencyAnalyzer.includedInJDK(aTestArtifact("xerces", "xmlParserAPIs")));
        assertFalse(DefaultProjectDependencyAnalyzer.includedInJDK(aTestArtifact("groupId", "artifactId")));
    }

    @Test
    void testBuildDeclaredArtifactsSelectsResolvedDirectArtifacts() {
        Dependency direct = aTestArtifact("direct");
        Dependency transitive = aTestArtifact("transitive");
        DependencyCoordinates directCoordinates = toCoordinates(direct);
        when(project.getDependencies()).thenReturn(Collections.singletonList(directCoordinates));

        Set<Dependency> declaredArtifacts =
                DefaultProjectDependencyAnalyzer.buildDeclaredArtifacts(project, Arrays.asList(direct, transitive));
        assertIterableEquals(Collections.singletonList(direct), declaredArtifacts);
        assertSame(direct, declaredArtifacts.iterator().next());
    }

    @Test
    void testBuildDeclaredArtifactsUsesDefaultClassifierFromArtifactType() {
        Dependency testJar = aTestArtifact("groupId", "test-jar", DependencyScope.COMPILE, "jar", "tests");
        DependencyCoordinates coordinates = toCoordinates(testJar);
        // the declaration has no classifier, the type provides it
        Type type = coordinates.getType();
        when(coordinates.getClassifier()).thenReturn(null);
        when(type.getClassifier()).thenReturn("tests");
        when(project.getDependencies()).thenReturn(Collections.singletonList(coordinates));

        assertIterableEquals(
                Collections.singletonList(testJar),
                DefaultProjectDependencyAnalyzer.buildDeclaredArtifacts(project, Collections.singleton(testJar)));
    }

    @Test
    void testBuildDeclaredArtifactsRetainsRelocatedDeclaration() {
        Dependency relocated = aTestArtifact("org.apache.axis", "axis-ant");
        Dependency declared = aTestArtifact("axis", "axis-ant");
        DependencyCoordinates declaration = toCoordinates(declared);
        Type type = declaration.getType();
        // a jar type has no default classifier
        when(type.getClassifier()).thenReturn(null);
        when(project.getDependencies()).thenReturn(Collections.singletonList(declaration));

        Set<Dependency> declaredArtifacts =
                DefaultProjectDependencyAnalyzer.buildDeclaredArtifacts(project, Collections.singleton(relocated));
        assertEquals(1, declaredArtifacts.size());
        Dependency artifact = declaredArtifacts.iterator().next();
        assertEquals("axis", artifact.getGroupId());
        assertEquals("axis-ant", artifact.getArtifactId());
        assertEquals(DependencyScope.COMPILE, artifact.getScope());
        assertEquals("", artifact.getClassifier());
        assertFalse(artifact == relocated);
    }

    @Test
    void testRetainsTestOnlyCompileDependencyWhenGraphCollectionFails() {
        Dependency candidate = aTestArtifact("candidate");
        DependencyCoordinates candidateCoordinates = toCoordinates(candidate);
        when(project.getDependencies()).thenReturn(Collections.singletonList(candidateCoordinates));
        DependencyResolverResult compileResult = dependencyGraph("candidate", "other");
        when(dependencyResolver.collect(any(DependencyResolverRequest.class)))
                .thenReturn(compileResult)
                .thenThrow(new DependencyResolverException("collection failed", new Exception("failure")));

        assertIterableEquals(
                Collections.singletonList(candidate),
                analyzer.getTestArtifactsWithNonTestScope(project, Collections.singleton(candidate)));
    }

    @Test
    void testRemovesDependenciesReachableFromCompileOrRuntimeGraph() {
        Dependency compileCandidate = aTestArtifact("compile-candidate");
        Dependency runtimeCandidate = aTestArtifact("runtime-candidate");
        List<DependencyCoordinates> declared = Arrays.asList(
                toCoordinates(compileCandidate),
                toCoordinates(runtimeCandidate),
                toCoordinates(aTestArtifact("groupId", "compile", DependencyScope.COMPILE)),
                toCoordinates(aTestArtifact("groupId", "provided", DependencyScope.PROVIDED)),
                toCoordinates(aTestArtifact("groupId", "system", DependencyScope.SYSTEM)),
                toCoordinates(aTestArtifact("groupId", "runtime", DependencyScope.RUNTIME)),
                toCoordinates(aTestArtifact("groupId", "test", DependencyScope.TEST)));
        when(project.getDependencies()).thenReturn(declared);
        List<DependencyCoordinates> managed = Collections.singletonList(mock(DependencyCoordinates.class));
        when(project.getManagedDependencies()).thenReturn(managed);
        ProducedArtifact pom = mock(ProducedArtifact.class);
        when(project.getPomArtifact()).thenReturn(pom);

        DependencyResolverResult compileResult = dependencyGraph("compile-candidate");
        DependencyResolverResult runtimeResult = dependencyGraph("runtime-candidate");
        when(dependencyResolver.collect(any(DependencyResolverRequest.class))).thenReturn(compileResult, runtimeResult);

        Set<Dependency> candidates = new LinkedHashSet<>(Arrays.asList(compileCandidate, runtimeCandidate));
        assertTrue(
                analyzer.getTestArtifactsWithNonTestScope(project, candidates).isEmpty());

        ArgumentCaptor<DependencyResolverRequest> requestCaptor =
                ArgumentCaptor.forClass(DependencyResolverRequest.class);
        verify(dependencyResolver, times(2)).collect(requestCaptor.capture());
        List<DependencyResolverRequest> requests = requestCaptor.getAllValues();
        assertEquals(new HashSet<>(Arrays.asList("compile", "provided", "system")), artifactIds(requests.get(0)));
        assertEquals(new HashSet<>(Arrays.asList("compile", "runtime")), artifactIds(requests.get(1)));
        assertEquals(PathScope.MAIN_COMPILE, requests.get(0).getPathScope());
        assertEquals(PathScope.MAIN_RUNTIME, requests.get(1).getPathScope());
        for (DependencyResolverRequest request : requests) {
            assertEquals(DependencyResolverRequest.RequestType.COLLECT, request.getRequestType());
            assertTrue(request.getRootArtifact().isPresent());
            assertSame(pom, request.getRootArtifact().get());
            assertIterableEquals(managed, request.getManagedDependencies());
        }
    }

    @Test
    void buildCandidateArtifactsByUsedClassShouldReturnAllProviders() {
        Dependency first = aTestArtifact("first");
        Dependency second = aTestArtifact("second");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(first, Collections.singleton("com.example.Cls"));
        artifactClassMap.put(second, Collections.singleton("com.example.Cls"));

        Set<String> used = Collections.singleton("com.example.Cls");
        Map<String, Set<Dependency>> candidates =
                DefaultProjectDependencyAnalyzer.buildCandidateArtifactsByUsedClass(artifactClassMap, used);

        assertIterableEquals(Arrays.asList(first, second), candidates.get("com.example.Cls"));
    }

    @Test
    void buildSelectedArtifactByUsedClassShouldKeepSelectedProvider() {
        Dependency first = aTestArtifact("first");
        Dependency second = aTestArtifact("second");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(first, Collections.singleton("com.example.Cls"));
        artifactClassMap.put(second, Collections.singleton("com.example.Cls"));

        Set<String> used = Collections.singleton("com.example.Cls");
        Map<String, Dependency> classToArtifactMap =
                DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);
        Map<String, Dependency> selected =
                DefaultProjectDependencyAnalyzer.buildSelectedArtifactByUsedClass(classToArtifactMap, used);

        assertEquals(first, selected.get("com.example.Cls"));
    }

    private static Set<String> artifactIds(DependencyResolverRequest request) {
        return request.getDependencies().stream()
                .map(DependencyCoordinates::getArtifactId)
                .collect(Collectors.toSet());
    }

    /**
     * The parent ids of a resolved dependency graph, as the analyzer reads them from the graph.
     */
    private static Map<String, String> parentOf(Dependency parent, Dependency child) {
        Map<String, String> parentIds = new HashMap<>();
        parentIds.put(versionlessId(child), versionlessId(parent));
        return parentIds;
    }

    private static String versionlessId(Dependency dependency) {
        StringBuilder id = new StringBuilder();
        id.append(dependency.getGroupId())
                .append(':')
                .append(dependency.getArtifactId())
                .append(':')
                .append(dependency.getExtension());
        String classifier = dependency.getClassifier();
        if (classifier != null && !classifier.isEmpty()) {
            id.append(':').append(classifier);
        }
        return id.toString();
    }

    @Test
    void testAnalyzeUsesTheResolvedDependencies() throws Exception {
        Dependency declared = aTestArtifact("groupId", "declared", DependencyScope.COMPILE);
        Path jar = Files.createTempFile(tempDir, "declared", ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            // an empty jar, the dependency does not provide any class
        }
        DependencyCoordinates coordinates = toCoordinates(declared);
        Node root = rootWithChildren(declared);
        when(project.getDependencies()).thenReturn(Collections.singletonList(coordinates));
        DependencyResolverResult result = mock(DependencyResolverResult.class);
        when(result.getDependencies()).thenReturn(Collections.singletonMap(declared, jar));
        when(result.getRoot()).thenReturn(root);

        ProjectDependencyAnalysis analysis = analyzer.analyze(project, result, null);

        assertIterableEquals(Collections.singletonList(declared), analysis.getUnusedDeclaredArtifacts());
        assertTrue(analysis.getUsedDeclaredArtifacts().isEmpty());
        assertTrue(analysis.getUsedUndeclaredArtifacts().isEmpty());
    }

    private Node rootWithChildren(Dependency... dependencies) {
        Node root = mock(Node.class);
        List<Node> children = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            Node node = mock(Node.class);
            when(node.getDependency()).thenReturn(dependency);
            when(node.getArtifact()).thenReturn(dependency);
            when(node.getChildren()).thenReturn(Collections.emptyList());
            children.add(node);
        }
        when(root.getChildren()).thenReturn(children);
        return root;
    }

    private DependencyResolverResult dependencyGraph(String... artifactIds) {
        Node root = mock(Node.class);
        List<Node> children = new ArrayList<>();
        for (String artifactId : artifactIds) {
            Dependency artifact = aTestArtifact(artifactId);
            Node node = mock(Node.class);
            when(node.getArtifact()).thenReturn(artifact);
            when(node.getChildren()).thenReturn(Collections.emptyList());
            children.add(node);
        }
        when(root.getChildren()).thenReturn(children);
        DependencyResolverResult result = mock(DependencyResolverResult.class);
        when(result.getRoot()).thenReturn(root);
        return result;
    }

    private DependencyCoordinates toCoordinates(Dependency dependency) {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();
        String classifier = dependency.getClassifier();
        String extension = dependency.getExtension();
        DependencyScope scope = dependency.getScope();
        Type type = mock(Type.class);
        when(type.getExtension()).thenReturn(extension);
        when(type.getClassifier()).thenReturn("");
        DependencyCoordinates coordinates = mock(DependencyCoordinates.class);
        when(coordinates.getGroupId()).thenReturn(groupId);
        when(coordinates.getArtifactId()).thenReturn(artifactId);
        when(coordinates.getClassifier()).thenReturn(classifier);
        when(coordinates.getType()).thenReturn(type);
        when(coordinates.getScope()).thenReturn(scope);
        return coordinates;
    }

    private Dependency aTestArtifact(String artifactId) {
        return aTestArtifact("groupId", artifactId);
    }

    private Dependency aTestArtifact(String groupId, String artifactId) {
        return aTestArtifact(groupId, artifactId, DependencyScope.COMPILE);
    }

    private Dependency aTestArtifact(String groupId, String artifactId, DependencyScope scope) {
        return aTestArtifact(groupId, artifactId, scope, "jar", "");
    }

    private Dependency aTestArtifact(
            String groupId, String artifactId, DependencyScope scope, String extension, String classifier) {
        Dependency dependency = mock(Dependency.class);
        when(dependency.getGroupId()).thenReturn(groupId);
        when(dependency.getArtifactId()).thenReturn(artifactId);
        when(dependency.getScope()).thenReturn(scope);
        when(dependency.getExtension()).thenReturn(extension);
        when(dependency.getClassifier()).thenReturn(classifier);
        return dependency;
    }
}
