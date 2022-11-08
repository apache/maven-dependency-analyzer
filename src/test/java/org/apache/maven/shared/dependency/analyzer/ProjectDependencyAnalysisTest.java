package org.apache.maven.shared.dependency.analyzer;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.Scope;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests <code>ProjectDependencyAnalysis</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see ProjectDependencyAnalysis
 */
public class ProjectDependencyAnalysisTest
{
    @Test
    public void testConstructor()
    {
        Set<Dependency> usedDeclaredArtifacts = new HashSet<>();
        Set<Dependency> usedUndeclaredArtifacts = new HashSet<>();
        Set<Dependency> unusedDeclaredArtifacts = new HashSet<>();
        Set<Dependency> testArtifactsWithNonTestScope = new HashSet<>();

        ProjectDependencyAnalysis analysis =
            new ProjectDependencyAnalysis( usedDeclaredArtifacts, usedUndeclaredArtifacts, unusedDeclaredArtifacts,
                testArtifactsWithNonTestScope );

        assertThat( analysis.getUsedDeclaredArtifacts() ).isEqualTo( usedDeclaredArtifacts );
        assertThat( analysis.getUsedUndeclaredArtifacts() ).isEqualTo( usedUndeclaredArtifacts );
        assertThat( analysis.getUnusedDeclaredArtifacts() ).isEqualTo( unusedDeclaredArtifacts );
    }

    @Test
    public void ignoreNonCompileShouldFilterOnlyUnusedDeclare()
    {
        Dependency artifactCompile = aTestArtifact( "test1", Scope.COMPILE );
        Dependency artifactProvided = aTestArtifact( "test2", Scope.PROVIDED );
        Dependency artifactTest = aTestArtifact( "test3", Scope.TEST );

        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
            asSet( artifactCompile, artifactProvided, artifactTest ),
            asSet( artifactCompile, artifactProvided, artifactTest ),
            asSet( artifactCompile, artifactProvided, artifactTest ),
            asSet( artifactCompile, artifactProvided, artifactTest ) );

        ProjectDependencyAnalysis compileOnlyAnalysis = analysis.ignoreNonCompile();

        assertThat( compileOnlyAnalysis.getUsedDeclaredArtifacts() ).hasSize( 3 );
        assertThat( compileOnlyAnalysis.getUsedUndeclaredArtifacts() ).hasSize( 3 );

        assertThat( compileOnlyAnalysis.getUnusedDeclaredArtifacts() )
            .hasSize( 1 )
            .allSatisfy( a -> assertThat( a.getScope() ).isEqualTo( Scope.COMPILE ) );

        assertThat( compileOnlyAnalysis.getTestArtifactsWithNonTestScope() )
            .hasSize( 3 );
    }

    private <T> Set<T> asSet( T... items )
    {
        return new HashSet<>( Arrays.asList( items ) );
    }

    private Dependency aTestArtifact( String artifactId, Scope scope )
    {
        Dependency dependency = Mockito.mock( Dependency.class );
        when( dependency.getGroupId() ).thenReturn( "groupId" );
        when( dependency.getArtifactId() ).thenReturn( artifactId );
        when( dependency.getScope() ).thenReturn( scope );
        when( dependency.key() ).thenReturn( artifactId );
        return dependency;
    }
}
