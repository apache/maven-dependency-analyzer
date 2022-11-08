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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Dependency;
import org.apache.maven.api.Scope;

/**
 * Project dependencies analysis result.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public class ProjectDependencyAnalysis
{
    // fields -----------------------------------------------------------------

    private final Set<Dependency> usedDeclaredArtifacts;

    private final Map<Dependency, Set<String>> usedUndeclaredArtifacts;

    private final Set<Dependency> unusedDeclaredArtifacts;

    private final Set<Dependency> testArtifactsWithNonTestScope;

    /**
     * <p>Constructor for ProjectDependencyAnalysis.</p>
     */
    public ProjectDependencyAnalysis()
    {
        this( null, (Map<Dependency, Set<String>>) null, null, null );
    }

    /**
     * <p>Constructor for ProjectDependencyAnalysis to maintain compatibility with old API</p>
     *
     * @param usedDeclaredArtifacts artifacts both used and declared
     * @param usedUndeclaredArtifacts artifacts used but not declared
     * @param unusedDeclaredArtifacts artifacts declared but not used
     */
    public ProjectDependencyAnalysis( Set<Dependency> usedDeclaredArtifacts,
                                      Set<Dependency> usedUndeclaredArtifacts,
                                      Set<Dependency> unusedDeclaredArtifacts )
    {
        this( usedDeclaredArtifacts, usedUndeclaredArtifacts,
                unusedDeclaredArtifacts, Collections.emptySet() );
    }

    /**
     * <p>Constructor for ProjectDependencyAnalysis.</p>
     *
     * @param usedDeclaredArtifacts artifacts both used and declared
     * @param usedUndeclaredArtifacts artifacts used but not declared
     * @param unusedDeclaredArtifacts artifacts declared but not used
     * @param testArtifactsWithNonTestScope artifacts only used in tests but not declared with test scope
     */
    public ProjectDependencyAnalysis( Set<Dependency> usedDeclaredArtifacts,
                                      Set<Dependency> usedUndeclaredArtifacts,
                                      Set<Dependency> unusedDeclaredArtifacts,
                                      Set<Dependency> testArtifactsWithNonTestScope )
    {
        this( usedDeclaredArtifacts,
                mapWithKeys( usedUndeclaredArtifacts ),
                unusedDeclaredArtifacts,
                testArtifactsWithNonTestScope );
    }

    public ProjectDependencyAnalysis( Set<Dependency> usedDeclaredArtifacts,
            Map<Dependency, Set<String>> usedUndeclaredArtifacts,
            Set<Dependency> unusedDeclaredArtifacts,
            Set<Dependency> testArtifactsWithNonTestScope )
    {
        this.usedDeclaredArtifacts = safeCopy( usedDeclaredArtifacts );
        this.usedUndeclaredArtifacts = safeCopy( usedUndeclaredArtifacts );
        this.unusedDeclaredArtifacts = safeCopy( unusedDeclaredArtifacts );
        this.testArtifactsWithNonTestScope = safeCopy( testArtifactsWithNonTestScope );
    }

    /**
     * Returns artifacts both used and declared.
     *
     * @return artifacts both used and declared
     */
    public Set<Dependency> getUsedDeclaredArtifacts()
    {
        return safeCopy( usedDeclaredArtifacts );
    }

    /**
     * Returns artifacts used but not declared.
     *
     * @return artifacts used but not declared
     */
    public Set<Dependency> getUsedUndeclaredArtifacts()
    {
        return safeCopy( usedUndeclaredArtifacts.keySet() );
    }

    /**
     * Returns artifacts used but not declared.
     *
     * @return artifacts used but not declared
     */
    public Map<Dependency, Set<String>> getUsedUndeclaredArtifactsWithClasses()
    {
        return safeCopy( usedUndeclaredArtifacts );
    }

    /**
     * Returns artifacts declared but not used.
     *
     * @return artifacts declared but not used
     */
    public Set<Dependency> getUnusedDeclaredArtifacts()
    {
        return safeCopy( unusedDeclaredArtifacts );
    }

    /**
     * Returns artifacts only used in tests but not declared with test scope.
     *
     * @return  artifacts only used in tests but not declared with test scope
     */
    public Set<Dependency> getTestArtifactsWithNonTestScope()
    {
        return safeCopy( testArtifactsWithNonTestScope );
    }

    /**
     * Filter non-compile scoped artifacts from unused declared.
     *
     * @return updated project dependency analysis
     * @since 1.3
     */
    public ProjectDependencyAnalysis ignoreNonCompile()
    {
        Set<Dependency> filteredUnusedDeclared = new HashSet<>( unusedDeclaredArtifacts );
        filteredUnusedDeclared.removeIf( artifact -> !artifact.getScope().equals( Scope.COMPILE ) );

        return new ProjectDependencyAnalysis( usedDeclaredArtifacts, usedUndeclaredArtifacts, filteredUnusedDeclared,
                testArtifactsWithNonTestScope );
    }

    /**
     * Force use status of some declared dependencies, to manually fix consequences of bytecode-level analysis which
     * happens to not detect some effective use (constants, annotation with source-retention, javadoc).
     *
     * @param forceUsedDependencies dependencies to move from "unused-declared" to "used-declared", with
     *                              <code>groupId:artifactId</code> format
     * @return updated project dependency analysis
     * @throws ProjectDependencyAnalyzerException if dependencies forced were either not declared or already detected as
     *                                            used
     * @since 1.3
     */
    @SuppressWarnings( "UnusedReturnValue" )
    public ProjectDependencyAnalysis forceDeclaredDependenciesUsage( String[] forceUsedDependencies )
        throws ProjectDependencyAnalyzerException
    {
        Set<String> forced = new HashSet<>( Arrays.asList( forceUsedDependencies ) );

        Set<Dependency> forcedUnusedDeclared = new HashSet<>( unusedDeclaredArtifacts );
        Set<Dependency> forcedUsedDeclared = new HashSet<>( usedDeclaredArtifacts );

        Iterator<Dependency> iter = forcedUnusedDeclared.iterator();
        while ( iter.hasNext() )
        {
            Dependency artifact = iter.next();

            if ( forced.remove( artifact.getGroupId() + ':' + artifact.getArtifactId() ) )
            {
                // ok, change artifact status from unused-declared to used-declared
                iter.remove();
                forcedUsedDeclared.add( artifact );
            }
        }

        if ( !forced.isEmpty() )
        {
            // trying to force dependencies as used-declared which were not declared or already detected as used
            Set<String> used = new HashSet<>();
            for ( Artifact artifact : usedDeclaredArtifacts )
            {
                String id = artifact.getGroupId() + ':' + artifact.getArtifactId();
                if ( forced.remove( id ) )
                {
                    used.add( id );
                }
            }

            StringBuilder builder = new StringBuilder();
            if ( !forced.isEmpty() )
            {
                builder.append( "not declared: " ).append( forced );
            }
            if ( !used.isEmpty() )
            {
                if ( builder.length() > 0 )
                {
                    builder.append( " and " );
                }
                builder.append( "declared but already detected as used: " ).append( used );
            }
            throw new ProjectDependencyAnalyzerException( "Trying to force use of dependencies which are " + builder );
        }

        return new ProjectDependencyAnalysis( forcedUsedDeclared, usedUndeclaredArtifacts, forcedUnusedDeclared,
                testArtifactsWithNonTestScope );
    }

    /**
     * <p>hashCode.</p>
     *
     * @return an int
     */
    public int hashCode()
    {
        int hashCode = getUsedDeclaredArtifacts().hashCode();
        hashCode = ( hashCode * 37 ) + getUsedUndeclaredArtifacts().hashCode();
        hashCode = ( hashCode * 37 ) + getUnusedDeclaredArtifacts().hashCode();
        hashCode = ( hashCode * 37 ) + getTestArtifactsWithNonTestScope().hashCode();

        return hashCode;
    }

    /** {@inheritDoc} */
    public boolean equals( Object object )
    {
        if ( object instanceof ProjectDependencyAnalysis )
        {
            ProjectDependencyAnalysis analysis = (ProjectDependencyAnalysis) object;

            return getUsedDeclaredArtifacts().equals( analysis.getUsedDeclaredArtifacts() )
                && getUsedUndeclaredArtifacts().equals( analysis.getUsedUndeclaredArtifacts() )
                && getUnusedDeclaredArtifacts().equals( analysis.getUnusedDeclaredArtifacts() )
                && getTestArtifactsWithNonTestScope().equals( analysis.getTestArtifactsWithNonTestScope() );
        }

        return false;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String toString()
    {
        StringBuilder buffer = new StringBuilder();

        if ( !getUsedDeclaredArtifacts().isEmpty() )
        {
            buffer.append( "usedDeclaredArtifacts=" ).append( getUsedDeclaredArtifacts() );
        }

        if ( !getUsedUndeclaredArtifacts().isEmpty() )
        {
            if ( buffer.length() > 0 )
            {
                buffer.append( "," );
            }

            buffer.append( "usedUndeclaredArtifacts=" ).append( getUsedUndeclaredArtifacts() );
        }

        if ( !getUnusedDeclaredArtifacts().isEmpty() )
        {
            if ( buffer.length() > 0 )
            {
                buffer.append( "," );
            }

            buffer.append( "unusedDeclaredArtifacts=" ).append( getUnusedDeclaredArtifacts() );
        }

        if ( !getTestArtifactsWithNonTestScope().isEmpty() )
        {
            if ( buffer.length() > 0 )
            {
                buffer.append( "," );
            }

            buffer.append( "testArtifactsWithNonTestScope=" ).append( getTestArtifactsWithNonTestScope() );
        }

        buffer.insert( 0, "[" );
        buffer.insert( 0, getClass().getName() );

        buffer.append( "]" );

        return buffer.toString();
    }

    // private methods --------------------------------------------------------

    private <T> Set<T> safeCopy( Set<T> set )
    {
        return ( set == null ) ? Collections.emptySet() : Collections.unmodifiableSet( new LinkedHashSet<>( set ) );
    }

    private static <T> Map<T, Set<String>> safeCopy( Map<T, Set<String>> origMap )
    {
        if ( origMap == null )
        {
            return Collections.emptyMap();
        }

        Map<T, Set<String>> map = new HashMap<>();

        for ( Map.Entry<T, Set<String>> e : origMap.entrySet() )
        {
            map.put( e.getKey(), Collections.unmodifiableSet( new LinkedHashSet<>( e.getValue() ) ) );
        }

        return map;
    }

    private static Map<Dependency, Set<String>> mapWithKeys( Set<Dependency> keys )
    {
        if ( keys == null )
        {
            return Collections.emptyMap();
        }

        Map<Dependency, Set<String>> map = new HashMap<>();

        for ( Dependency k : keys )
        {
            map.put( k, Collections.emptySet() );
        }

        return map;
    }
}
