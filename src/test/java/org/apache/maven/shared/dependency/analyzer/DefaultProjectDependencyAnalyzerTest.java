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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void buildArtifactClassMapSkipsModuleInfoAndPackageInfoInJars(@TempDir Path tempDir) throws IOException {
        File aggregatorJar = jarWith(tempDir.resolve("aggregator.jar"), "module-info.class", "META-INF/MANIFEST.MF");
        File libraryJar = jarWith(
                tempDir.resolve("library.jar"),
                "module-info.class",
                "org/example/package-info.class",
                "org/example/Library.class",
                "META-INF/versions/9/module-info.class");

        Artifact aggregator = aTestArtifact("aggregator");
        aggregator.setFile(aggregatorJar);
        Artifact library = aTestArtifact("library");
        library.setFile(libraryJar);

        MavenProject project = new MavenProject();
        project.setArtifacts(new LinkedHashSet<>(Arrays.asList(aggregator, library)));

        Map<Artifact, Set<String>> artifactClassMap =
                analyzer.buildArtifactClassMap(project, new ClassesPatterns(Collections.emptyList()));

        assertEquals(Collections.emptySet(), artifactClassMap.get(aggregator));
        assertEquals(Collections.singleton("org.example.Library"), artifactClassMap.get(library));
    }

    @Test
    void buildArtifactClassMapKeepsClassesWithDashInTheirName(@TempDir Path tempDir) throws IOException {
        // Kotlin emits classes such as okio/-Util
        Path jar = tempDir.resolve("kotlinLibrary.jar");
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "p/-Util", null, "java/lang/Object", null);
        writer.visitEnd();
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("p/-Util.class"));
            out.write(writer.toByteArray());
            out.closeEntry();
        }
        Artifact library = aTestArtifact("kotlinLibrary");
        library.setFile(jar.toFile());
        MavenProject project = new MavenProject();
        project.setArtifacts(new LinkedHashSet<>(Collections.singletonList(library)));

        Map<Artifact, Set<String>> artifactClassMap =
                analyzer.buildArtifactClassMap(project, new ClassesPatterns(Collections.emptyList()));

        assertEquals(Collections.singleton("p.-Util"), artifactClassMap.get(library));
    }

    @Test
    void analyzeListsUsedUndeclaredArtifactsInResolutionOrder(@TempDir Path tempDir) throws Exception {
        List<Artifact> resolved = new ArrayList<>();
        Set<DependencyUsage> usages = new HashSet<>();
        for (char c = 'z'; c >= 'a'; c--) {
            Artifact artifact = aTestArtifact(String.valueOf(c));
            artifact.setFile(jarWith(tempDir.resolve(c + ".jar"), "lib/" + c + "/Type.class"));
            resolved.add(artifact);
            usages.add(new DependencyUsage("lib." + c + ".Type", "my.App"));
        }
        MavenProject project = new MavenProject();
        project.setArtifacts(new LinkedHashSet<>(resolved));

        ProjectDependencyAnalysis analysis = new DefaultProjectDependencyAnalyzer(
                        Collections.singletonList((p, excluded) -> usages),
                        Collections.emptyList(),
                        artifactHandlerManager)
                .analyze(project, Collections.emptyList());

        assertIterableEquals(resolved, analysis.getUsedUndeclaredArtifacts());
        assertIterableEquals(
                resolved, analysis.getUsedUndeclaredArtifactsWithUsages().keySet());
    }

    private static File jarWith(Path path, String... entries) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            for (String entry : entries) {
                jar.putNextEntry(new ZipEntry(entry));
                jar.closeEntry();
            }
        }
        return path.toFile();
    }

    @Test
    void testBuildClassToArtifactMap() {
        Artifact artifact1 = aTestArtifact("artifact1");
        Artifact artifact2 = aTestArtifact("artifact2");

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, Collections.singleton("class1"));
        artifactClassMap.put(artifact2, Collections.singleton("class2"));

        Map<String, Artifact> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);

        assertEquals(2, result.size());
        assertEquals(artifact1, result.get("class1"));
        assertEquals(artifact2, result.get("class2"));
    }

    @Test
    void testBuildClassToArtifactMapWithDuplicates() {
        Artifact artifact1 = aTestArtifact("artifact1");
        Artifact artifact2 = aTestArtifact("artifact2");

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, Collections.singleton("duplicateClass"));
        artifactClassMap.put(artifact2, Collections.singleton("duplicateClass"));

        Map<String, Artifact> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);

        assertEquals(1, result.size());
        // Should favor the first artifact encountered
        assertEquals(artifact1, result.get("duplicateClass"));
    }

    @Test
    void testBuildClassToArtifactMapWithDuplicatesPrefersDeclared() {
        Artifact undeclared = aTestArtifact("undeclared");
        Artifact declared = aTestArtifact("declared");

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(undeclared, new HashSet<>(Arrays.asList("duplicateClass", "otherClass")));
        artifactClassMap.put(declared, Collections.singleton("duplicateClass"));

        Map<String, Artifact> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(
                artifactClassMap, Collections.singleton(declared));

        assertEquals(2, result.size());
        assertEquals(declared, result.get("duplicateClass"));
        assertEquals(undeclared, result.get("otherClass"));
    }

    @Test
    void testBuildClassToArtifactMapWithDuplicatesAndNoneDeclaredKeepsFirst() {
        Artifact artifact1 = aTestArtifact("artifact1");
        Artifact artifact2 = aTestArtifact("artifact2");
        Artifact declaredButAbsent = aTestArtifact("absent");

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, Collections.singleton("duplicateClass"));
        artifactClassMap.put(artifact2, Collections.singleton("duplicateClass"));

        Map<String, Artifact> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(
                artifactClassMap, Collections.singleton(declaredButAbsent));

        assertEquals(artifact1, result.get("duplicateClass"));
    }

    @Test
    void testDuplicateClassIsCreditedToDeclaredArtifactOnly() {
        Artifact plexusUtils = aTestArtifact("plexus-utils");
        Artifact plexusXml = aTestArtifact("plexus-xml");

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(plexusUtils, Collections.singleton("Xpp3Dom"));
        artifactClassMap.put(plexusXml, Collections.singleton("Xpp3Dom"));

        Map<String, Artifact> classToArtifactMap = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(
                artifactClassMap, Collections.singleton(plexusXml));
        Map<Artifact, Set<DependencyUsage>> used = DefaultProjectDependencyAnalyzer.buildUsedArtifacts(
                classToArtifactMap, Collections.singleton(new DependencyUsage("Xpp3Dom", "Main")));

        assertIterableEquals(Collections.singletonList(plexusXml), used.keySet());
    }

    @Test
    void testClasslessDeclaredAggregatorIsUsedWhenItsDirectDependencyIsUsed() {
        Artifact jupiter = aTestArtifact("junit-jupiter");
        Artifact api = aTestArtifact("junit-jupiter-api");
        api.setDependencyTrail(Arrays.asList(
                "groupId:project:jar:1.0", "groupId:junit-jupiter:jar:1.0", "groupId:junit-jupiter-api:jar:1.0"));

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(jupiter, Collections.emptySet());
        artifactClassMap.put(api, Collections.singleton("org.junit.jupiter.api.Test"));

        Map<Artifact, Set<DependencyUsage>> used = new LinkedHashMap<>();
        used.put(api, Collections.singleton(new DependencyUsage("org.junit.jupiter.api.Test", "Main")));

        DefaultProjectDependencyAnalyzer.creditClasslessAggregators(
                artifactClassMap, Collections.singleton(jupiter), used);

        assertIterableEquals(Collections.singletonList(jupiter), used.keySet());
        assertEquals(1, used.get(jupiter).size());
    }

    @Test
    void testClasslessDeclaredAggregatorStaysUnusedWhenNothingItBringsInIsUsed() {
        Artifact jupiter = aTestArtifact("junit-jupiter");

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(jupiter, Collections.emptySet());

        Map<Artifact, Set<DependencyUsage>> used = new LinkedHashMap<>();

        DefaultProjectDependencyAnalyzer.creditClasslessAggregators(
                artifactClassMap, Collections.singleton(jupiter), used);

        assertTrue(used.isEmpty());
    }

    @Test
    void testDeclaredChildOfClasslessAggregatorKeepsItsOwnCredit() {
        Artifact jupiter = aTestArtifact("junit-jupiter");
        Artifact api = aTestArtifact("junit-jupiter-api");
        api.setDependencyTrail(Arrays.asList(
                "groupId:project:jar:1.0", "groupId:junit-jupiter:jar:1.0", "groupId:junit-jupiter-api:jar:1.0"));

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(jupiter, Collections.emptySet());
        artifactClassMap.put(api, Collections.singleton("org.junit.jupiter.api.Test"));

        Map<Artifact, Set<DependencyUsage>> used = new LinkedHashMap<>();
        used.put(api, Collections.singleton(new DependencyUsage("org.junit.jupiter.api.Test", "Main")));

        DefaultProjectDependencyAnalyzer.creditClasslessAggregators(
                artifactClassMap, new HashSet<>(Arrays.asList(jupiter, api)), used);

        assertEquals(new HashSet<>(Arrays.asList(jupiter, api)), new HashSet<>(used.keySet()));
    }

    @Test
    void testAggregatorWithClassesIsNotTouched() {
        Artifact lib = aTestArtifact("lib");
        Artifact child = aTestArtifact("child");
        child.setDependencyTrail(
                Arrays.asList("groupId:project:jar:1.0", "groupId:lib:jar:1.0", "groupId:child:jar:1.0"));

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(lib, Collections.singleton("lib.Foo"));
        artifactClassMap.put(child, Collections.singleton("child.Bar"));

        Map<Artifact, Set<DependencyUsage>> used = new LinkedHashMap<>();
        used.put(child, Collections.singleton(new DependencyUsage("child.Bar", "Main")));

        DefaultProjectDependencyAnalyzer.creditClasslessAggregators(artifactClassMap, Collections.singleton(lib), used);

        assertIterableEquals(Collections.singletonList(child), used.keySet());
    }

    @Test
    void testBuildClassToArtifactMapWithMultipleClasses() {
        Artifact artifact1 = aTestArtifact("artifact1");

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(artifact1, new HashSet<>(Arrays.asList("class1", "class2")));

        Map<String, Artifact> result = DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);

        assertEquals(2, result.size());
        assertEquals(artifact1, result.get("class1"));
        assertEquals(artifact1, result.get("class2"));
    }

    @Test
    void testBuildUsedArtifacts() {
        Artifact artifact1 = aTestArtifact("artifact1");
        Map<String, Artifact> classToArtifactMap = Collections.singletonMap("class1", artifact1);
        Set<DependencyUsage> dependencyClasses = Collections.singleton(new DependencyUsage("class1", "main"));

        Map<Artifact, Set<DependencyUsage>> result =
                DefaultProjectDependencyAnalyzer.buildUsedArtifacts(classToArtifactMap, dependencyClasses);

        assertEquals(1, result.size());
        assertEquals(1, result.get(artifact1).size());
        assertEquals("class1", result.get(artifact1).iterator().next().getDependencyClass());
    }

    @Test
    void testBuildUsedArtifactsWithMultipleClasses() {
        Artifact artifact1 = aTestArtifact("artifact1");
        Map<String, Artifact> classToArtifactMap = Collections.singletonMap("class1", artifact1);
        Set<DependencyUsage> dependencyClasses = new HashSet<>(
                Arrays.asList(new DependencyUsage("class1", "main"), new DependencyUsage("class1", "test")));

        Map<Artifact, Set<DependencyUsage>> result =
                DefaultProjectDependencyAnalyzer.buildUsedArtifacts(classToArtifactMap, dependencyClasses);

        assertEquals(1, result.size());
        assertEquals(2, result.get(artifact1).size());
    }

    @Test
    void testBuildUsedArtifactsWithJDKExcluded() {
        Artifact artifact1 = aTestArtifact("xml-apis", "xml-apis");
        Map<String, Artifact> classToArtifactMap = Collections.singletonMap("class1", artifact1);
        Set<DependencyUsage> dependencyClasses = Collections.singleton(new DependencyUsage("class1", "main"));

        Map<Artifact, Set<DependencyUsage>> result =
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
        Artifact direct = aTestArtifact("direct");
        Artifact transitive = aTestArtifact("transitive");
        MavenProject project = new MavenProject();
        project.setDependencies(Collections.singletonList(toDependency(direct)));
        project.setArtifacts(new LinkedHashSet<>(Arrays.asList(direct, transitive)));

        Set<Artifact> declaredArtifacts =
                DefaultProjectDependencyAnalyzer.buildDeclaredArtifacts(project, artifactHandlerManager);
        assertIterableEquals(Collections.singletonList(direct), declaredArtifacts);
        assertSame(direct, declaredArtifacts.iterator().next());
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

        assertIterableEquals(
                Collections.singletonList(testJar),
                DefaultProjectDependencyAnalyzer.buildDeclaredArtifacts(project, artifactHandlerManager));
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

        Set<Artifact> declaredArtifacts =
                DefaultProjectDependencyAnalyzer.buildDeclaredArtifacts(project, artifactHandlerManager);
        assertEquals(1, declaredArtifacts.size());
        Artifact artifact = declaredArtifacts.iterator().next();
        assertEquals("axis", artifact.getGroupId());
        assertEquals("axis-ant", artifact.getArtifactId());
        assertEquals("1.4", artifact.getVersion());
        assertNotSame(relocated, artifact);
    }

    @Test
    void testRetainsTestOnlyCompileDependencyWithoutRepositorySession() {
        Artifact candidate = aTestArtifact("candidate");

        assertIterableEquals(
                Collections.singletonList(candidate),
                analyzer.getTestArtifactsWithNonTestScope(new MavenProject(), Collections.singleton(candidate)));
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

        assertIterableEquals(
                Collections.singletonList(candidate),
                analyzer.getTestArtifactsWithNonTestScope(project, Collections.singleton(candidate)));
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
        assertEquals(1, remoteRepositories.size());

        when(repositorySystem.collectDependencies(any(RepositorySystemSession.class), any(CollectRequest.class)))
                .thenReturn(dependencyGraph("compile-candidate", "2.0"), dependencyGraph("runtime-candidate", "2.0"));

        Set<Artifact> candidates = new LinkedHashSet<>(Arrays.asList(compileCandidate, runtimeCandidate));
        assertTrue(
                analyzer.getTestArtifactsWithNonTestScope(project, candidates).isEmpty());

        ArgumentCaptor<CollectRequest> requestCaptor = ArgumentCaptor.forClass(CollectRequest.class);
        verify(repositorySystem, times(2))
                .collectDependencies(eq(mavenSession.getRepositorySession()), requestCaptor.capture());
        List<CollectRequest> requests = requestCaptor.getAllValues();
        assertEquals(new HashSet<>(Arrays.asList("compile", "provided", "system")), artifactIds(requests.get(0)));
        assertEquals(new HashSet<>(Arrays.asList("compile", "runtime")), artifactIds(requests.get(1)));
        for (CollectRequest request : requests) {
            assertEquals("project", request.getRequestContext());
            assertEquals(remoteRepositories, request.getRepositories());
            assertEquals(
                    Collections.singletonList("managed"),
                    request.getManagedDependencies().stream()
                            .map(d -> d.getArtifact().getArtifactId())
                            .collect(Collectors.toList()));
        }
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

        assertIterableEquals(
                Collections.singletonList(candidate),
                analyzer.getTestArtifactsWithNonTestScope(project, Collections.singleton(candidate)));
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

    @Test
    void buildUsedDependencyClassesShouldLeaveOutProjectAndPlatformClasses() {
        Artifact dependency = aTestArtifact("dependency");
        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(dependency, new HashSet<>(Arrays.asList("com.dep.Lib", "javax.annotation.Nonnull")));

        Set<DependencyUsage> usages = new LinkedHashSet<>(Arrays.asList(
                new DependencyUsage("com.dep.Lib", "my.App"),
                new DependencyUsage("my.Helper", "my.App"),
                new DependencyUsage("java.lang.Object", "my.Helper"),
                new DependencyUsage("javax.annotation.Nonnull", "my.App"),
                new DependencyUsage("my.Versioned", "my.App"),
                new DependencyUsage("java.lang.Object", "META-INF.versions.11.my.Versioned"),
                new DependencyUsage("my.Excluded", "my.App"),
                new DependencyUsage("com.missing.Gone", "my.App")));

        Set<String> used = DefaultProjectDependencyAnalyzer.buildUsedDependencyClasses(
                usages,
                artifactClassMap,
                className -> className.startsWith("java.") || className.equals("my.Excluded"));

        assertIterableEquals(Arrays.asList("com.dep.Lib", "javax.annotation.Nonnull", "com.missing.Gone"), used);
    }

    @Test
    void buildUsedDependencyClassesShouldKeepIgnoredClassesThatADependencyProvides() {
        Artifact dependency = aTestArtifact("dependency");
        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(dependency, new HashSet<>(Arrays.asList("java.lang.Object", "my.Helper", "my.Excluded")));

        Set<DependencyUsage> usages = new LinkedHashSet<>(Arrays.asList(
                new DependencyUsage("java.lang.Object", "my.Helper"),
                new DependencyUsage("my.Helper", "my.App"),
                new DependencyUsage("my.Excluded", "my.App"),
                new DependencyUsage("java.lang.String", "my.App")));

        Set<String> used = DefaultProjectDependencyAnalyzer.buildUsedDependencyClasses(
                usages,
                artifactClassMap,
                className -> className.startsWith("java.") || className.equals("my.Excluded"));

        // a JDK, a project and an excluded class, each also found in the dependency; java.lang.String is not
        assertIterableEquals(Arrays.asList("java.lang.Object", "my.Helper", "my.Excluded"), used);
    }

    @Test
    void isPlatformClassShouldRecognizeJdkClassesOnly() {
        assertTrue(DefaultProjectDependencyAnalyzer.isPlatformClass("java.lang.Object"));
        assertTrue(DefaultProjectDependencyAnalyzer.isPlatformClass("java.util.Map$Entry"));
        if (!System.getProperty("java.specification.version").startsWith("1.")) {
            // jdk.compiler is defined to the application class loader, not the platform loader
            assertTrue(DefaultProjectDependencyAnalyzer.isPlatformClass("com.sun.source.tree.Tree"));
        }
        assertFalse(DefaultProjectDependencyAnalyzer.isPlatformClass(DependencyUsage.class.getName()));
        assertFalse(DefaultProjectDependencyAnalyzer.isPlatformClass("com.missing.Gone"));
    }

    @Test
    void buildCandidateArtifactsByUsedClassShouldReturnAllProviders() {
        Artifact first = aTestArtifact("first");
        Artifact second = aTestArtifact("second");

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(first, Collections.singleton("com.example.Cls"));
        artifactClassMap.put(second, Collections.singleton("com.example.Cls"));

        Set<String> used = Collections.singleton("com.example.Cls");
        Map<String, Set<Artifact>> candidates =
                DefaultProjectDependencyAnalyzer.buildCandidateArtifactsByUsedClass(artifactClassMap, used);

        assertIterableEquals(Arrays.asList(first, second), candidates.get("com.example.Cls"));
    }

    @Test
    void buildSelectedArtifactByUsedClassShouldKeepSelectedProvider() {
        Artifact first = aTestArtifact("first");
        Artifact second = aTestArtifact("second");

        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
        artifactClassMap.put(first, Collections.singleton("com.example.Cls"));
        artifactClassMap.put(second, Collections.singleton("com.example.Cls"));

        Set<String> used = Collections.singleton("com.example.Cls");
        Map<String, Artifact> classToArtifactMap =
                DefaultProjectDependencyAnalyzer.buildClassToArtifactMap(artifactClassMap);
        Map<String, Artifact> selected =
                DefaultProjectDependencyAnalyzer.buildSelectedArtifactByUsedClass(classToArtifactMap, used);

        assertEquals(first, selected.get("com.example.Cls"));
    }
}
