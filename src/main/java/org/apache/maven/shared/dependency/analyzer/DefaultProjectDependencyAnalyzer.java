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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

/**
 * 
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @plexus.component role="org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer"
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

            Set dependencyClasses = buildDependencyClasses( project );

            Set declaredArtifacts = project.getDependencyArtifacts();

            Set usedArtifacts = new HashSet();

            for ( Iterator dependencyIterator = dependencyClasses.iterator(); dependencyIterator.hasNext(); )
            {
                String className = (String) dependencyIterator.next();

                Artifact artifact = findArtifactForClassName( artifactClassMap, className );

                if ( artifact != null )
                    usedArtifacts.add( artifact );
            }

            Set usedDeclaredArtifacts = new HashSet( declaredArtifacts );
            usedDeclaredArtifacts.retainAll( usedArtifacts );

            Set usedUndeclaredArtifacts = new HashSet( usedArtifacts );
            usedUndeclaredArtifacts.removeAll( declaredArtifacts );

            Set unusedDeclaredArtifacts = new HashSet( declaredArtifacts );
            unusedDeclaredArtifacts.removeAll( usedArtifacts );

            return new ProjectDependencyAnalysis( usedDeclaredArtifacts, usedUndeclaredArtifacts,
                                                  unusedDeclaredArtifacts );
        }
        catch ( IOException exception )
        {
            throw new ProjectDependencyAnalyzerException( "Cannot analyze dependencies", exception );
        }
    }

    // private methods --------------------------------------------------------

    private Map buildArtifactClassMap( MavenProject project )
        throws IOException
    {
        Map artifactClassMap = new HashMap();

        Set dependencyArtifacts = project.getArtifacts();

        for ( Iterator iterator = dependencyArtifacts.iterator(); iterator.hasNext(); )
        {
            Artifact artifact = (Artifact) iterator.next();

            File file = artifact.getFile();

            if ( file != null )
            {
                URL url = file.toURL();

                Set classes = classAnalyzer.analyze( url );

                artifactClassMap.put( artifact, classes );
            }
        }

        return artifactClassMap;
    }

    private Set buildDependencyClasses( MavenProject project )
        throws IOException
    {
        String buildDirectory = project.getBuild().getDirectory();

        URL buildDirectoryURL = new File( buildDirectory ).toURI().toURL();

        return dependencyAnalyzer.analyze( buildDirectoryURL );
    }

    private Artifact findArtifactForClassName( Map artifactClassMap, String className )
    {
        for ( Iterator artifactIterator = artifactClassMap.keySet().iterator(); artifactIterator.hasNext(); )
        {
            Artifact artifact = (Artifact) artifactIterator.next();

            Set artifactClassNames = (Set) artifactClassMap.get( artifact );

            for ( Iterator classNameIterator = artifactClassNames.iterator(); classNameIterator.hasNext(); )
            {
                String artifactClassName = (String) classNameIterator.next();

                if ( artifactClassName.equals( className ) )
                    return artifact;
            }
        }

        return null;
    }
}
