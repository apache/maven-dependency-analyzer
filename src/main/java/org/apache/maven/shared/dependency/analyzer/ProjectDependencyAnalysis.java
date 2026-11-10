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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.DependencyScope;

/**
 * Project dependencies analysis result.
 * <p>
 * Class names, maps keyed by class name and dependency usages are returned sorted; artifacts keep the order in which
 * they were passed in.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public class ProjectDependencyAnalysis {
    /** Orders class names, with {@code null} first as the hash-based collections accepted it. */
    private static final Comparator<String> CLASS_ORDER = Comparator.nullsFirst(Comparator.<String>naturalOrder());

    /** Orders usages by dependency class, then by the class that uses it. */
    private static final Comparator<DependencyUsage> USAGE_ORDER = Comparator.comparing(
                    DependencyUsage::getDependencyClass, CLASS_ORDER)
            .thenComparing(DependencyUsage::getUsedBy, CLASS_ORDER);

    // fields -----------------------------------------------------------------

    private final Map<Dependency, Set<DependencyUsage>> usedDeclaredArtifacts;

    private final Map<Dependency, Set<DependencyUsage>> usedUndeclaredArtifacts;

    private final Set<Dependency> unusedDeclaredArtifacts;

    private final Set<Dependency> testArtifactsWithNonTestScope;

    private final Set<String> usedDependencyClasses;

    private final Map<String, Set<Dependency>> candidateArtifactsByUsedClass;

    private final Map<String, Dependency> selectedArtifactByUsedClass;

    /**
     * <p>Constructor for ProjectDependencyAnalysis.</p>
     */
    public ProjectDependencyAnalysis() {
        this(null, (Map<Dependency, Set<DependencyUsage>>) null, null, null);
    }

    /**
     * <p>Constructor for ProjectDependencyAnalysis to maintain compatibility with old API</p>
     *
     * @param usedDeclaredArtifacts artifacts both used and declared
     * @param usedUndeclaredArtifacts artifacts used but not declared
     * @param unusedDeclaredArtifacts artifacts declared but not used
     */
    public ProjectDependencyAnalysis(
            Set<Dependency> usedDeclaredArtifacts,
            Set<Dependency> usedUndeclaredArtifacts,
            Set<Dependency> unusedDeclaredArtifacts) {
        this(usedDeclaredArtifacts, usedUndeclaredArtifacts, unusedDeclaredArtifacts, Collections.emptySet());
    }

    /**
     * Constructor for ProjectDependencyAnalysis.
     *
     * @param usedDeclaredArtifacts artifacts both used and declared
     * @param usedUndeclaredArtifacts artifacts used but not declared
     * @param unusedDeclaredArtifacts artifacts declared but not used
     * @param testArtifactsWithNonTestScope artifacts only used in tests but not declared with test scope
     */
    public ProjectDependencyAnalysis(
            Set<Dependency> usedDeclaredArtifacts,
            Set<Dependency> usedUndeclaredArtifacts,
            Set<Dependency> unusedDeclaredArtifacts,
            Set<Dependency> testArtifactsWithNonTestScope) {
        this(
                mapWithKeys(usedDeclaredArtifacts),
                mapWithKeys(usedUndeclaredArtifacts),
                unusedDeclaredArtifacts,
                testArtifactsWithNonTestScope);
    }

    /**
     * Creates an analysis result with usage details.
     *
     * @param usedDeclaredArtifacts artifacts both used and declared
     * @param usedUndeclaredArtifacts artifacts used but not declared
     * @param unusedDeclaredArtifacts artifacts declared but not used
     * @param testArtifactsWithNonTestScope artifacts only used in tests but not declared with test scope
     */
    public ProjectDependencyAnalysis(
            Map<Dependency, Set<DependencyUsage>> usedDeclaredArtifacts,
            Map<Dependency, Set<DependencyUsage>> usedUndeclaredArtifacts,
            Set<Dependency> unusedDeclaredArtifacts,
            Set<Dependency> testArtifactsWithNonTestScope) {
        this(
                usedDeclaredArtifacts,
                usedUndeclaredArtifacts,
                unusedDeclaredArtifacts,
                testArtifactsWithNonTestScope,
                null,
                null,
                null);
    }

    /**
     * Creates an analysis result with usage details, raw used classes and candidate artifacts.
     *
     * @param usedDeclaredArtifacts artifacts both used and declared
     * @param usedUndeclaredArtifacts artifacts used but not declared
     * @param unusedDeclaredArtifacts artifacts declared but not used
     * @param testArtifactsWithNonTestScope artifacts only used in tests but not declared with test scope
     * @param usedDependencyClasses dependency class names referenced by the analyzed bytecode
     * @param candidateArtifactsByUsedClass candidate artifacts that provide each used dependency class
     * @param selectedArtifactByUsedClass artifact selected for each used dependency class
     * @since 1.18.0
     */
    public ProjectDependencyAnalysis(
            Map<Dependency, Set<DependencyUsage>> usedDeclaredArtifacts,
            Map<Dependency, Set<DependencyUsage>> usedUndeclaredArtifacts,
            Set<Dependency> unusedDeclaredArtifacts,
            Set<Dependency> testArtifactsWithNonTestScope,
            Set<String> usedDependencyClasses,
            Map<String, Set<Dependency>> candidateArtifactsByUsedClass,
            Map<String, Dependency> selectedArtifactByUsedClass) {
        this.usedDeclaredArtifacts = safeCopy(usedDeclaredArtifacts);
        this.usedUndeclaredArtifacts = safeCopy(usedUndeclaredArtifacts);
        this.unusedDeclaredArtifacts = safeCopy(unusedDeclaredArtifacts);
        this.testArtifactsWithNonTestScope = safeCopy(testArtifactsWithNonTestScope);
        this.usedDependencyClasses = safeCopyStrings(usedDependencyClasses);
        this.candidateArtifactsByUsedClass = safeCopyStringDependencySetMap(candidateArtifactsByUsedClass);
        this.selectedArtifactByUsedClass = safeCopyStringDependencyMap(selectedArtifactByUsedClass);
    }

    /**
     * Returns artifacts both used and declared.
     *
     * @return artifacts both used and declared
     */
    public Set<Dependency> getUsedDeclaredArtifacts() {
        return safeCopy(usedDeclaredArtifacts.keySet());
    }

    /**
     * Returns artifacts both used and declared.
     *
     * @return artifacts both used and declared
     */
    public Map<Dependency, Set<DependencyUsage>> getUsedDeclaredArtifactsWithUsages() {
        return safeCopy(usedDeclaredArtifacts);
    }

    /**
     * Returns artifacts used but not declared.
     *
     * @return artifacts used but not declared
     */
    public Set<Dependency> getUsedUndeclaredArtifacts() {
        return safeCopy(usedUndeclaredArtifacts.keySet());
    }

    /**
     * Returns artifacts used but not declared.
     *
     * @return artifacts used but not declared
     */
    public Map<Dependency, Set<String>> getUsedUndeclaredArtifactsWithClasses() {
        Map<Dependency, Set<String>> usedUndeclaredArtifactsWithClasses = new LinkedHashMap<>();

        for (Map.Entry<Dependency, Set<DependencyUsage>> entry : usedUndeclaredArtifacts.entrySet()) {
            usedUndeclaredArtifactsWithClasses.put(
                    entry.getKey(),
                    entry.getValue().stream()
                            .map(DependencyUsage::getDependencyClass)
                            .sorted(CLASS_ORDER)
                            .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        return usedUndeclaredArtifactsWithClasses;
    }

    /**
     * Returns artifacts used but not declared with their usages.
     *
     * @return artifacts used but not declared with their usages
     */
    public Map<Dependency, Set<DependencyUsage>> getUsedUndeclaredArtifactsWithUsages() {
        return safeCopy(usedUndeclaredArtifacts);
    }

    /**
     * Returns artifacts declared but not used.
     *
     * @return artifacts declared but not used
     */
    public Set<Dependency> getUnusedDeclaredArtifacts() {
        return safeCopy(unusedDeclaredArtifacts);
    }

    /**
     * Returns artifacts only used in tests but not declared with test scope.
     *
     * @return  artifacts only used in tests but not declared with test scope
     */
    public Set<Dependency> getTestArtifactsWithNonTestScope() {
        return safeCopy(testArtifactsWithNonTestScope);
    }

    /**
     * Returns the class names referenced by the analyzed bytecode that can come from a dependency. The project's own
     * classes, the classes of the JDK running the analysis and excluded classes are left out unless a dependency
     * provides them too.
     *
     * @return dependency class names referenced by the analyzed bytecode
     * @since 1.18.0
     */
    public Set<String> getUsedDependencyClasses() {
        return safeCopyStrings(usedDependencyClasses);
    }

    /**
     * Returns the artifacts that provide each referenced dependency class.
     *
     * @return candidate artifacts per referenced dependency class
     * @since 1.18.0
     */
    public Map<String, Set<Dependency>> getCandidateArtifactsByUsedClass() {
        return safeCopyStringDependencySetMap(candidateArtifactsByUsedClass);
    }

    /**
     * Returns the artifact selected for each referenced dependency class.
     *
     * @return selected artifact per referenced dependency class
     * @since 1.18.0
     */
    public Map<String, Dependency> getSelectedArtifactByUsedClass() {
        return safeCopyStringDependencyMap(selectedArtifactByUsedClass);
    }

    /**
     * Returns referenced dependency classes provided by more than one artifact.
     *
     * @return ambiguous referenced dependency classes
     * @since 1.18.0
     */
    public Set<String> getAmbiguousUsedDependencyClasses() {
        Set<String> ambiguous = new LinkedHashSet<>();
        for (Map.Entry<String, Set<Dependency>> entry : candidateArtifactsByUsedClass.entrySet()) {
            if (entry.getValue().size() > 1) {
                ambiguous.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(ambiguous);
    }

    /**
     * Returns referenced dependency classes that no dependency artifact provides, for example classes of a
     * dependency that is missing from the build. The project's own classes, the classes of the JDK running the
     * analysis and excluded classes are left out.
     *
     * @return unmapped referenced dependency classes
     * @since 1.18.0
     */
    public Set<String> getUnmappedUsedDependencyClasses() {
        Set<String> unmapped = new LinkedHashSet<>();
        for (String className : usedDependencyClasses) {
            if (candidateArtifactsByUsedClass
                    .getOrDefault(className, Collections.emptySet())
                    .isEmpty()) {
                unmapped.add(className);
            }
        }
        return Collections.unmodifiableSet(unmapped);
    }

    /**
     * Filter non-compile scoped artifacts from unused declared.
     *
     * @return updated project dependency analysis
     * @since 1.3
     */
    public ProjectDependencyAnalysis ignoreNonCompile() {
        Set<Dependency> filteredUnusedDeclared = new LinkedHashSet<>(unusedDeclaredArtifacts);
        filteredUnusedDeclared.removeIf(artifact -> !DependencyScope.COMPILE.equals(artifact.getScope()));

        return new ProjectDependencyAnalysis(
                usedDeclaredArtifacts,
                usedUndeclaredArtifacts,
                filteredUnusedDeclared,
                testArtifactsWithNonTestScope,
                usedDependencyClasses,
                candidateArtifactsByUsedClass,
                selectedArtifactByUsedClass);
    }

    /**
     * Force use status of some declared dependencies, to manually fix consequences of bytecode-level analysis which
     * happens to not detect some effective use (constants, annotation with source-retention, javadoc).
     * <p>
     * The dependencies already detected as used keep their usages; a newly forced dependency has an empty usage set.
     * The used classes and their candidate and selected artifacts are kept unchanged.
     *
     * @param forceUsedDependencies dependencies to move from "unused-declared" to "used-declared", with
     *                              <code>groupId:artifactId</code> format
     * @return updated project dependency analysis
     * @throws ProjectDependencyAnalyzerException if dependencies forced were either not declared or already detected as
     *                                            used
     * @since 1.3
     */
    public ProjectDependencyAnalysis forceDeclaredDependenciesUsage(String[] forceUsedDependencies)
            throws ProjectDependencyAnalyzerException {
        Set<String> forced = new HashSet<>(Arrays.asList(forceUsedDependencies));

        Set<Dependency> forcedUnusedDeclared = new LinkedHashSet<>(unusedDeclaredArtifacts);
        // keep the usages of the dependencies detected as used; a forced dependency has none
        Map<Dependency, Set<DependencyUsage>> forcedUsedDeclared = new LinkedHashMap<>(usedDeclaredArtifacts);

        Iterator<Dependency> iter = forcedUnusedDeclared.iterator();
        while (iter.hasNext()) {
            Dependency artifact = iter.next();

            if (forced.remove(artifact.getGroupId() + ':' + artifact.getArtifactId())) {
                // ok, change artifact status from unused-declared to used-declared
                iter.remove();
                forcedUsedDeclared.putIfAbsent(artifact, Collections.emptySet());
            }
        }

        if (!forced.isEmpty()) {
            // trying to force dependencies as used-declared which were not declared or already detected as used
            Set<String> used = new HashSet<>();
            for (Dependency artifact : usedDeclaredArtifacts.keySet()) {
                String id = artifact.getGroupId() + ':' + artifact.getArtifactId();
                if (forced.remove(id)) {
                    used.add(id);
                }
            }

            StringBuilder builder = new StringBuilder();
            if (!forced.isEmpty()) {
                builder.append("not declared: ").append(forced);
            }
            if (!used.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(" and ");
                }
                builder.append("declared but already detected as used: ").append(used);
            }
            throw new ProjectDependencyAnalyzerException("Trying to force use of dependencies which are " + builder);
        }

        return new ProjectDependencyAnalysis(
                forcedUsedDeclared,
                usedUndeclaredArtifacts,
                forcedUnusedDeclared,
                testArtifactsWithNonTestScope,
                usedDependencyClasses,
                candidateArtifactsByUsedClass,
                selectedArtifactByUsedClass);
    }

    /**
     * <p>hashCode.</p>
     *
     * @return an int
     */
    @Override
    public int hashCode() {
        int hashCode = getUsedDeclaredArtifacts().hashCode();
        hashCode = (hashCode * 37) + getUsedUndeclaredArtifacts().hashCode();
        hashCode = (hashCode * 37) + getUnusedDeclaredArtifacts().hashCode();
        hashCode = (hashCode * 37) + getTestArtifactsWithNonTestScope().hashCode();

        return hashCode;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object object) {
        if (object instanceof ProjectDependencyAnalysis) {
            ProjectDependencyAnalysis analysis = (ProjectDependencyAnalysis) object;

            return getUsedDeclaredArtifacts().equals(analysis.getUsedDeclaredArtifacts())
                    && getUsedUndeclaredArtifacts().equals(analysis.getUsedUndeclaredArtifacts())
                    && getUnusedDeclaredArtifacts().equals(analysis.getUnusedDeclaredArtifacts())
                    && getTestArtifactsWithNonTestScope().equals(analysis.getTestArtifactsWithNonTestScope());
        }

        return false;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();

        if (!getUsedDeclaredArtifacts().isEmpty()) {
            buffer.append("usedDeclaredArtifacts=").append(getUsedDeclaredArtifacts());
        }

        if (!getUsedUndeclaredArtifacts().isEmpty()) {
            if (buffer.length() > 0) {
                buffer.append(",");
            }

            buffer.append("usedUndeclaredArtifacts=").append(getUsedUndeclaredArtifacts());
        }

        if (!getUnusedDeclaredArtifacts().isEmpty()) {
            if (buffer.length() > 0) {
                buffer.append(",");
            }

            buffer.append("unusedDeclaredArtifacts=").append(getUnusedDeclaredArtifacts());
        }

        if (!getTestArtifactsWithNonTestScope().isEmpty()) {
            if (buffer.length() > 0) {
                buffer.append(",");
            }

            buffer.append("testArtifactsWithNonTestScope=").append(getTestArtifactsWithNonTestScope());
        }

        buffer.insert(0, "[");
        buffer.insert(0, getClass().getName());

        buffer.append("]");

        return buffer.toString();
    }

    // private methods --------------------------------------------------------

    private Set<Dependency> safeCopy(Set<Dependency> set) {
        return (set == null) ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(set));
    }

    private static Set<String> safeCopyStrings(Set<String> set) {
        if (set == null) {
            return Collections.emptySet();
        }

        Set<String> sorted = new TreeSet<>(CLASS_ORDER);
        sorted.addAll(set);
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    private static <V> Map<String, V> sortedByClass(Map<String, V> map) {
        Map<String, V> sorted = new TreeMap<>(CLASS_ORDER);
        sorted.putAll(map);
        return sorted;
    }

    private static Set<DependencyUsage> sortedUsages(Set<DependencyUsage> usages) {
        List<DependencyUsage> sorted = new ArrayList<>(usages);
        sorted.sort(USAGE_ORDER);
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    private static Map<Dependency, Set<DependencyUsage>> safeCopy(Map<Dependency, Set<DependencyUsage>> origMap) {
        if (origMap == null) {
            return Collections.emptyMap();
        }

        Map<Dependency, Set<DependencyUsage>> map = new LinkedHashMap<>();

        for (Map.Entry<Dependency, Set<DependencyUsage>> e : origMap.entrySet()) {
            map.put(e.getKey(), sortedUsages(e.getValue()));
        }

        return map;
    }

    private static Map<String, Set<Dependency>> safeCopyStringDependencySetMap(Map<String, Set<Dependency>> origMap) {
        if (origMap == null) {
            return Collections.emptyMap();
        }

        Map<String, Set<Dependency>> map = new LinkedHashMap<>();

        for (Map.Entry<String, Set<Dependency>> e : sortedByClass(origMap).entrySet()) {
            map.put(e.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(e.getValue())));
        }

        return map;
    }

    private static Map<String, Dependency> safeCopyStringDependencyMap(Map<String, Dependency> origMap) {
        if (origMap == null) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(sortedByClass(origMap)));
    }

    private static Map<Dependency, Set<DependencyUsage>> mapWithKeys(Set<Dependency> keys) {
        if (keys == null) {
            return Collections.emptyMap();
        }

        Map<Dependency, Set<DependencyUsage>> map = new LinkedHashMap<>();

        for (Dependency k : keys) {
            map.put(k, Collections.emptySet());
        }

        return map;
    }
}
