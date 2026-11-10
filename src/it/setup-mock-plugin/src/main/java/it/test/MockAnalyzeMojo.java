package it.test;
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
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Set;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;

@Mojo( name = "mock-analyze", defaultPhase = "verify" )
public class MockAnalyzeMojo implements org.apache.maven.api.plugin.Mojo
{
    private static String format( Dependency d )
    {
        // same layout as the Maven 3 Artifact.toString(), so that the verify scripts stay unchanged
        StringBuilder sb = new StringBuilder();
        sb.append( d.getGroupId() ).append( ':' ).append( d.getArtifactId() ).append( ':' ).append( d.getType().id() );
        if ( d.getClassifier() != null && !d.getClassifier().isEmpty() )
        {
            sb.append( ':' ).append( d.getClassifier() );
        }
        return sb.append( ':' ).append( d.getVersion() ).append( ':' ).append( d.getScope().id() ).toString();
    }

    class UnixPrintWiter extends PrintWriter
    {
        public UnixPrintWiter( File file ) throws FileNotFoundException
        {
            super( file );
        }

        @Override
        public void println()
        {
            write( '\n' );
        }
    }

    @Inject
    private ProjectDependencyAnalyzer analyzer;

    @Inject
    private Session session;

    @Inject
    private Project project;

    @Inject
    private Log log;

    @Parameter( defaultValue = "${project.build.directory}/analysis.txt", readonly = true )
    private File output;

    @Parameter
    private Set<String> excludedClasses;

    @Override
    public void execute() throws MojoException
    {
        try
        {
            ProjectDependencyAnalysis analysis = analyzer.analyze( session, project, excludedClasses );

            Files.createDirectories( output.toPath().getParent() );
            try ( PrintWriter printWriter = new UnixPrintWiter( output ) )
            {
                printWriter.println();

                printWriter.println( "UsedDeclaredArtifacts:" );
                analysis.getUsedDeclaredArtifacts().forEach( a -> printWriter.println( " " + format( a ) ) );
                printWriter.println();

                printWriter.println( "UsedUndeclaredArtifactsWithClasses:" );
                analysis.getUsedUndeclaredArtifactsWithClasses().forEach( ( a, c ) -> {
                    printWriter.println( " " + format( a ) );
                    c.forEach( i -> printWriter.println( "  " + i ) );
                } );
                printWriter.println();

                printWriter.println( "UnusedDeclaredArtifacts:" );
                analysis.getUnusedDeclaredArtifacts().forEach( a -> printWriter.println( " " + format( a ) ) );
                printWriter.println();

                printWriter.println( "TestArtifactsWithNonTestScope:" );
                analysis.getTestArtifactsWithNonTestScope().forEach( a -> printWriter.println( " " + format( a ) ) );
            }
        }
        catch ( Exception e )
        {
            throw new MojoException( "analyze failed", e );
        }

        log.info( "Analyze done" );
    }
}
