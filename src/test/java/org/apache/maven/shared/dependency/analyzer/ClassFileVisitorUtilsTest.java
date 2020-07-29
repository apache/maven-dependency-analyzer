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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * Tests <code>ClassFileVisitorUtils</code>.
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @see ClassFileVisitorUtils
 */
public class ClassFileVisitorUtilsTest
    extends AbstractFileTest
{
    
    private MockVisitor visitor = new MockVisitor();

    private static class MockVisitor implements ClassFileVisitor
    {
        
        ArrayList<String> classNames = new ArrayList<>();
        ArrayList<String> data = new ArrayList<>();

        @Override
        public void visitClass( String className, InputStream in )
        {
            classNames.add( className );
            try {
                List<String> lines = IOUtils.readLines( in, StandardCharsets.UTF_8 );
                data.addAll( lines );
            } catch (IOException ex) {
                throw new RuntimeException( ex );
            }
        }

    }

    public void testAcceptJar()
        throws IOException
    {
        File file = createJar();
        try ( JarOutputStream out = new JarOutputStream( new FileOutputStream( file ) ) )
        {
            writeEntry( out, "a/b/c.class", "class a.b.c" );
            writeEntry( out, "x/y/z.class", "class x.y.z" );
        }

        ClassFileVisitorUtils.accept( file.toURI().toURL(), visitor );
        
        assertEquals("a.b.c", visitor.classNames.get(0));
        assertEquals("x.y.z", visitor.classNames.get(1));
        assertEquals("class a.b.c", visitor.data.get(0));
        assertEquals("class x.y.z", visitor.data.get(1));
    }

    public void testAcceptJarWithNonClassEntry()
        throws IOException
    {
        File file = createJar();
        try ( JarOutputStream out = new JarOutputStream( new FileOutputStream( file ) ) )
        {
            writeEntry( out, "a/b/c.jpg", "jpeg a.b.c" );
        }

        ClassFileVisitorUtils.accept( file.toURI().toURL(), visitor );

        assertTrue(visitor.classNames.isEmpty());
    }

    public void testAcceptDir()
        throws IOException
    {
        File dir = createDir();

        File abDir = mkdirs( dir, "a/b" );
        createFile( abDir, "c.class", "class a.b.c" );

        File xyDir = mkdirs( dir, "x/y" );
        createFile( xyDir, "z.class", "class x.y.z" );

        ClassFileVisitorUtils.accept( dir.toURI().toURL(), visitor );

        FileUtils.deleteDirectory( dir );

        assertEquals("a.b.c", visitor.classNames.get(0));
        assertEquals("x.y.z", visitor.classNames.get(1));
        assertEquals("class a.b.c", visitor.data.get(0));
        assertEquals("class x.y.z", visitor.data.get(1));
    }

    public void testAcceptDirWithNonClassFile()
        throws IOException
    {
        File dir = createDir();

        File abDir = mkdirs( dir, "a/b" );
        createFile( abDir, "c.jpg", "jpeg a.b.c" );

        ClassFileVisitorUtils.accept( dir.toURI().toURL(), visitor );

        FileUtils.deleteDirectory( dir );

        assertTrue(visitor.classNames.isEmpty());
    }

    public void testAcceptWithFile()
        throws IOException
    {
        File file = File.createTempFile( "test", ".class" );
        file.deleteOnExit();

        URL url = file.toURI().toURL();

        try
        {
            ClassFileVisitorUtils.accept( url, visitor );
            fail("expected IllegalArgumntException");
        }
        catch ( IllegalArgumentException exception )
        {
            assertEquals( "Cannot accept visitor on URL: " + url, exception.getMessage() );
        }
    }

    public void testAcceptWithUnsupportedScheme()
        throws IOException
    {
        MockVisitor visitor = new MockVisitor();

        URL url = new URL( "http://localhost/" );

        try
        {
            ClassFileVisitorUtils.accept( url, visitor );
            fail("expected IllegalArgumntException");
        }
        catch ( IllegalArgumentException exception )
        {
            assertEquals( "Cannot accept visitor on URL: " + url, exception.getMessage() );
        }
    }
}
