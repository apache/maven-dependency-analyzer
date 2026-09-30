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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
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
        analyzer = new DefaultProjectDependencyAnalyzer();
    }

    @Test
    void testBuildClassToArtifactMap() {
        Dependency artifact1 = aTestArtifact("artifact1");
        Dependency artifact2 = aTestArtifact("artifact2");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, Collections.singleton("class1"));
        artifactClassMap.put(artifact2, Collections.singleton("class2"));

        Map<String, Dependency> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);

        assertThat(result).hasSize(2);
        assertThat(result.get("class1")).isEqualTo(artifact1);
        assertThat(result.get("class2")).isEqualTo(artifact2);
    }

    @Test
    void testBuildClassToArtifactMapWithDuplicates() {
        Dependency artifact1 = aTestArtifact("artifact1");
        Dependency artifact2 = aTestArtifact("artifact2");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, Collections.singleton("duplicateClass"));
        artifactClassMap.put(artifact2, Collections.singleton("duplicateClass"));

        Map<String, Dependency> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);

        assertThat(result).hasSize(1);
        // Should favor the first artifact encountered
        assertThat(result.get("duplicateClass")).isEqualTo(artifact1);
    }

    @Test
    void testBuildClassToArtifactMapWithMultipleClasses() {
        Dependency artifact1 = aTestArtifact("artifact1");

        Map<Dependency, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, new HashSet<>(Arrays.asList("class1", "class2")));

        Map<String, Dependency> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);

        assertThat(result).hasSize(2);
        assertThat(result.get("class1")).isEqualTo(artifact1);
        assertThat(result.get("class2")).isEqualTo(artifact1);
    }

    @Test
    void testBuildUsedArtifacts() {
        Dependency artifact1 = aTestArtifact("artifact1");
        Map<String, Dependency> classToArtifactMap = Collections.singletonMap("class1", artifact1);
        Set<DependencyUsage> dependencyClasses = Collections.singleton(new DependencyUsage("class1", "main"));

        Map<Dependency, Set<DependencyUsage>> result =
                DefaultProjectDependencyAnalyzer.buildUsedArtifacts(classToArtifactMap, dependencyClasses);

        assertThat(result).hasSize(1);
        assertThat(result.get(artifact1)).hasSize(1);
        assertThat(result.get(artifact1).iterator().next().getDependencyClass()).isEqualTo("class1");
    }

    @Test
    void testBuildUsedArtifactsWithMultipleClasses() {
        Dependency artifact1 = aTestArtifact("artifact1");
        Map<String, Dependency> classToArtifactMap = Collections.singletonMap("class1", artifact1);
        Set<DependencyUsage> dependencyClasses = new HashSet<>(
                Arrays.asList(new DependencyUsage("class1", "main"), new DependencyUsage("class1", "test")));

        Map<Dependency, Set<DependencyUsage>> result =
                DefaultProjectDependencyAnalyzer.buildUsedArtifacts(classToArtifactMap, dependencyClasses);

        assertThat(result).hasSize(1);
        assertThat(result.get(artifact1)).hasSize(2);
    }

    @Test
    void testBuildUsedArtifactsWithJDKExcluded() {
        Dependency artifact1 = aTestArtifact("xml-apis", "xml-apis");
        Map<String, Dependency> classToArtifactMap = Collections.singletonMap("class1", artifact1);
        Set<DependencyUsage> dependencyClasses = Collections.singleton(new DependencyUsage("class1", "main"));

        Map<Dependency, Set<DependencyUsage>> result =
                DefaultProjectDependencyAnalyzer.buildUsedArtifacts(classToArtifactMap, dependencyClasses);

        // Being in JDK, it should be excluded from used artifacts
        assertThat(result).isEmpty();
    }

    @Test
    void testIncludedInJDK() {
        assertThat(DefaultProjectDependencyAnalyzer.includedInJDK(aTestArtifact("xml-apis", "xml-apis")))
                .isTrue();
        assertThat(DefaultProjectDependencyAnalyzer.includedInJDK(aTestArtifact("xerces", "xmlParserAPIs")))
                .isTrue();
        assertThat(DefaultProjectDependencyAnalyzer.includedInJDK(aTestArtifact("groupId", "artifactId")))
                .isFalse();
    }

    @Test
    void testBuildDeclaredArtifactsSelectsResolvedDirectArtifacts() {
        Dependency direct = aTestArtifact("direct");
        Dependency transitive = aTestArtifact("transitive");
        DependencyCoordinates directCoordinates = toCoordinates(direct);
        when(project.getDependencies()).thenReturn(Collections.singletonList(directCoordinates));

        assertThat(DefaultProjectDependencyAnalyzer.buildDeclaredArtifacts(
                        session, project, Arrays.asList(direct, transitive)))
                .containsExactly(direct)
                .first()
                .isSameAs(direct);
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

        assertThat(DefaultProjectDependencyAnalyzer.buildDeclaredArtifacts(
                        session, project, Collections.singleton(testJar)))
                .containsExactly(testJar);
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

        assertThat(DefaultProjectDependencyAnalyzer.buildDeclaredArtifacts(
                        session, project, Collections.singleton(relocated)))
                .singleElement()
                .satisfies(artifact -> {
                    assertThat(artifact.getGroupId()).isEqualTo("axis");
                    assertThat(artifact.getArtifactId()).isEqualTo("axis-ant");
                    assertThat(artifact.getScope()).isEqualTo(DependencyScope.COMPILE);
                    assertThat(artifact.getClassifier()).isEmpty();
                    assertThat(artifact).isNotSameAs(relocated);
                });
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

        assertThat(analyzer.getTestArtifactsWithNonTestScope(session, project, Collections.singleton(candidate)))
                .containsExactly(candidate);
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
        assertThat(analyzer.getTestArtifactsWithNonTestScope(session, project, candidates))
                .isEmpty();

        ArgumentCaptor<DependencyResolverRequest> requestCaptor =
                ArgumentCaptor.forClass(DependencyResolverRequest.class);
        verify(dependencyResolver, times(2)).collect(requestCaptor.capture());
        List<DependencyResolverRequest> requests = requestCaptor.getAllValues();
        assertThat(artifactIds(requests.get(0))).containsExactlyInAnyOrder("compile", "provided", "system");
        assertThat(artifactIds(requests.get(1))).containsExactlyInAnyOrder("compile", "runtime");
        assertThat(requests.get(0).getPathScope()).isEqualTo(PathScope.MAIN_COMPILE);
        assertThat(requests.get(1).getPathScope()).isEqualTo(PathScope.MAIN_RUNTIME);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.getRequestType()).isEqualTo(DependencyResolverRequest.RequestType.COLLECT);
            assertThat(request.getRootArtifact()).containsSame(pom);
            assertThat(request.getManagedDependencies()).containsExactlyElementsOf(managed);
        });
    }

    private static Set<String> artifactIds(DependencyResolverRequest request) {
        return request.getDependencies().stream()
                .map(DependencyCoordinates::getArtifactId)
                .collect(Collectors.toSet());
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
