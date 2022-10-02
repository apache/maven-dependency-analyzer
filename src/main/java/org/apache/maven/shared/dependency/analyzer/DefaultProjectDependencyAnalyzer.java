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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.Node;
import org.apache.maven.api.Project;
import org.apache.maven.api.Scope;
import org.apache.maven.api.Session;

/**
 * <p>DefaultProjectDependencyAnalyzer class.</p>
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
@Named
@Singleton
public class DefaultProjectDependencyAnalyzer
    implements ProjectDependencyAnalyzer
{
    /**
     * ClassAnalyzer
     */
    private final ClassAnalyzer classAnalyzer;

    /**
     * DependencyAnalyzer
     */
    private final DependencyAnalyzer dependencyAnalyzer;

    @Inject
    public DefaultProjectDependencyAnalyzer( ClassAnalyzer classAnalyzer, DependencyAnalyzer dependencyAnalyzer )
    {
        this.classAnalyzer = classAnalyzer;
        this.dependencyAnalyzer = dependencyAnalyzer;
    }

    /** {@inheritDoc} */
    public ProjectDependencyAnalysis analyze( Session session, Project project )
        throws ProjectDependencyAnalyzerException
    {
        try
        {
            // Collect dependencies
            Node root = session.collectDependencies( project );

            // Make sure all artifacts are resolved
            session.resolveArtifacts( root.stream()
                    .skip( 1 ) // skip the root
                    .map( Node::getDependency )
                    .map( session::createArtifactCoordinate )
                    .collect( Collectors.toList() ) );

            // Build dependency --> classes mapping and class -> dependency
            Map<Dependency, Set<String>> artifactClassMap = root.stream()
                    .skip( 1 )
                    .map( Node::getDependency )
                    .collect( Collectors.toMap(
                            d -> d,
                            d -> getClasses( session.getArtifactPath( d ).get() ),
                            DefaultProjectDependencyAnalyzer::union,
                            LinkedHashMap::new ) // note that we want to keep the ordering
                    );
            Map<String, Dependency> classToDependencyMap = new HashMap<>();
            artifactClassMap.forEach( ( u, vs ) -> vs.forEach( v -> classToDependencyMap.putIfAbsent( v, u ) ) );

            // Compute set of classes for main and test
            Set<String> mainDependencyClasses = buildDependencyClasses( project.getBuild().getOutputDirectory() );
            Set<String> testDependencyClasses = buildDependencyClasses( project.getBuild().getTestOutputDirectory() );
            Set<String> dependencyClasses = union( mainDependencyClasses, testDependencyClasses );

            // Compute dependencies used by main and test classes
            Map<Dependency, Set<String>> usedArtifactsWithClasses =
                    buildUsedArtifacts( classToDependencyMap, dependencyClasses );
            Set<Dependency> usedArtifacts = usedArtifactsWithClasses.keySet();
            Set<Dependency> mainUsedArtifacts =
                    buildUsedArtifacts( classToDependencyMap, mainDependencyClasses ).keySet();
            Set<Dependency> testArtifacts =
                    buildUsedArtifacts( classToDependencyMap, testDependencyClasses ).keySet();
            Set<Dependency> testOnlyArtifacts = remove( testArtifacts, mainUsedArtifacts );

            // Compute direct dependencies, keep order
            Set<Dependency> declaredArtifacts = root.getChildren().stream()
                    .map( Node::getDependency )
                    .collect( Collectors.toCollection( LinkedHashSet::new ) );

            Set<Dependency> usedDeclaredArtifacts = retain( declaredArtifacts, usedArtifacts );

            Set<Dependency> usedUndeclaredArtifacts = remove( usedArtifacts, declaredArtifacts );
            Map<Dependency, Set<String>> usedUndeclaredArtifactsWithClasses =
                    new LinkedHashMap<>( usedArtifactsWithClasses );
            usedUndeclaredArtifactsWithClasses.keySet().retainAll( usedUndeclaredArtifacts );

            Set<Dependency> unusedDeclaredArtifacts = remove( declaredArtifacts, usedArtifacts );

            Set<Dependency> testArtifactsWithNonTestScope = getTestArtifactsWithNonTestScope( testOnlyArtifacts );

            return new ProjectDependencyAnalysis( usedDeclaredArtifacts,
                                                  usedUndeclaredArtifactsWithClasses,
                                                  unusedDeclaredArtifacts,
                                                  testArtifactsWithNonTestScope );
        }
        catch ( IOException exception )
        {
            throw new ProjectDependencyAnalyzerException( "Cannot analyze dependencies", exception );
        }
    }

    private static <T> Set<T> remove( Set<T> start, Set<T> remove )
    {
        return start.stream().filter( a -> !remove.contains( a ) ).collect( toLinkedSet() );
    }

    private static <T> Set<T> union( Set<T> s1, Set<T> s2 )
    {
        return Stream.concat( s1.stream(), s2.stream() ).collect( toLinkedSet() );
    }

    private static <T> Set<T> retain( Set<T> s1, Set<T> s2 )
    {
        return s1.stream().filter( s2::contains ).collect( toLinkedSet() );
    }

    private static <T> Collector<T, ?, Set<T>> toLinkedSet()
    {
        return Collectors.toCollection( LinkedHashSet::new );
    }

    private static Set<Dependency> getTestArtifactsWithNonTestScope( Set<Dependency> testOnlyArtifacts )
    {
        return testOnlyArtifacts.stream()
                .filter( d -> d.getScope().equals( Scope.COMPILE ) )
                .collect( toLinkedSet() );
    }

    private Set<String> getClasses( Path file )
    {
        try
        {
            return classAnalyzer.analyze( file );
        }
        catch ( IOException e )
        {
            throw new ProjectDependencyAnalyzerException( "Error while processing jar " + file, e );
        }
    }

    private Set<String> buildDependencyClasses( String path )
        throws IOException
    {
        return dependencyAnalyzer.analyze( Paths.get( path ) );
    }

    private static Map<Dependency, Set<String>> buildUsedArtifacts( Map<String, Dependency> classToDependencyMap,
                                                                    Set<String> dependencyClasses )
    {
        Map<Dependency, Set<String>> usedArtifacts = new HashMap<>();
        dependencyClasses.forEach( cl ->
            Optional.ofNullable( classToDependencyMap.get( cl ) )
                    .ifPresent( dep -> usedArtifacts.computeIfAbsent( dep, k -> new HashSet<>() ).add( cl ) ) );
        return usedArtifacts;
    }

}
