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

import org.apache.maven.artifact.Artifact;

/**
 * Helpers for producing the keys used to identify artifacts in dependency analysis results.
 *
 * @since 1.18.0
 */
public final class ArtifactKeys {

    private ArtifactKeys() {
        // utility class
    }

    /**
     * Returns the group and artifact identifier of an artifact, separated by a colon. This is the
     * same key used to report forced dependency usage and to look up declared dependencies.
     *
     * @param artifact the artifact, must not be {@code null}
     * @return the group-and-artifact key
     */
    public static String groupArtifactId(Artifact artifact) {
        return artifact.getGroupId() + ':' + artifact.getArtifactId();
    }
}
