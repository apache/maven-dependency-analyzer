/**
 * $HeadURL$
 *
 * (c) 2007 IIZUKA Software Technologies Ltd.  All rights reserved.
 */
package org.apache.maven.shared.dependency.analyzer;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.test.plugin.BuildTool;
import org.apache.maven.shared.test.plugin.ProjectTool;
import org.apache.maven.shared.test.plugin.TestToolsException;
import org.codehaus.plexus.PlexusTestCase;

/**
 * Tests <code>DefaultProjectDependencyAnalyzer</code>.
 *
 * @author	Mark Hobson
 * @version	$Id$
 * @see     DefaultProjectDependencyAnalyzer
 */
public class DefaultProjectDependencyAnalyzerTest extends PlexusTestCase
{
    // fields -----------------------------------------------------------------
    
    private BuildTool buildTool;
    
    private ProjectTool projectTool;

    private ProjectDependencyAnalyzer analyzer;

    // TestCase methods -------------------------------------------------------
    
    /*
     * @see org.codehaus.plexus.PlexusTestCase#setUp()
     */
    protected void setUp() throws Exception
    {
        super.setUp();
        
        buildTool = (BuildTool) lookup( BuildTool.ROLE );
        
        projectTool = (ProjectTool) lookup( ProjectTool.ROLE );

        analyzer = (ProjectDependencyAnalyzer) lookup( ProjectDependencyAnalyzer.ROLE );
    }

    // tests ------------------------------------------------------------------
    
    public void testJarWithNoDependencies() throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithNoDependencies/pom.xml" );
        
        MavenProject project = getProject( "jarWithNoDependencies/pom.xml" );
        
        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );
        
        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis();
        
        assertEquals( expectedAnalysis, actualAnalysis );
    }
    
    public void testJarWithCompileDependency() throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithCompileDependency/pom.xml" );
        
        MavenProject project2 = getProject( "jarWithCompileDependency/project2/pom.xml" );
        
        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project2 );
        
        Artifact project1 = createArtifact( "org.apache.maven.shared.dependency-analyzer.tests", "jarWithCompileDependency1", "jar", "1.0", "compile");
        Set usedDeclaredArtifacts = Collections.singleton( project1 );
        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, null, null );
        
        assertEquals( expectedAnalysis, actualAnalysis );
    }
    
    // private methods --------------------------------------------------------
    
    private void compileProject( String pomPath ) throws TestToolsException
    {
        File pom = getTestFile( "target/test-classes/", pomPath );
        Properties properties = new Properties();
        List goals = Arrays.asList( new String[] { "clean", "install" } );
        File log = new File(pom.getParentFile(), "build.log");

        InvocationResult result = buildTool.executeMaven( pom, properties, goals, log );
        assertNull( "Error compiling test project", result.getExecutionException() );
        assertEquals( "Error compiling test project", 0, result.getExitCode() );
    }
    
    private MavenProject getProject( String pomPath ) throws TestToolsException
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
