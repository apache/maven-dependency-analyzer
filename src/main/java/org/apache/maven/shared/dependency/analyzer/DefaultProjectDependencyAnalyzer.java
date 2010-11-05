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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

/**
 * 
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @plexus.component role="org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer" role-hint="default"
 */
public class DefaultProjectDependencyAnalyzer
    implements ProjectDependencyAnalyzer
{
    // fields -----------------------------------------------------------------

    /**
     * ClassAnalyzer
     * 
     * @plexus.requirement
     */
    private ClassAnalyzer classAnalyzer;

    /**
     * DependencyAnalyzer
     * 
     * @plexus.requirement
     */
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
            Map artifactClassMap = buildArtifactClassMap( project );

            Set dependencyClasses = buildDependencyClasses( project , artifactClassMap );

            Set declaredArtifacts = buildDeclaredArtifacts( project );
            
            Set usedArtifacts = buildUsedArtifacts( artifactClassMap, dependencyClasses );
            
            Set usedDeclaredArtifacts = new LinkedHashSet( declaredArtifacts );
            usedDeclaredArtifacts.retainAll( usedArtifacts );

            Set usedUndeclaredArtifacts = new LinkedHashSet( usedArtifacts );
            usedUndeclaredArtifacts = removeAll( usedUndeclaredArtifacts, declaredArtifacts );

            Set unusedDeclaredArtifacts = new LinkedHashSet( declaredArtifacts );
            unusedDeclaredArtifacts = removeAll( unusedDeclaredArtifacts, usedArtifacts );

            return new ProjectDependencyAnalysis( usedDeclaredArtifacts, usedUndeclaredArtifacts,
                                                  unusedDeclaredArtifacts );
        }
        catch ( IOException exception )
        {
            throw new ProjectDependencyAnalyzerException( "Cannot analyze dependencies", exception );
        }
    }

    /**
     * This method defines a new way to remove the artifacts by using the
     * conflict id. We don't care about the version here because there can be
     * only 1 for a given artifact anyway.
     * 
     * @param start
     *            initial set
     * @param remove
     *            set to exclude
     * @return set with remove excluded
     */
    private Set removeAll( Set start, Set remove )
    {
        Set results = new LinkedHashSet( start.size() );
        Iterator iter = start.iterator();
        while ( iter.hasNext() )
        {
            Artifact artifact = (Artifact) iter.next();
            Iterator iter2 = remove.iterator();
            boolean found = false;
            while ( iter2.hasNext() )
            {
                Artifact artifact2 = (Artifact) iter2.next();
                if ( artifact.getDependencyConflictId().equals( artifact2.getDependencyConflictId() ) )
                {
                    found = true;
                }
            }
            if ( !found )
            {
                results.add( artifact );
            }
        }
        return results;
    }

    // private methods --------------------------------------------------------

    private Map buildArtifactClassMap( MavenProject project )
        throws IOException
    {
        Map artifactClassMap = new LinkedHashMap();

        Set dependencyArtifacts = project.getArtifacts();

        for ( Iterator iterator = dependencyArtifacts.iterator(); iterator.hasNext(); )
        {
            Artifact artifact = (Artifact) iterator.next();

            File file = artifact.getFile();

            if ( file != null && file.getName().endsWith( ".jar" ) )
            {
                URL url = file.toURL();

                Set classes = classAnalyzer.analyze( url );

                artifactClassMap.put( artifact, classes );
            }
        }

        return artifactClassMap;
    }

    protected Set buildDependencyClasses( MavenProject project , Map artifactClassMap )
        throws IOException
    {
        Set dependencyClasses = new HashSet();
        
        String outputDirectory = project.getBuild().getOutputDirectory();
        dependencyClasses.addAll( buildDependencyClasses( outputDirectory ) );
        
        String testOutputDirectory = project.getBuild().getTestOutputDirectory();
        dependencyClasses.addAll( buildDependencyClasses( testOutputDirectory ) );

        return dependencyClasses;
    }
    
    private Set buildDependencyClasses( String path )
        throws IOException
    {
        URL url = new File( path ).toURI().toURL();

        return dependencyAnalyzer.analyze( url );
    }
    
    private Set buildDeclaredArtifacts( MavenProject project )
    {
        Set declaredArtifacts = project.getDependencyArtifacts();
        
        if ( declaredArtifacts == null )
        {
            declaredArtifacts = Collections.EMPTY_SET;
        }
        
        return declaredArtifacts;
    }
    
    private Set buildUsedArtifacts( Map artifactClassMap, Set dependencyClasses )
    {
        Set usedArtifacts = new HashSet();

        for ( Iterator dependencyIterator = dependencyClasses.iterator(); dependencyIterator.hasNext(); )
        {
            String className = (String) dependencyIterator.next();

            Artifact artifact = findArtifactForClassName( artifactClassMap, className );

            if ( artifact != null )
            {
                usedArtifacts.add( artifact );
            }
        }
        
        return usedArtifacts;
    }

    protected Artifact findArtifactForClassName( Map artifactClassMap, String className )
    {
        for ( Iterator artifactIterator = artifactClassMap.keySet().iterator(); artifactIterator.hasNext(); )
        {
            Artifact artifact = (Artifact) artifactIterator.next();

            Set artifactClassNames = (Set) artifactClassMap.get( artifact );

            if ( artifactClassNames.contains( className ) )
            {
                return artifact;
            }
        }

        return null;
    }
}
