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

import org.apache.maven.api.Dependency;
import org.apache.maven.api.DependencyScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests <code>ProjectDependencyAnalysis</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see ProjectDependencyAnalysis
 */
class ProjectDependencyAnalysisTest {
    @Test
    void testConstructor() {
        Set<Dependency> usedDeclaredArtifacts = new HashSet<>();
        Set<Dependency> usedUndeclaredArtifacts = new HashSet<>();
        Set<Dependency> unusedDeclaredArtifacts = new HashSet<>();
        Set<Dependency> testArtifactsWithNonTestScope = new HashSet<>();

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                usedDeclaredArtifacts, usedUndeclaredArtifacts, unusedDeclaredArtifacts, testArtifactsWithNonTestScope);

        assertEquals(usedDeclaredArtifacts, analysis.getUsedDeclaredArtifacts());
        assertEquals(usedUndeclaredArtifacts, analysis.getUsedUndeclaredArtifacts());
        assertEquals(unusedDeclaredArtifacts, analysis.getUnusedDeclaredArtifacts());
    }

    @Test
    void ignoreNonCompileShouldFilterOnlyUnusedDeclare() {
        Dependency artifactCompile = aTestArtifact("test1", DependencyScope.COMPILE);
        Dependency artifactProvided = aTestArtifact("test2", DependencyScope.PROVIDED);
        Dependency artifactTest = aTestArtifact("test3", DependencyScope.TEST);

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                asSet(artifactCompile, artifactProvided, artifactTest),
                asSet(artifactCompile, artifactProvided, artifactTest),
                asSet(artifactCompile, artifactProvided, artifactTest),
                asSet(artifactCompile, artifactProvided, artifactTest));

        ProjectDependencyAnalysis compileOnlyAnalysis = analysis.ignoreNonCompile();

        assertEquals(3, compileOnlyAnalysis.getUsedDeclaredArtifacts().size());
        assertEquals(3, compileOnlyAnalysis.getUsedUndeclaredArtifacts().size());

        assertEquals(1, compileOnlyAnalysis.getUnusedDeclaredArtifacts().size());
        for (Dependency a : compileOnlyAnalysis.getUnusedDeclaredArtifacts()) {
            assertEquals(DependencyScope.COMPILE, a.getScope());
        }

        assertEquals(3, compileOnlyAnalysis.getTestArtifactsWithNonTestScope().size());
    }

    @Test
    void shouldExposeUsedClassesAndCandidateArtifacts() {
        Dependency declared = aTestArtifact("declared", DependencyScope.COMPILE);
        Dependency other = aTestArtifact("other", DependencyScope.COMPILE);
        Set<String> used = new HashSet<>(Arrays.asList("a.B", "c.D"));
        Map<String, Set<Dependency>> candidates = new java.util.LinkedHashMap<>();
        candidates.put("a.B", new LinkedHashSet<>(Arrays.asList(declared, other)));
        candidates.put("c.D", new LinkedHashSet<>(Collections.singleton(declared)));
        Map<String, Dependency> selected = new java.util.LinkedHashMap<>();
        selected.put("a.B", declared);
        selected.put("c.D", declared);

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptySet(),
                used,
                candidates,
                selected);

        assertEquals(new HashSet<>(Arrays.asList("a.B", "c.D")), new HashSet<>(analysis.getUsedDependencyClasses()));
        assertIterableEquals(
                Arrays.asList(declared, other),
                analysis.getCandidateArtifactsByUsedClass().get("a.B"));
        assertEquals(declared, analysis.getSelectedArtifactByUsedClass().get("a.B"));
        assertIterableEquals(Collections.singletonList("a.B"), analysis.getAmbiguousUsedDependencyClasses());
        assertTrue(analysis.getUnmappedUsedDependencyClasses().isEmpty());
    }

    @Test
    void shouldReportUnmappedUsedDependencyClasses() {
        Set<String> used = new HashSet<>(Collections.singletonList("a.B"));
        Map<String, Set<Dependency>> candidates = new java.util.LinkedHashMap<>();
        candidates.put("a.B", Collections.emptySet());

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptySet(),
                used,
                candidates,
                Collections.emptyMap());

        assertIterableEquals(Collections.singletonList("a.B"), analysis.getUnmappedUsedDependencyClasses());
        assertFalse(analysis.getSelectedArtifactByUsedClass().containsKey("a.B"));
    }

    @Test
    void forcingUsageKeepsTheUsagesOfOtherUsedDeclaredDependencies() throws Exception {
        Dependency first = aTestArtifact("first", DependencyScope.COMPILE);
        Dependency second = aTestArtifact("second", DependencyScope.COMPILE);
        Dependency forcedA = aTestArtifact("forced-a", DependencyScope.COMPILE);
        Dependency forcedB = aTestArtifact("forced-b", DependencyScope.COMPILE);
        DependencyUsage firstUsage = new DependencyUsage("a.First", "my.App");
        DependencyUsage secondUsage = new DependencyUsage("b.Second", "my.Other");
        Map<Dependency, Set<DependencyUsage>> usedDeclared = new LinkedHashMap<>();
        usedDeclared.put(first, Collections.singleton(firstUsage));
        usedDeclared.put(second, Collections.singleton(secondUsage));
        Set<String> usedClasses = new LinkedHashSet<>(Arrays.asList("a.First", "b.Second"));
        Map<String, Set<Dependency>> candidates = new LinkedHashMap<>();
        candidates.put("a.First", Collections.singleton(first));
        candidates.put("b.Second", Collections.singleton(second));
        Map<String, Dependency> selected = new LinkedHashMap<>();
        selected.put("a.First", first);
        selected.put("b.Second", second);

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                        usedDeclared,
                        Collections.emptyMap(),
                        new LinkedHashSet<>(Arrays.asList(forcedA, forcedB)),
                        Collections.emptySet(),
                        usedClasses,
                        candidates,
                        selected)
                .forceDeclaredDependenciesUsage(new String[] {"groupId:forced-a", "groupId:forced-b"});

        Map<Dependency, Set<DependencyUsage>> usages = analysis.getUsedDeclaredArtifactsWithUsages();
        assertEquals(Collections.singleton(firstUsage), usages.get(first));
        assertEquals(Collections.singleton(secondUsage), usages.get(second));
        assertEquals(Collections.emptySet(), usages.get(forcedA));
        assertEquals(Collections.emptySet(), usages.get(forcedB));
        // the detected dependencies come first, then the forced ones in the order they were declared
        assertIterableEquals(Arrays.asList(first, second, forcedA, forcedB), usages.keySet());
        assertTrue(analysis.getUnusedDeclaredArtifacts().isEmpty());
        assertEquals(usedClasses, analysis.getUsedDependencyClasses());
        assertEquals(candidates, analysis.getCandidateArtifactsByUsedClass());
        assertEquals(selected, analysis.getSelectedArtifactByUsedClass());
    }

    @Test
    void forcingAnAlreadyUsedOrUndeclaredDependencyFails() {
        Dependency used = aTestArtifact("used", DependencyScope.COMPILE);
        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                Collections.singletonMap(used, Collections.singleton(new DependencyUsage("a.B", "my.App"))),
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptySet());

        ProjectDependencyAnalyzerException exception = assertThrows(
                ProjectDependencyAnalyzerException.class,
                () -> analysis.forceDeclaredDependenciesUsage(new String[] {"groupId:used", "groupId:missing"}));
        assertEquals(
                "Trying to force use of dependencies which are not declared: [groupId:missing]"
                        + " and declared but already detected as used: [groupId:used]",
                exception.getMessage());
    }

    @Test
    void classesAndUsagesAreSorted() {
        Dependency undeclared = aTestArtifact("undeclared", DependencyScope.COMPILE);
        // HashSet iterates these as c.Zeta, a.Alpha, b.Beta
        Set<DependencyUsage> usages = new HashSet<>(Arrays.asList(
                new DependencyUsage("c.Zeta", "my.App"),
                new DependencyUsage("a.Alpha", "my.Zed"),
                new DependencyUsage("a.Alpha", "my.App"),
                new DependencyUsage("b.Beta", "my.App")));
        Set<String> used = new HashSet<>(Arrays.asList("c.Zeta", "a.Alpha", "b.Beta"));
        Map<String, Set<Dependency>> candidates = new java.util.HashMap<>();
        for (String className : used) {
            candidates.put(className, Collections.emptySet());
        }

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                Collections.emptyMap(),
                Collections.singletonMap(undeclared, usages),
                Collections.emptySet(),
                Collections.emptySet(),
                used,
                candidates,
                Collections.emptyMap());

        List<String> sorted = Arrays.asList("a.Alpha", "b.Beta", "c.Zeta");
        assertIterableEquals(sorted, analysis.getUsedDependencyClasses());
        assertIterableEquals(sorted, analysis.getUnmappedUsedDependencyClasses());
        assertIterableEquals(sorted, analysis.getCandidateArtifactsByUsedClass().keySet());
        assertIterableEquals(
                sorted, analysis.getUsedUndeclaredArtifactsWithClasses().get(undeclared));
        assertIterableEquals(
                Arrays.asList(
                        new DependencyUsage("a.Alpha", "my.App"),
                        new DependencyUsage("a.Alpha", "my.Zed"),
                        new DependencyUsage("b.Beta", "my.App"),
                        new DependencyUsage("c.Zeta", "my.App")),
                analysis.getUsedUndeclaredArtifactsWithUsages().get(undeclared));
        // sorted copies still answer null lookups like the hash-based ones did
        assertFalse(analysis.getUsedDependencyClasses().contains(null));
        assertFalse(analysis.getCandidateArtifactsByUsedClass().containsKey(null));
    }

    @Test
    void forcingUsageKeepsTheOrderOfTheRemainingUnusedDependencies() throws Exception {
        List<Dependency> unused = new ArrayList<>();
        for (char c = 'z'; c >= 'a'; c--) {
            unused.add(aTestArtifact(String.valueOf(c), DependencyScope.COMPILE));
        }

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        new LinkedHashSet<>(unused),
                        Collections.emptySet())
                .forceDeclaredDependenciesUsage(new String[] {"groupId:m"});

        unused.removeIf(artifact -> "m".equals(artifact.getArtifactId()));
        assertIterableEquals(unused, analysis.getUnusedDeclaredArtifacts());
    }

    @Test
    void nullClassNamesAreAcceptedAndSortedFirst() {
        Dependency undeclared = aTestArtifact("undeclared", DependencyScope.COMPILE);
        Set<String> used = new HashSet<>(Arrays.asList("b.Beta", null, "a.Alpha"));
        Map<String, Set<Dependency>> candidates = new java.util.HashMap<>();
        candidates.put("b.Beta", Collections.singleton(undeclared));
        candidates.put(null, Collections.singleton(undeclared));
        Map<String, Dependency> selected = new java.util.HashMap<>();
        selected.put("b.Beta", undeclared);
        selected.put(null, undeclared);

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptySet(),
                used,
                candidates,
                selected);

        assertIterableEquals(Arrays.asList(null, "a.Alpha", "b.Beta"), analysis.getUsedDependencyClasses());
        assertIterableEquals(
                Arrays.asList(null, "b.Beta"),
                analysis.getCandidateArtifactsByUsedClass().keySet());
        assertIterableEquals(
                Arrays.asList(null, "b.Beta"),
                analysis.getSelectedArtifactByUsedClass().keySet());
    }

    @Test
    void ignoreNonCompileKeepsTheOrderOfTheUnusedDependencies() {
        List<Dependency> unused = new ArrayList<>();
        for (char c = 'z'; c >= 'a'; c--) {
            unused.add(aTestArtifact(String.valueOf(c), DependencyScope.COMPILE));
        }

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        new LinkedHashSet<>(unused),
                        Collections.emptySet())
                .ignoreNonCompile();

        assertIterableEquals(unused, analysis.getUnusedDeclaredArtifacts());
    }

    private <T> Set<T> asSet(T... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    private Dependency aTestArtifact(String artifactId, DependencyScope scope) {
        Dependency dependency = mock(Dependency.class);
        when(dependency.getGroupId()).thenReturn("groupId");
        when(dependency.getArtifactId()).thenReturn(artifactId);
        when(dependency.getScope()).thenReturn(scope);
        return dependency;
    }
}
