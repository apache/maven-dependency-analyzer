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

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.Project;
import org.apache.maven.api.ResolutionScope;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Component;
import org.apache.maven.api.plugin.annotations.LifecyclePhase;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;

@Mojo( name = "mock-analyze",
       requiresDependencyResolution = ResolutionScope.TEST,
       defaultPhase = LifecyclePhase.VERIFY )
public class MockAnalyzeMojo implements org.apache.maven.api.plugin.Mojo
{
    class UnixPrintWiter extends PrintWriter
    {
        public UnixPrintWiter( Path file ) throws FileNotFoundException
        {
            super( file.toFile() );
        }

        @Override
        public void println()
        {
            write( '\n' );
        }
    }

    @Component
    private ProjectDependencyAnalyzer analyzer;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private Session session;

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private Project project;

    @Parameter( defaultValue = "${project.build.directory}/analysis.txt", readonly = true )
    private Path output;

    @Component
    private Log log;

    @Override
    public void execute() throws MojoException
    {
        try
        {
            ProjectDependencyAnalysis analysis = analyzer.analyze( session, project );

            Files.createDirectories( output.getParent() );
            try ( PrintWriter printWriter = new UnixPrintWiter( output ) )
            {
                printWriter.println();

                printWriter.println( "UsedDeclaredArtifacts:" );
                analysis.getUsedDeclaredArtifacts().forEach( a -> printWriter.println( " " + toString( a ) ) );
                printWriter.println();

                printWriter.println( "UsedUndeclaredArtifactsWithClasses:" );
                analysis.getUsedUndeclaredArtifactsWithClasses().forEach( ( a, c ) -> {
                    printWriter.println( " " + toString( a ) );
                    c.forEach( i -> printWriter.println( "  " + i ) );
                } );
                printWriter.println();

                printWriter.println( "UnusedDeclaredArtifacts:" );
                analysis.getUnusedDeclaredArtifacts().forEach( a -> printWriter.println( " " + toString( a ) ) );
                printWriter.println();

                printWriter.println( "TestArtifactsWithNonTestScope:" );
                analysis.getTestArtifactsWithNonTestScope().forEach( a -> printWriter.println( " " + toString( a ) ) );
            }
        }
        catch ( Exception e )
        {
            throw new MojoException( "analyze failed", e );
        }

        log.info( "Analyze done" );
    }

    private String toString( Dependency a )
    {
        return a.key() + ":" + a.getScope().id();
    }
}
