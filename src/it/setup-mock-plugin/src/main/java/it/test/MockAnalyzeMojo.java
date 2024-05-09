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

import javax.inject.Inject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;

@Mojo( name = "mock-analyze", requiresDependencyResolution = ResolutionScope.TEST,
       defaultPhase = LifecyclePhase.VERIFY )
public class MockAnalyzeMojo extends AbstractMojo
{
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
    private MavenProject project;

    @Parameter( defaultValue = "${project.build.directory}/analysis.txt", readonly = true )
    private File output;

    @Parameter
    private Set<String> excludedClasses;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        try
        {
            ProjectDependencyAnalysis analysis = analyzer.analyze( project, excludedClasses );

            Files.createDirectories( output.toPath().getParent() );
            try ( PrintWriter printWriter = new UnixPrintWiter( output ) )
            {
                printWriter.println();

                printWriter.println( "UsedDeclaredArtifacts:" );
                analysis.getUsedDeclaredArtifacts().forEach( a -> printWriter.println( " " + a ) );
                printWriter.println();

                printWriter.println( "UsedUndeclaredArtifactsWithClasses:" );
                analysis.getUsedUndeclaredArtifactsWithClasses().forEach( ( a, c ) -> {
                    printWriter.println( " " + a );
                    c.forEach( i -> printWriter.println( "  " + i ) );
                } );
                printWriter.println();

                printWriter.println( "UnusedDeclaredArtifacts:" );
                analysis.getUnusedDeclaredArtifacts().forEach( a -> printWriter.println( " " + a ) );
                printWriter.println();

                printWriter.println( "TestArtifactsWithNonTestScope:" );
                analysis.getTestArtifactsWithNonTestScope().forEach( a -> printWriter.println( " " + a ) );
            }
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "analyze failed", e );
        }

        getLog().info( "Analyze done" );
    }
}
