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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

/**
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
@Component( role = ProjectDependencyAnalyzer.class )
public class DefaultProjectDependencyAnalyzer
    implements ProjectDependencyAnalyzer
{
    // fields -----------------------------------------------------------------

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

    // ProjectDependencyAnalyzer methods --------------------------------------

    /*
     * @see org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer#analyze(org.apache.maven.project.MavenProject)
     */
    public ProjectDependencyAnalysis analyze( MavenProject project )
        throws ProjectDependencyAnalyzerException
    {
        try
        {
            Map<Artifact, Set<String>> artifactClassMap = buildArtifactClassMap( project );

            Set<DependencyUsage> dependencyUsages = buildDependencyUsages( project );

            Set<String> testOnlyDependencyClasses = buildTestDependencyClasses( project );

            Set<Artifact> declaredArtifacts = buildDeclaredArtifacts( project );

            Map<Artifact, Set<DependencyUsage>> usedArtifacts = buildArtifactToUsageMap( artifactClassMap,
                                                                                         dependencyUsages );

            Set<Artifact> testOnlyArtifacts = buildUsedArtifacts( artifactClassMap, testOnlyDependencyClasses );

            Map<Artifact, Set<DependencyUsage>> usedDeclaredArtifacts = buildMutableCopy( usedArtifacts );
            usedDeclaredArtifacts.keySet().retainAll( declaredArtifacts );

            Map<Artifact, Set<DependencyUsage>> usedUndeclaredArtifacts = buildMutableCopy( usedArtifacts );
            removeAll( usedUndeclaredArtifacts.keySet(), declaredArtifacts );

            Set<Artifact> unusedDeclaredArtifacts = new LinkedHashSet<Artifact>( declaredArtifacts );
            removeAll( unusedDeclaredArtifacts, usedArtifacts.keySet() );

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
     */
    private void removeAll( Set<Artifact> start, Set<Artifact> remove )
    {
        for ( Iterator<Artifact> iterator = start.iterator(); iterator.hasNext(); )
        {
            Artifact artifact = iterator.next();

            for ( Artifact artifact2 : remove )
            {
                if ( artifact.getDependencyConflictId().equals( artifact2.getDependencyConflictId() ) )
                {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    private Set<Artifact> getTestArtifactsWithNonTestScope( Set<Artifact> testOnlyArtifacts )
    {
        Set<Artifact> nonTestScopeArtifacts = new LinkedHashSet<Artifact>();

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
        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<Artifact, Set<String>>();

        @SuppressWarnings( "unchecked" )
        Set<Artifact> dependencyArtifacts = project.getArtifacts();

        for ( Artifact artifact : dependencyArtifacts )
        {
            File file = artifact.getFile();

            if ( file != null && file.getName().endsWith( ".jar" ) )
            {
                // optimized solution for the jar case
                JarFile jarFile = new JarFile( file );

                try
                {
                    Enumeration<JarEntry> jarEntries = jarFile.entries();

                    Set<String> classes = new HashSet<String>();

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
                finally
                {
                    try
                    {
                        jarFile.close();
                    }
                    catch ( IOException ignore )
                    {
                        // ingore
                    }
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
        Set<String> nonTestDependencyClasses = new HashSet<>();
        Set<String> testDependencyClasses = new HashSet<>();
        Set<String> testOnlyDependencyClasses = new HashSet<>();

        String outputDirectory = project.getBuild().getOutputDirectory();
        for ( DependencyUsage nonTestUsage : buildDependencyUsages( outputDirectory ) )
        {
            nonTestDependencyClasses.add( nonTestUsage.getDependencyClass() );
        }

        String testOutputDirectory = project.getBuild().getTestOutputDirectory();
        for ( DependencyUsage testUsage : buildDependencyUsages( testOutputDirectory ) )
        {
            testDependencyClasses.add( testUsage.getDependencyClass() );
        }

        for ( String testString : testDependencyClasses )
        {
            if ( !nonTestDependencyClasses.contains( testString ) )
            {
                testOnlyDependencyClasses.add( testString );
            }
        }

        return testOnlyDependencyClasses;
    }

    private Set<DependencyUsage> buildDependencyUsages( MavenProject project )
        throws IOException
    {
        Set<DependencyUsage> dependencyUsages = new HashSet<DependencyUsage>();

        String outputDirectory = project.getBuild().getOutputDirectory();
        dependencyUsages.addAll( buildDependencyUsages( outputDirectory ) );

        String testOutputDirectory = project.getBuild().getTestOutputDirectory();
        dependencyUsages.addAll( buildDependencyUsages( testOutputDirectory ) );

        return dependencyUsages;
    }

    private Set<DependencyUsage> buildDependencyUsages( String path )
        throws IOException
    {
        URL url = new File( path ).toURI().toURL();

        return dependencyAnalyzer.analyzeWithUsages( url );
    }

    private Set<Artifact> buildDeclaredArtifacts( MavenProject project )
    {
        @SuppressWarnings( "unchecked" )
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
        Set<Artifact> usedArtifacts = new HashSet<Artifact>();

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

    private Map<Artifact, Set<DependencyUsage>> buildArtifactToUsageMap( Map<Artifact, Set<String>> artifactClassMap,
                                                                         Set<DependencyUsage> dependencyUsages )
    {
        Map<String, Set<DependencyUsage>> dependencyClassToUsages = buildDependencyClassToUsageMap( dependencyUsages );

        Map<Artifact, Set<DependencyUsage>> artifactToUsages = new HashMap<Artifact, Set<DependencyUsage>>();

        for ( Entry<String, Set<DependencyUsage>> entry : dependencyClassToUsages.entrySet() )
        {
            Artifact artifact = findArtifactForClassName( artifactClassMap, entry.getKey() );

            if ( artifact != null )
            {
                if ( !artifactToUsages.containsKey( artifact ) )
                {
                    artifactToUsages.put( artifact, new HashSet<DependencyUsage>() );
                }

                artifactToUsages.get( artifact ).addAll( entry.getValue() );
            }
        }

        return artifactToUsages;
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

    private Map<String, Set<DependencyUsage>> buildDependencyClassToUsageMap( Set<DependencyUsage> dependencyUsages )
    {
        Map<String, Set<DependencyUsage>> dependencyClassToUsages = new HashMap<String, Set<DependencyUsage>>();

        for ( DependencyUsage dependencyUsage : dependencyUsages )
        {
            String dependencyClass = dependencyUsage.getDependencyClass();

            if ( !dependencyClassToUsages.containsKey( dependencyClass ) )
            {
                dependencyClassToUsages.put( dependencyClass, new HashSet<DependencyUsage>() );
            }

            dependencyClassToUsages.get( dependencyClass ).add( dependencyUsage );
        }

        return dependencyClassToUsages;
    }

    private Map<Artifact, Set<DependencyUsage>> buildMutableCopy( Map<Artifact, Set<DependencyUsage>> map )
    {
        Map<Artifact, Set<DependencyUsage>> copy = new LinkedHashMap<Artifact, Set<DependencyUsage>>();

        for ( Entry<Artifact, Set<DependencyUsage>> entry : map.entrySet() )
        {
            copy.put( entry.getKey(), new LinkedHashSet<DependencyUsage>( entry.getValue() ) );
        }

        return copy;
    }
}
