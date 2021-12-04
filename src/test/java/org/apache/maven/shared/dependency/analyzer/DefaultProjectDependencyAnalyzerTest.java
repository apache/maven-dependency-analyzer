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

import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.test.plugin.BuildTool;
import org.apache.maven.shared.test.plugin.ProjectTool;
import org.apache.maven.shared.test.plugin.RepositoryTool;
import org.apache.maven.shared.test.plugin.TestToolsException;
import org.codehaus.plexus.PlexusTestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.apache.commons.lang3.SystemUtils.isJavaVersionAtLeast;
import static org.junit.Assume.assumeTrue;

/**
 * Tests <code>DefaultProjectDependencyAnalyzer</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see DefaultProjectDependencyAnalyzer
 */
@RunWith( JUnit4.class )
public class DefaultProjectDependencyAnalyzerTest
    extends PlexusTestCase
{
    private BuildTool buildTool;

    private ProjectTool projectTool;

    private ProjectDependencyAnalyzer analyzer;

    private static File localRepo;

    /*
     * @see org.codehaus.plexus.PlexusTestCase#setUp()
     */
    @Before
    public void setUp()
        throws Exception
    {
        super.setUp();

        buildTool = (BuildTool) lookup( BuildTool.ROLE );

        projectTool = (ProjectTool) lookup( ProjectTool.ROLE );

        if ( localRepo == null )
        {
            RepositoryTool repositoryTool = (RepositoryTool) lookup( RepositoryTool.ROLE );
            localRepo = repositoryTool.findLocalRepositoryDirectory();
            System.out.println( "Local repository: " + localRepo );
        }

        analyzer = (ProjectDependencyAnalyzer) lookup( ProjectDependencyAnalyzer.class.getName() );
    }

    @Test
    public void testPom()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "pom/pom.xml" );

        MavenProject project = getProject( "pom/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis();

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testJarWithNoDependencies()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithNoDependencies/pom.xml" );

        MavenProject project = getProject( "jarWithNoDependencies/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis();

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testJava8methodRefs()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        assumeTrue( SystemUtils.isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) );

        // Only visible through constant pool analysis (supported for JDK8+)
        compileProject( "java8methodRefs/pom.xml" );

        MavenProject project = getProject( "java8methodRefs/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        Artifact project1 = createArtifact( "commons-io", "commons-io", "jar", "2.4", "compile" );
        Artifact project2 = createArtifact( "commons-lang", "commons-lang", "jar", "2.6", "compile" );
        Set<Artifact> usedDeclaredArtifacts = new HashSet<>( Arrays.asList( project1, project2 ) );

        ProjectDependencyAnalysis expectedAnalysis =
            new ProjectDependencyAnalysis( usedDeclaredArtifacts, new HashSet<Artifact>(), new HashSet<Artifact>(),
                    new HashSet<Artifact>() );

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testInlinedStaticReference()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        assumeTrue( SystemUtils.isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) );

        // Only visible through constant pool analysis (supported for JDK8+)
        compileProject( "inlinedStaticReference/pom.xml" );

        MavenProject project = getProject( "inlinedStaticReference/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        Artifact project1 = createArtifact( "dom4j", "dom4j", "jar", "1.6.1", "compile" );
        Set<Artifact> usedDeclaredArtifacts = Collections.singleton( project1 );

        ProjectDependencyAnalysis expectedAnalysis =
            new ProjectDependencyAnalysis( usedDeclaredArtifacts, new HashSet<Artifact>(), new HashSet<Artifact>(),
                    new HashSet<Artifact>() );

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testJarWithCompileDependency()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithCompileDependency/pom.xml" );

        MavenProject project2 = getProject( "jarWithCompileDependency/project2/pom.xml" );

        if ( project2.getBuild().getOutputDirectory().contains( "${" ) )
        {
            // if Maven version used as dependency is upgraded to >= 2.2.0
            throw new TestToolsException( "output directory was not interpolated: "
                + project2.getBuild().getOutputDirectory() );
        }

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project2 );
        
        assertTrue( "Incorrectly classified Guava as testonly",
                   actualAnalysis.getTestArtifactsWithNonTestScope().isEmpty() );

        Artifact project1 = createArtifact( "org.apache.maven.shared.dependency-analyzer.tests",
                                            "jarWithCompileDependency1", "jar", "1.0", "compile" );
        Artifact guava = createArtifact( "com.google.guava", "guava", "jar", "30.1.1-android", "compile" );
        Set<Artifact> usedDeclaredArtifacts = new HashSet<>( Arrays.asList( project1, guava ) );
        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, (Set<Artifact>) null, null,
                null );

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testForceDeclaredDependenciesUsage()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithTestDependency/pom.xml" );

        MavenProject project2 = getProject( "jarWithTestDependency/project2/pom.xml" );

        ProjectDependencyAnalysis analysis = analyzer.analyze( project2 );

        try
        {
            analysis.forceDeclaredDependenciesUsage( new String[] {
                "org.apache.maven.shared.dependency-analyzer.tests:jarWithTestDependency1" } );
            fail( "failure expected since junit dependency is declared-used" );
        }
        catch ( ProjectDependencyAnalyzerException pdae )
        {
            assertTrue( pdae.getMessage().contains( "Trying to force use of dependencies which are "
                + "declared but already detected as used: "
                + "[org.apache.maven.shared.dependency-analyzer.tests:jarWithTestDependency1]" ) );
        }

        try
        {
            analysis.forceDeclaredDependenciesUsage( new String[] { "undefined:undefined" } );
            fail( "failure expected since undefined dependency is not declared" );
        }
        catch ( ProjectDependencyAnalyzerException pdae )
        {
            assertTrue( pdae.getMessage().contains( "Trying to force use of dependencies which are "
                + "not declared: [undefined:undefined]" ) );
        }
    }

    @Test
    public void testJarWithTestDependency()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithTestDependency/pom.xml" );

        MavenProject project2 = getProject( "jarWithTestDependency/project2/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project2 );
        
        Artifact project1 = createArtifact( "org.apache.maven.shared.dependency-analyzer.tests",
                                            "jarWithTestDependency1", "jar", "1.0", "test" );
        Artifact junit = createArtifact( "junit", "junit", "jar", "3.8.1", "test" );

        ProjectDependencyAnalysis expectedAnalysis;
        if ( isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) )
        {
            Set<Artifact> usedDeclaredArtifacts = new HashSet<>( Arrays.asList( project1, junit ) );
            expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, (Set<Artifact>) null, null, null );
        }
        else
        {
            // With JDK 7 and earlier, not all deps are identified correctly
            Set<Artifact> usedDeclaredArtifacts = Collections.singleton( project1 );
            Set<Artifact> unusedDeclaredArtifacts = Collections.singleton( junit );
            expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, (Set<Artifact>) null, unusedDeclaredArtifacts,
                    null );
        }

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testJarWithXmlTransitiveDependency()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithXmlTransitiveDependency/pom.xml" );

        MavenProject project = getProject( "jarWithXmlTransitiveDependency/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        Artifact jdom = createArtifact( "dom4j", "dom4j", "jar", "1.6.1", "compile" );
        Set<Artifact> usedDeclaredArtifacts = Collections.singleton( jdom );

        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, (Set<Artifact>) null, null,
                null );

        // MSHARED-47: usedUndeclaredArtifacts=[xml-apis:xml-apis:jar:1.0.b2:compile]
        // assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testJarWithCompileScopedTestDependency()
            throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithCompileScopedTestDependency/pom.xml" );

        MavenProject project2 = getProject( "jarWithCompileScopedTestDependency/project2/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project2 );

        Artifact artifact1 = createArtifact( "org.apache.maven.shared.dependency-analyzer.tests",
                "jarWithCompileScopedTestDependency1", "jar", "1.0", "test" );
        Artifact junit = createArtifact( "junit", "junit", "jar", "3.8.1", "compile" );

        ProjectDependencyAnalysis expectedAnalysis;
        if ( isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) )
        {
            Set<Artifact> usedDeclaredArtifacts = new HashSet<>( Arrays.asList( artifact1, junit ) );
            Set<Artifact> nonTestScopedTestArtifacts = Collections.singleton( junit );
            expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, (Set<Artifact>) null, null,
                    nonTestScopedTestArtifacts );
        }
        else
        {
            // With JDK 7 and earlier, not all deps are identified correctly
            Set<Artifact> usedDeclaredArtifacts = Collections.singleton( artifact1 );
            Set<Artifact> unUsedDeclaredArtifacts = Collections.singleton( junit );
            expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, (Set<Artifact>) null, unUsedDeclaredArtifacts,
                    null );
        }

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testJarWithRuntimeScopedTestDependency() throws TestToolsException, ProjectDependencyAnalyzerException
    {
        // We can't effectively analyze runtime dependencies at this time
        compileProject( "jarWithRuntimeScopedTestDependency/pom.xml" );

        MavenProject project2 = getProject( "jarWithRuntimeScopedTestDependency/project2/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project2 );

        Artifact artifact1 = createArtifact( "org.apache.maven.shared.dependency-analyzer.tests",
                "jarWithRuntimeScopedTestDependency1", "jar", "1.0", "test" );
        Artifact junit = createArtifact( "junit", "junit", "jar", "3.8.1", "runtime" );

        ProjectDependencyAnalysis expectedAnalysis;
        if ( isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) )
        {
            Set<Artifact> usedDeclaredArtifacts = new HashSet<>( Arrays.asList( artifact1, junit ) );
            expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, (Set<Artifact>) null, null,
                    null );
        }
        else
        {
            // With JDK 7 and earlier, not all deps are identified correctly
            Set<Artifact> usedDeclaredArtifacts = Collections.singleton( artifact1 );
            Set<Artifact> unUsedDeclaredArtifacts = Collections.singleton( junit );
            expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, (Set<Artifact>) null, unUsedDeclaredArtifacts,
                    null );
        }

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testMultimoduleProject()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "multimoduleProject/pom.xml" );

        // difficult to create multi-module project with Maven 2.x, so here's hacky solution
        // to get a inter-module dependency
        MavenProject project = getProject( "multimoduleProject/module2/pom.xml" );
        @SuppressWarnings( "unchecked" )
        Set<Artifact> dependencyArtifacts = project.getArtifacts();
        for ( Artifact artifact : dependencyArtifacts )
        {
            if ( artifact.getArtifactId().equals( "test-module1" ) )
            {
                File dir = getTestFile( "target/test-classes/", "multimoduleProject/module1/target/classes/" );
                artifact.setFile( dir );
            }
        }

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        Artifact junit = createArtifact( "org.apache.maven.its.dependency", "test-module1", "jar", "1.0", "compile" );
        Set<Artifact> usedDeclaredArtifacts = Collections.singleton( junit );

        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, (Set<Artifact>) null, null,
                null );

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testTypeUseAnnotationDependency()
            throws TestToolsException, ProjectDependencyAnalyzerException
    {
        // java.lang.annotation.ElementType.TYPE_USE introduced with Java 1.8
        assumeTrue( SystemUtils.isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) );

        Properties properties = new Properties();
        properties.put( "maven.compiler.source", "1.8" );
        properties.put( "maven.compiler.target", "1.8" );
        compileProject( "typeUseAnnotationDependency/pom.xml", properties);

        MavenProject usage = getProject( "typeUseAnnotationDependency/usage/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( usage );

        Artifact annotation = createArtifact( "org.apache.maven.shared.dependency-analyzer.tests",
                                            "typeUseAnnotationDependencyAnnotation", "jar", "1.0", "compile" );
        Set<Artifact> usedDeclaredArtifacts = Collections.singleton( annotation );
        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis(usedDeclaredArtifacts, (Set<Artifact>) null, null,
                null );

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testTypeUseAnnotationDependencyOnLocalVariable()
            throws TestToolsException, ProjectDependencyAnalyzerException
    {
        // java.lang.annotation.ElementType.TYPE_USE introduced with Java 1.8
        assumeTrue( SystemUtils.isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) );

        Properties properties = new Properties();
        properties.put( "maven.compiler.source", "1.8" );
        properties.put( "maven.compiler.target", "1.8" );
        compileProject( "typeUseAnnotationDependency/pom.xml", properties);

        MavenProject usage = getProject( "typeUseAnnotationDependency/usageLocalVar/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( usage );

        Artifact annotation = createArtifact( "org.apache.maven.shared.dependency-analyzer.tests",
                                            "typeUseAnnotationDependencyAnnotation", "jar", "1.0", "compile" );
        Set<Artifact> usedDeclaredArtifacts = Collections.singleton( annotation );
        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis(usedDeclaredArtifacts, (Set<Artifact>) null, null,
                null);

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testUnnamedPackageClassReference()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        assumeTrue( SystemUtils.isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) );

        // Only visible through constant pool analysis (supported for JDK8+)
        compileProject( "unnamedPackageClassReference/pom.xml" );

        MavenProject project = getProject( "unnamedPackageClassReference/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        Artifact dnsjava = createArtifact( "dnsjava", "dnsjava", "jar", "2.1.8", "compile" );
        // we don't use any dnsjava classes so this should show up as an unused dep
        Set<Artifact> unusedDeclaredArtifacts = Collections.singleton( dnsjava );

        ProjectDependencyAnalysis expectedAnalysis =
            new ProjectDependencyAnalysis( new HashSet<Artifact>(), new HashSet<Artifact>(), unusedDeclaredArtifacts,
                new HashSet<Artifact>() );

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testJarWithClassInUnnamedPackage()
            throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithClassInUnnamedPackage/pom.xml" );

        MavenProject project2 = getProject( "jarWithClassInUnnamedPackage/project2/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project2 );

        Artifact project1 = createArtifact( "org.apache.maven.shared.dependency-analyzer.tests",
                                            "jarWithClassInUnnamedPackage1", "jar", "1.0", "compile" );
        Set<Artifact> unusedDeclaredArtifacts = Collections.singleton( project1 );
        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis( null, null, unusedDeclaredArtifacts );

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    @Test
    public void testUsedUndeclaredClassReference()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "usedUndeclaredReference/pom.xml" );

        MavenProject project = getProject( "usedUndeclaredReference/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        Artifact xmlApis = createArtifact( "xml-apis", "xml-apis", "jar", "1.0.b2", "compile" );
        Set<Artifact> expectedUsedUndeclaredArtifacts = Collections.singleton( xmlApis );

        assertEquals( expectedUsedUndeclaredArtifacts, actualAnalysis.getUsedUndeclaredArtifacts() );
    }

    // private methods --------------------------------------------------------

    private void compileProject( String pomPath )
        throws TestToolsException
    {
        compileProject( pomPath, new Properties() );
    }

    private void compileProject(String pomPath, Properties properties) throws TestToolsException {
        File pom = getTestFile( "target/test-classes/", pomPath );
        if ( isJavaVersionAtLeast( JavaVersion.JAVA_9 )
             && !properties.containsKey( "maven.compiler.source" ) )
        {
          properties.put( "maven.compiler.source", "1.7" );
          properties.put( "maven.compiler.target", "1.7" );
        }
        
        String httpsProtocols = System.getProperty( "https.protocols" );
        if ( httpsProtocols != null )
        {
            properties.put( "https.protocols", httpsProtocols );
        }

        List<String> goals = Arrays.asList( "clean", "install" );
        File log = new File( pom.getParentFile(), "build.log" );

        // TODO: don't install test artifacts to local repository
        InvocationRequest request = buildTool.createBasicInvocationRequest( pom, properties, goals, log );
        request.setLocalRepositoryDirectory( localRepo );
        InvocationResult result = buildTool.executeMaven( request );

        assertNull( "Error compiling test project", result.getExecutionException() );
        assertEquals( "Error compiling test project", 0, result.getExitCode() );
    }

    private MavenProject getProject( String pomPath )
        throws TestToolsException
    {
        File pom = getTestFile( "target/test-classes/", pomPath );

        return projectTool.readProjectWithDependencies( pom );
    }

    private Artifact createArtifact( String groupId, String artifactId, String type, String version, String scope )
    {
        VersionRange versionRange = VersionRange.createFromVersion( version );
        ArtifactHandler handler = new DefaultArtifactHandler();

        return new DefaultArtifact( groupId, artifactId, versionRange, scope, type, null, handler );
    }
}
