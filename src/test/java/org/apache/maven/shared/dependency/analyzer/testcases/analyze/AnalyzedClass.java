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
package org.apache.maven.shared.dependency.analyzer.testcases.analyze;

import org.apache.maven.shared.dependency.analyzer.testcases.prepare.Prepare;

/**
 * Class to be analyzed in unit test.
 * <p>
 * The handler method in {@link Prepare} takes an implicit Consumer&lt;ArtifactResolutionRequest&gt; argument. Analyze
 * this class to verify the implicit reference.
 */
public class AnalyzedClass {

    public void useFirst() {
        new Prepare().handler(this::doNothing);
    }

    private void doNothing(final Object object) {}
}
