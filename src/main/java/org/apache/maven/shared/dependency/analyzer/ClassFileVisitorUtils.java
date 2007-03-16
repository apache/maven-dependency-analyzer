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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.codehaus.plexus.util.DirectoryScanner;

/**
 * 
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public final class ClassFileVisitorUtils
{
    // constants --------------------------------------------------------------

    private static final String[] CLASS_INCLUDES = { "**/*.class" };

    // constructors -----------------------------------------------------------

    private ClassFileVisitorUtils()
    {
        // private constructor for utility class
    }

    // public methods ---------------------------------------------------------

    public static void accept( URL url, ClassFileVisitor visitor )
        throws IOException
    {
        if ( url.getPath().endsWith( ".jar" ) )
        {
            acceptJar( url, visitor );
        }
        else if ( url.getProtocol().equals( "file" ) )
        {
            try
            {
                File file = new File( new URI( url.toString() ).getPath() );

                if ( file.isDirectory() )
                {
                    acceptDirectory( file, visitor );
                }
                else
                {
                    throw new IllegalArgumentException( "Cannot accept visitor on URL: " + url );
                }
            }
            catch ( URISyntaxException exception )
            {
                IllegalArgumentException e = new IllegalArgumentException( "Cannot accept visitor on URL: " + url );
                e.initCause( exception );
                throw e;
            }
        }
        else
        {
            throw new IllegalArgumentException( "Cannot accept visitor on URL: " + url );
        }
    }

    // private methods --------------------------------------------------------

    private static void acceptJar( URL url, ClassFileVisitor visitor )
        throws IOException
    {
        JarInputStream in = new JarInputStream( url.openStream() );

        JarEntry entry = null;

        while ( ( entry = in.getNextJarEntry() ) != null )
        {
            String name = entry.getName();

            if ( name.endsWith( ".class" ) )
                visitClass( name, in, visitor );
        }

        in.close();
    }

    private static void acceptDirectory( File directory, ClassFileVisitor visitor )
        throws IOException
    {
        if ( !directory.isDirectory() )
            throw new IllegalArgumentException( "File is not a directory" );

        DirectoryScanner scanner = new DirectoryScanner();

        scanner.setBasedir( directory );
        scanner.setIncludes( CLASS_INCLUDES );

        scanner.scan();

        String[] paths = scanner.getIncludedFiles();

        for ( int i = 0; i < paths.length; i++ )
        {
            String path = paths[i].replace( File.separatorChar, '/' );

            File file = new File( directory, path );
            FileInputStream in = new FileInputStream( file );

            visitClass( path, in, visitor );

            in.close();
        }
    }

    private static void visitClass( String path, InputStream in, ClassFileVisitor visitor )
    {
        if ( !path.endsWith( ".class" ) )
            throw new IllegalArgumentException( "Path is not a class" );

        String className = path.substring( 0, path.length() - 6 );

        className = className.replace( '/', '.' );

        visitor.visitClass( className, in );
    }
}
