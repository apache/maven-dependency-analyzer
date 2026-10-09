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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests <code>DefaultProjectDependencyAnalyzer</code>.
 *
 * @see DefaultProjectDependencyAnalyzer
 */
class DefaultProjectDependencyAnalyzerTest {
    private RepositorySystem repositorySystem;

    private MavenSession mavenSession;

    private ArtifactHandlerManager artifactHandlerManager;

    private DefaultProjectDependencyAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        repositorySystem = mock(RepositorySystem.class);
        mavenSession = mock(MavenSession.class);
        artifactHandlerManager = mock(ArtifactHandlerManager.class);
        when(artifactHandlerManager.getArtifactHandler(anyString())).thenReturn(mock(ArtifactHandler.class));
        analyzer = new DefaultProjectDependencyAnalyzer(
                repositorySystem, Collections.emptyList(), () -> mavenSession, artifactHandlerManager);
    }

    @Test
    void testBuildClassToArtifactMap() {
        Artifact artifact1 = aTestArtifact("artifact1");
        Artifact artifact2 = aTestArtifact("artifact2");

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, Collections.singleton("class1"));
        artifactClassMap.put(artifact2, Collections.singleton("class2"));

        Map<String, Artifact> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);

        assertThat(result).hasSize(2);
        assertThat(result.get("class1")).isEqualTo(artifact1);
        assertThat(result.get("class2")).isEqualTo(artifact2);
    }

    @Test
    void testBuildClassToArtifactMapWithDuplicates() {
        Artifact artifact1 = aTestArtifact("artifact1");
        Artifact artifact2 = aTestArtifact("artifact2");

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, Collections.singleton("duplicateClass"));
        artifactClassMap.put(artifact2, Collections.singleton("duplicateClass"));

        Map<String, Artifact> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);

        assertThat(result).hasSize(1);
        // Should favor the first artifact encountered
        assertThat(result.get("duplicateClass")).isEqualTo(artifact1);
    }

    @Test
    void testBuildClassToArtifactMapWithMultipleClasses() {
        Artifact artifact1 = aTestArtifact("artifact1");

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, new HashSet<>(Arrays.asList("class1", "class2")));

        Map<String, Artifact> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);

        assertThat(result).hasSize(2);
        assertThat(result.get("class1")).isEqualTo(artifact1);
        assertThat(result.get("class2")).isEqualTo(artifact1);
    }

    @Test
    void testBuildUsedArtifacts() {
        Artifact artifact1 = aTestArtifact("artifact1");
        Map<String, Artifact> classToArtifactMap = Collections.singletonMap("class1", artifact1);
        Set<DependencyUsage> dependencyClasses = Collections.singleton(new DependencyUsage("class1", "main"));

        Map<Artifact, Set<DependencyUsage>> result =
                DefaultProjectDependencyAnalyzer.buildUsedArtifacts(classToArtifactMap, dependencyClasses);

        assertThat(result).hasSize(1);
        assertThat(result.get(artifact1)).hasSize(1);
        assertThat(result.get(artifact1).iterator().next().getDependencyClass()).isEqualTo("class1");
    }

    @Test
    void testBuildUsedArtifactsWithMultipleClasses() {
        Artifact artifact1 = aTestArtifact("artifact1");
        Map<String, Artifact> classToArtifactMap = Collections.singletonMap("class1", artifact1);
        Set<DependencyUsage> dependencyClasses = new HashSet<>(
                Arrays.asList(new DependencyUsage("class1", "main"), new DependencyUsage("class1", "test")));

        Map<Artifact, Set<DependencyUsage>> result =
                DefaultProjectDependencyAnalyzer.buildUsedArtifacts(classToArtifactMap, dependencyClasses);

        assertThat(result).hasSize(1);
        assertThat(result.get(artifact1)).hasSize(2);
    }

    @Test
    void testBuildUsedArtifactsWithJDKExcluded() {
        Artifact artifact1 = aTestArtifact("xml-apis", "xml-apis");
        Map<String, Artifact> classToArtifactMap = Collections.singletonMap("class1", artifact1);
        Set<DependencyUsage> dependencyClasses = Collections.singleton(new DependencyUsage("class1", "main"));

        Map<Artifact, Set<DependencyUsage>> result =
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
        Artifact direct = aTestArtifact("direct");
        Artifact transitive = aTestArtifact("transitive");
        MavenProject project = new MavenProject();
        project.setDependencies(Collections.singletonList(toDependency(direct)));
        project.setArtifacts(new LinkedHashSet<>(Arrays.asList(direct, transitive)));

        assertThat(DefaultProjectDependencyAnalyzer.buildDeclaredArtifacts(project, artifactHandlerManager))
                .containsExactly(direct)
                .first()
                .isSameAs(direct);
    }

    @Test
    void testBuildDeclaredArtifactsUsesDefaultClassifierFromArtifactType() {
        Artifact testJar = new DefaultArtifact(
                "groupId",
                "test-jar",
                VersionRange.createFromVersion("1.0"),
                Artifact.SCOPE_COMPILE,
                "test-jar",
                "tests",
                null);
        Dependency dependency = toDependency(testJar);
        dependency.setClassifier(null);
        MavenProject project = new MavenProject();
        project.setDependencies(Collections.singletonList(dependency));
        project.setArtifacts(Collections.singleton(testJar));
        ArtifactHandler testJarHandler = mock(ArtifactHandler.class);
        when(artifactHandlerManager.getArtifactHandler("test-jar")).thenReturn(testJarHandler);
        when(testJarHandler.getClassifier()).thenReturn("tests");

        assertThat(DefaultProjectDependencyAnalyzer.buildDeclaredArtifacts(project, artifactHandlerManager))
                .containsExactly(testJar);
    }

    @Test
    void testBuildDeclaredArtifactsRetainsRelocatedDeclaration() {
        Dependency declaration = new Dependency();
        declaration.setGroupId("axis");
        declaration.setArtifactId("axis-ant");
        declaration.setVersion("1.4");
        MavenProject project = new MavenProject();
        project.setDependencies(Collections.singletonList(declaration));
        Artifact relocated = aTestArtifact("org.apache.axis", "axis-ant");
        project.setArtifacts(Collections.singleton(relocated));

        assertThat(DefaultProjectDependencyAnalyzer.buildDeclaredArtifacts(project, artifactHandlerManager))
                .singleElement()
                .satisfies(artifact -> {
                    assertThat(artifact.getGroupId()).isEqualTo("axis");
                    assertThat(artifact.getArtifactId()).isEqualTo("axis-ant");
                    assertThat(artifact.getVersion()).isEqualTo("1.4");
                    assertThat(artifact).isNotSameAs(relocated);
                });
    }

    @Test
    void testRetainsTestOnlyCompileDependencyWithoutRepositorySession() {
        Artifact candidate = aTestArtifact("candidate");

        assertThat(analyzer.getTestArtifactsWithNonTestScope(new MavenProject(), Collections.singleton(candidate)))
                .containsExactly(candidate);
        verifyNoInteractions(repositorySystem);
    }

    @Test
    void testRetainsTestOnlyCompileDependencyWhenRuntimeGraphCollectionFails() throws Exception {
        Artifact candidate = aTestArtifact("candidate");
        MavenProject project = projectWithRepositorySession(candidate);
        CollectRequest failedRequest = new CollectRequest();
        when(repositorySystem.collectDependencies(any(RepositorySystemSession.class), any(CollectRequest.class)))
                .thenReturn(dependencyGraph("candidate", "2.0"))
                .thenThrow(new DependencyCollectionException(new CollectResult(failedRequest)));

        assertThat(analyzer.getTestArtifactsWithNonTestScope(project, Collections.singleton(candidate)))
                .containsExactly(candidate);
    }

    @Test
    void testRemovesDependenciesReachableFromCompileOrRuntimeGraph() throws Exception {
        Artifact compileCandidate = aTestArtifact("compile-candidate");
        Artifact runtimeCandidate = aTestArtifact("runtime-candidate");
        Artifact compile = aTestArtifactWithScope("compile", Artifact.SCOPE_COMPILE);
        Artifact provided = aTestArtifactWithScope("provided", Artifact.SCOPE_PROVIDED);
        Artifact system = aTestArtifactWithScope("system", Artifact.SCOPE_SYSTEM);
        Artifact runtime = aTestArtifactWithScope("runtime", Artifact.SCOPE_RUNTIME);
        Artifact test = aTestArtifactWithScope("test", Artifact.SCOPE_TEST);
        MavenProject project = projectWithRepositorySession(
                compileCandidate, runtimeCandidate, compile, provided, system, runtime, test);
        Dependency managed = toDependency(aTestArtifact("managed"));
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.addDependency(managed);
        project.getModel().setDependencyManagement(dependencyManagement);
        project.setRemoteArtifactRepositories(Collections.singletonList(new MavenArtifactRepository(
                "central", "https://repo.example.org", new DefaultRepositoryLayout(), null, null)));
        List<RemoteRepository> remoteRepositories = project.getRemoteProjectRepositories();
        assertThat(remoteRepositories).hasSize(1);

        when(repositorySystem.collectDependencies(any(RepositorySystemSession.class), any(CollectRequest.class)))
                .thenReturn(dependencyGraph("compile-candidate", "2.0"), dependencyGraph("runtime-candidate", "2.0"));

        Set<Artifact> candidates = new LinkedHashSet<>(Arrays.asList(compileCandidate, runtimeCandidate));
        assertThat(analyzer.getTestArtifactsWithNonTestScope(project, candidates))
                .isEmpty();

        ArgumentCaptor<CollectRequest> requestCaptor = ArgumentCaptor.forClass(CollectRequest.class);
        verify(repositorySystem, times(2))
                .collectDependencies(eq(mavenSession.getRepositorySession()), requestCaptor.capture());
        List<CollectRequest> requests = requestCaptor.getAllValues();
        assertThat(artifactIds(requests.get(0))).containsExactlyInAnyOrder("compile", "provided", "system");
        assertThat(artifactIds(requests.get(1))).containsExactlyInAnyOrder("compile", "runtime");
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.getRequestContext()).isEqualTo("project");
            assertThat(request.getRepositories()).isEqualTo(remoteRepositories);
            assertThat(request.getManagedDependencies())
                    .extracting(d -> d.getArtifact().getArtifactId())
                    .containsExactly("managed");
        });
    }

    @Test
    void testAppliesRepositorySessionDecorators() throws Exception {
        Artifact candidate = aTestArtifact("candidate");
        MavenProject project = projectWithRepositorySession(candidate);
        RepositorySystemSession decorated = mock(RepositorySystemSession.class);
        when(decorated.getArtifactTypeRegistry()).thenReturn(new DefaultArtifactTypeRegistry());
        analyzer = new DefaultProjectDependencyAnalyzer(
                repositorySystem,
                Collections.singletonList((p, session) -> p == project ? decorated : session),
                () -> mavenSession,
                artifactHandlerManager);
        when(repositorySystem.collectDependencies(any(RepositorySystemSession.class), any(CollectRequest.class)))
                .thenReturn(dependencyGraph("other", "1.0"));

        assertThat(analyzer.getTestArtifactsWithNonTestScope(project, Collections.singleton(candidate)))
                .containsExactly(candidate);
        verify(repositorySystem, times(2)).collectDependencies(eq(decorated), any(CollectRequest.class));
    }

    private MavenProject projectWithRepositorySession(Artifact... dependencyArtifacts) {
        MavenProject project = new MavenProject();
        project.setDependencies(
                Arrays.stream(dependencyArtifacts).map(this::toDependency).collect(Collectors.toList()));
        RepositorySystemSession repositorySession = mock(RepositorySystemSession.class);
        when(repositorySession.getArtifactTypeRegistry()).thenReturn(new DefaultArtifactTypeRegistry());
        when(mavenSession.getRepositorySession()).thenReturn(repositorySession);
        return project;
    }

    private CollectResult dependencyGraph(String artifactId, String version) {
        DependencyNode root = new DefaultDependencyNode((org.eclipse.aether.graph.Dependency) null);
        root.setChildren(Collections.singletonList(new DefaultDependencyNode(
                new org.eclipse.aether.artifact.DefaultArtifact("groupId", artifactId, "jar", version))));
        return new CollectResult(new CollectRequest()).setRoot(root);
    }

    private Set<String> artifactIds(CollectRequest request) {
        return request.getDependencies().stream()
                .map(d -> d.getArtifact().getArtifactId())
                .collect(Collectors.toSet());
    }

    private Dependency toDependency(Artifact artifact) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(artifact.getGroupId());
        dependency.setArtifactId(artifact.getArtifactId());
        dependency.setVersion(artifact.getVersion());
        dependency.setScope(artifact.getScope());
        dependency.setType(artifact.getType());
        dependency.setClassifier(artifact.getClassifier());
        return dependency;
    }

    private Artifact aTestArtifact(String artifactId) {
        return aTestArtifact("groupId", artifactId);
    }

    private Artifact aTestArtifact(String groupId, String artifactId) {
        return aTestArtifact(groupId, artifactId, Artifact.SCOPE_COMPILE);
    }

    private Artifact aTestArtifactWithScope(String artifactId, String scope) {
        return aTestArtifact("groupId", artifactId, scope);
    }

    private Artifact aTestArtifact(String groupId, String artifactId, String scope) {
        return new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion("1.0"), scope, "jar", "", null);
    }
}
