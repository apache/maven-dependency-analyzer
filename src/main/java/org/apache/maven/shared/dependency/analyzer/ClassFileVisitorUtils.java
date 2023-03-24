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
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

/**
 * Utility to visit classes in a library given either as a jar file or an exploded directory.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public final class ClassFileVisitorUtils
{

    private ClassFileVisitorUtils()
    {
        // private constructor for utility class
    }

    /**
     *
     * @param url a {@link java.net.URL} object
     * @param visitor a {@link org.apache.maven.shared.dependency.analyzer.ClassFileVisitor} object
     * @throws java.io.IOException if any
     */
    public static void accept( URL url, ClassFileVisitor visitor )
        throws IOException
    {
        if ( url.getPath().endsWith( ".jar" ) )
        {
            acceptJar( url, visitor );
        }
        else if ( url.getProtocol().equalsIgnoreCase( "file" ) )
        {
            try
            {
                File file = new File( new URI( url.toString() ) );

                if ( file.isDirectory() )
                {
                    acceptDirectory( file, visitor );
                }
                else if ( file.exists() )
                {
                    throw new IllegalArgumentException( "Cannot accept visitor on URL: " + url );
                }
            }
            catch ( URISyntaxException exception )
            {
                throw new IllegalArgumentException( "Cannot accept visitor on URL: " + url,
                        exception );
            }
        }
        else
        {
            throw new IllegalArgumentException( "Cannot accept visitor on URL: " + url );
        }
    }

    private static void acceptJar( URL url, ClassFileVisitor visitor )
        throws IOException
    {
        try ( JarInputStream in = new JarInputStream( url.openStream() ) )
        {
            JarEntry entry;
            while ( ( entry = in.getNextJarEntry() ) != null )
            {
                String name = entry.getName();
                // ignore files like package-info.class and module-info.class
                if ( name.endsWith( ".class" ) && name.indexOf( '-' ) == -1 )
                {
                    // Even on Windows Jars use / as the separator character
                    visitClass( name, in, visitor, '/' );
                }
            }
        }
    }

    private static void acceptDirectory( File directory, ClassFileVisitor visitor )
        throws IOException
    {
    
        List<Path> classFiles = Files.walk( directory.toPath() )
            .filter( path -> path.getFileName().toString().endsWith( ".class" ) )
            .collect( Collectors.toList() );
            
        for ( Path path : classFiles )
        {
            try ( InputStream in = Files.newInputStream( path ) )
            {
                visitClass( directory, path, in, visitor );
            }
        }
    }

    private static void visitClass( File baseDirectory, Path path, InputStream in, ClassFileVisitor visitor )
    {
        // getPath() returns a String, not a java.nio.file.Path
        String stringPath = path.toFile().getPath().substring( baseDirectory.getPath().length() + 1 );
        visitClass( stringPath, in, visitor, File.separatorChar );
    }

    private static void visitClass( String stringPath, InputStream in, ClassFileVisitor visitor, char separator )
    {
        String className = stringPath.substring( 0, stringPath.length() - 6 );

        className = className.replace( separator, '.' );

        visitor.visitClass( className, in );
    }
}
