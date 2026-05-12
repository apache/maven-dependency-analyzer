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
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests <code>DefaultProjectDependencyAnalyzer</code>.
 *
 * @see DefaultProjectDependencyAnalyzer
 */
class DefaultProjectDependencyAnalyzerTest {

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

    private Artifact aTestArtifact(String artifactId) {
        return aTestArtifact("groupId", artifactId);
    }

    private Artifact aTestArtifact(String groupId, String artifactId) {
        return new DefaultArtifact(
                groupId, artifactId, VersionRange.createFromVersion("1.0"), "compile", "jar", "", null);
    }
}
