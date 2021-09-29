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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

/**
 * <p>DefaultProjectDependencyAnalyzer class.</p>
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
@Component( role = ProjectDependencyAnalyzer.class )
public class DefaultProjectDependencyAnalyzer
    implements ProjectDependencyAnalyzer
{
    /**
     * ClassAnalyzer
     */
    @Requirement
    private ClassAnalyzer classAnalyzer;

    /**
     * DependencyAnalyzer
     */
    @Requirement
    private DependencyAnalyzer dependencyAnalyzer;

    /** {@inheritDoc} */
    public ProjectDependencyAnalysis analyze( MavenProject project )
        throws ProjectDependencyAnalyzerException
    {
        try
        {
            Map<Artifact, Set<String>> artifactClassMap = buildArtifactClassMap( project );

            Set<String> dependencyClasses = buildDependencyClasses( project );
            Set<String> mainDependencyClasses = buildMainDependencyClasses( project );

            Set<String> testOnlyDependencyClasses = buildTestDependencyClasses( project );

            Set<Artifact> declaredArtifacts = buildDeclaredArtifacts( project );

            Set<Artifact> usedArtifacts = buildUsedArtifacts( artifactClassMap, dependencyClasses );
            Set<Artifact> mainUsedArtifacts = buildUsedArtifacts( artifactClassMap, mainDependencyClasses );
            
            Set<Artifact> testArtifacts = buildUsedArtifacts( artifactClassMap, testOnlyDependencyClasses );
            Set<Artifact> testOnlyArtifacts = removeAll( testArtifacts, mainUsedArtifacts );
            
            Set<Artifact> usedDeclaredArtifacts = new LinkedHashSet<>( declaredArtifacts );
            usedDeclaredArtifacts.retainAll( usedArtifacts );

            Set<Artifact> usedUndeclaredArtifacts = new LinkedHashSet<>( usedArtifacts );
            usedUndeclaredArtifacts = removeAll( usedUndeclaredArtifacts, declaredArtifacts );

            Set<Artifact> unusedDeclaredArtifacts = new LinkedHashSet<>( declaredArtifacts );
            unusedDeclaredArtifacts = removeAll( unusedDeclaredArtifacts, usedArtifacts );

            Set<Artifact> testArtifactsWithNonTestScope = getTestArtifactsWithNonTestScope( testOnlyArtifacts );

            return new ProjectDependencyAnalysis( usedDeclaredArtifacts, usedUndeclaredArtifacts,
                                                  unusedDeclaredArtifacts, testArtifactsWithNonTestScope );
        }
        catch ( IOException exception )
        {
            throw new ProjectDependencyAnalyzerException( "Cannot analyze dependencies", exception );
        }
    }

    /**
     * This method defines a new way to remove the artifacts by using the conflict id. We don't care about the version
     * here because there can be only 1 for a given artifact anyway.
     * 
     * @param start initial set
     * @param remove set to exclude
     * @return set with remove excluded
     */
    private Set<Artifact> removeAll( Set<Artifact> start, Set<Artifact> remove )
    {
        Set<Artifact> results = new LinkedHashSet<>( start.size() );

        for ( Artifact artifact : start )
        {
            boolean found = false;

            for ( Artifact artifact2 : remove )
            {
                if ( artifact.getDependencyConflictId().equals( artifact2.getDependencyConflictId() ) )
                {
                    found = true;
                    break;
                }
            }

            if ( !found )
            {
                results.add( artifact );
            }
        }

        return results;
    }

    private Set<Artifact> getTestArtifactsWithNonTestScope( Set<Artifact> testOnlyArtifacts )
    {
        Set<Artifact> nonTestScopeArtifacts = new LinkedHashSet<>();

        for ( Artifact artifact : testOnlyArtifacts )
        {
            if ( artifact.getScope().equals( "compile" ) )
            {
                nonTestScopeArtifacts.add( artifact );
            }
        }

        return nonTestScopeArtifacts;
    }

    private Map<Artifact, Set<String>> buildArtifactClassMap( MavenProject project )
        throws IOException
    {
        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();

        Set<Artifact> dependencyArtifacts = project.getArtifacts();

        for ( Artifact artifact : dependencyArtifacts )
        {
            File file = artifact.getFile();

            if ( file != null && file.getName().endsWith( ".jar" ) )
            {
                // optimized solution for the jar case

                try ( JarFile jarFile = new JarFile( file ) )
                {
                    Enumeration<JarEntry> jarEntries = jarFile.entries();

                    Set<String> classes = new HashSet<>();

                    while ( jarEntries.hasMoreElements() )
                    {
                        String entry = jarEntries.nextElement().getName();
                        if ( entry.endsWith( ".class" ) )
                        {
                            String className = entry.replace( '/', '.' );
                            className = className.substring( 0, className.length() - ".class".length() );
                            classes.add( className );
                        }
                    }

                    artifactClassMap.put( artifact, classes );
                }
            }
            else if ( file != null && file.isDirectory() )
            {
                URL url = file.toURI().toURL();
                Set<String> classes = classAnalyzer.analyze( url );

                artifactClassMap.put( artifact, classes );
            }
        }

        return artifactClassMap;
    }

    private Set<String> buildTestDependencyClasses( MavenProject project ) throws IOException
    {
        Set<String> testOnlyDependencyClasses = new HashSet<>();

        String outputDirectory = project.getBuild().getOutputDirectory();
        Set<String> nonTestDependencyClasses = new HashSet<>( buildDependencyClasses( outputDirectory ) );

        String testOutputDirectory = project.getBuild().getTestOutputDirectory();
        Set<String> testDependencyClasses = new HashSet<>( buildDependencyClasses( testOutputDirectory ) );

        for ( String testString : testDependencyClasses )
        {
            if ( !nonTestDependencyClasses.contains( testString ) )
            {
                testOnlyDependencyClasses.add( testString );
            }
        }

        return testOnlyDependencyClasses;
    }

    private Set<String> buildDependencyClasses( MavenProject project )
        throws IOException
    {

        String outputDirectory = project.getBuild().getOutputDirectory();
        Set<String> dependencyClasses = new HashSet<>( buildDependencyClasses( outputDirectory ) );

        String testOutputDirectory = project.getBuild().getTestOutputDirectory();
        dependencyClasses.addAll( buildDependencyClasses( testOutputDirectory ) );

        return dependencyClasses;
    }
    
    private Set<String> buildMainDependencyClasses( MavenProject project )
                    throws IOException
    {

        String outputDirectory = project.getBuild().getOutputDirectory();
        Set<String> dependencyClasses = new HashSet<>( buildDependencyClasses( outputDirectory ) );

        return dependencyClasses;
    }

    private Set<String> buildDependencyClasses( String path )
        throws IOException
    {
        URL url = new File( path ).toURI().toURL();

        return dependencyAnalyzer.analyze( url );
    }

    private Set<Artifact> buildDeclaredArtifacts( MavenProject project )
    {
        Set<Artifact> declaredArtifacts = project.getDependencyArtifacts();

        if ( declaredArtifacts == null )
        {
            declaredArtifacts = Collections.emptySet();
        }

        return declaredArtifacts;
    }

    private Set<Artifact> buildUsedArtifacts( Map<Artifact, Set<String>> artifactClassMap,
                                              Set<String> dependencyClasses )
    {
        Set<Artifact> usedArtifacts = new HashSet<>();

        for ( String className : dependencyClasses )
        {
            Artifact artifact = findArtifactForClassName( artifactClassMap, className );

            if ( artifact != null )
            {
                usedArtifacts.add( artifact );
            }
        }

        return usedArtifacts;
    }

    private Artifact findArtifactForClassName( Map<Artifact, Set<String>> artifactClassMap, String className )
    {
        for ( Map.Entry<Artifact, Set<String>> entry : artifactClassMap.entrySet() )
        {
            if ( entry.getValue().contains( className ) )
            {
                return entry.getKey();
            }
        }

        return null;
    }
}
