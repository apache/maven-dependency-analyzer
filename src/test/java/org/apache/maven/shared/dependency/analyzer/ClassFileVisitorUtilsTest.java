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
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests <code>ClassFileVisitorUtils</code>.
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see ClassFileVisitorUtils
 */
public class ClassFileVisitorUtilsTest
{
    private MockVisitor visitor;

    private static class MockVisitor implements ClassFileVisitor
    {
        final List<String> classNames = new ArrayList<>();
        final List<String> data = new ArrayList<>();

        @Override
        public void visitClass( String className, InputStreamProvider provider )
        {
            classNames.add( className );
            try ( InputStream in = provider.open() )
            {
                List<String> lines = IOUtils.readLines( in, StandardCharsets.UTF_8 );
                data.addAll( lines );
            }
            catch ( IOException ex )
            {
                throw new RuntimeException( ex );
            }
        }
    }

    @BeforeEach
    public void setUp()
    {
        visitor = new MockVisitor();
    }

    @Test
    public void testAcceptJar() throws IOException
    {
        File file = File.createTempFile( "test", ".jar" );
        file.deleteOnExit();

        try ( JarOutputStream out = new JarOutputStream( Files.newOutputStream( file.toPath() ) ) )
        {
            addZipEntry( out, "a/b/c.class", "class a.b.c" );
            addZipEntry( out, "x/y/z.class", "class x.y.z" );
        }

        ClassFileVisitorUtils.accept( file.toURI().toURL(), visitor );

        assertThat( visitor.classNames ).contains( "a.b.c" );
        assertThat( visitor.classNames ).contains( "x.y.z" );
        assertThat( visitor.data ).contains( "class a.b.c" );
        assertThat( visitor.data ).contains( "class x.y.z" );
    }

    @Test
    public void testAcceptJarWithNonClassEntry() throws IOException
    {
        File file = File.createTempFile( "test", ".jar" );
        file.deleteOnExit();

        try ( JarOutputStream out = new JarOutputStream( Files.newOutputStream( file.toPath() ) ) )
        {
            addZipEntry( out, "a/b/c.jpg", "jpeg a.b.c" );
        }

        ClassFileVisitorUtils.accept( file.toURI().toURL(), visitor );

        assertThat( visitor.classNames ) .isEmpty();
    }

    @Test
    public void testAcceptDir() throws IOException
    {
        Path dir = Files.createTempDirectory( "d-a-test" );

        Path abDir = Files.createDirectories( dir.resolve( "a/b" ) );
        writeToFile( abDir, "c.class", "class a.b.c" );

        Path xyDir = Files.createDirectories( dir.resolve( "x/y" ) );
        writeToFile( xyDir, "z.class", "class x.y.z" );

        ClassFileVisitorUtils.accept( dir.toUri().toURL(), visitor );

        FileUtils.deleteDirectory( dir.toFile() );

        assertThat( visitor.classNames ).contains( "a.b.c" );
        assertThat( visitor.classNames ).contains( "x.y.z" );
        assertThat( visitor.data ).contains( "class a.b.c" );
        assertThat( visitor.data ).contains( "class x.y.z" );
    }

    @Test
    public void testAcceptDirWithNonClassFile() throws IOException
    {
        Path dir = Files.createTempDirectory( "d-a-test" );

        Path abDir = Files.createDirectories( dir.resolve( "a/b" ) );
        writeToFile( abDir, "c.jpg", "jpeg a.b.c" );

        ClassFileVisitorUtils.accept( dir.toUri().toURL(), visitor );

        FileUtils.deleteDirectory( dir.toFile() );

        assertThat( visitor.classNames ).isEmpty();
    }

    @Test
    public void testAcceptWithFile() throws IOException
    {
        File file = File.createTempFile( "test", ".class" );
        file.deleteOnExit();

        URL url = file.toURI().toURL();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> ClassFileVisitorUtils.accept( url, visitor ) );
        assertThat( exception ).hasMessage( "Cannot accept visitor on path: " + file );
    }

    @Test
    public void testAcceptWithUnsupportedScheme() throws IOException
    {
        URL url = new URL( "http://localhost/" );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> ClassFileVisitorUtils.accept( url, visitor ) );
        assertThat( exception ).hasMessage( "Cannot accept visitor on URL: " + url );
    }

    private void writeToFile( Path parent, String file, String data ) throws IOException
    {
        Files.write( parent.resolve( file ), data.getBytes( StandardCharsets.UTF_8 ) );
    }

    private void addZipEntry( JarOutputStream out, String fileName, String content ) throws IOException
    {
        out.putNextEntry( new ZipEntry( fileName ) );
        byte[] bytes = content.getBytes( StandardCharsets.UTF_8 );
        out.write( bytes, 0, bytes.length );
    }
}
