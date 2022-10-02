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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import org.apache.maven.shared.dependency.analyzer.asm.ASMDependencyAnalyzer;
import org.codehaus.plexus.util.IOUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests <code>DefaultClassAnalyzer</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see DefaultClassAnalyzer
 */
public class DefaultClassAnalyzerTest
{
    private Path file;

    @BeforeEach
    public void setUp() throws IOException
    {
        file = Files.createTempFile( "test", ".jar" );
        try ( JarOutputStream out = new JarOutputStream( new FileOutputStream( file.toFile() ) ) )
        {
            addZipEntry( out, "a/b/c.class", "class a.b.c" );
            addZipEntry( out, "x/y/z.class", "class x.y.z" );
        }
    }

    @AfterEach
    public void cleanup() throws IOException
    {
        if ( file != null )
        {
            Files.deleteIfExists( file );
        }
    }

    @Test
    public void testAnalyzeWithJar() throws IOException
    {
        Set<String> expectedClasses = new HashSet<>();
        expectedClasses.add( "a.b.c" );
        expectedClasses.add( "x.y.z" );

        DefaultClassAnalyzer analyzer = new DefaultClassAnalyzer();
        Set<String> actualClasses = analyzer.analyze( file.toUri().toURL() );

        assertThat( actualClasses ).isEqualTo( expectedClasses );
    }

    @Test
    public void testAnalyzeBadJar() throws IOException
    {
        //to reproduce MDEP-143
        // corrupt the jar file by altering its contents
        ByteArrayOutputStream baos = new ByteArrayOutputStream( 100 );
        Files.copy( file, baos );
        byte[] ba = baos.toByteArray();
        ba[49] = 1;
        Files.copy( new ByteArrayInputStream( ba ), file, StandardCopyOption.REPLACE_EXISTING );

        DependencyAnalyzer analyzer = new ASMDependencyAnalyzer();

        ProjectDependencyAnalyzerException exception = assertThrows( ProjectDependencyAnalyzerException.class, () -> analyzer.analyze( file.toUri().toURL() ) );
        assertThat( exception ).hasMessageStartingWith( "Cannot process jar entry on path:" );
        assertThat( exception.getCause() ).isInstanceOf( ProjectDependencyAnalyzerException.class )
                .hasMessageStartingWith( "Unable to process class " );
        assertThat( exception.getCause().getCause() ).isInstanceOf( ZipException.class );
    }

    private void addZipEntry( JarOutputStream out, String fileName, String content ) throws IOException
    {
        out.putNextEntry( new ZipEntry( fileName ) );
        byte[] bytes = content.getBytes( StandardCharsets.UTF_8 );
        out.write( bytes, 0, bytes.length );
    }
}
