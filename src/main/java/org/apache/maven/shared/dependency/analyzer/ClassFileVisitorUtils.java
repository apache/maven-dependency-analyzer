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
import java.io.FilterInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipFile;

import org.apache.maven.shared.dependency.analyzer.ClassFileVisitor.InputStreamProvider;
import org.codehaus.plexus.util.DirectoryScanner;

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
     * <p>accept.</p>
     *
     * @param url a {@link java.net.URL} object.
     * @param visitor a {@link org.apache.maven.shared.dependency.analyzer.ClassFileVisitor} object.
     * @throws java.io.IOException if any.
     */
    public static void accept( URL url, ClassFileVisitor visitor )
            throws IOException
    {
        if ( url.getProtocol().equals( "file" ) )
        {
            try
            {
                Path path = Paths.get( url.toURI() );
                accept( path, visitor );
            }
            catch ( URISyntaxException exception )
            {
                throw new IllegalArgumentException( "Cannot accept visitor on URL: " + url, exception );
            }
        }
        else if ( url.getPath().endsWith( ".jar" ) )
        {
            acceptJarUrl( url, visitor );
        }
        else
        {
            throw new IllegalArgumentException( "Cannot accept visitor on URL: " + url );
        }
    }

    /**
     * <p>accept.</p>
     *
     * @param path a {@link Path} object.
     * @param visitor a {@link org.apache.maven.shared.dependency.analyzer.ClassFileVisitor} object.
     * @throws java.io.IOException if any.
     */
    public static void accept( Path path, ClassFileVisitor visitor )
            throws IOException
    {
        if ( Files.isDirectory( path ) )
        {
            acceptDirectory( path, visitor );
        }
        else if ( Files.isRegularFile( path ) && path.toString().endsWith( ".jar" ) )
        {
            acceptJarFile( path, visitor );
        }
        else if ( Files.exists( path ) )
        {
            throw new IllegalArgumentException( "Cannot accept visitor on path: " + path );
        }
    }

    // private methods --------------------------------------------------------

    private static void acceptJarFile( Path file, ClassFileVisitor visitor )
            throws IOException
    {
        try ( ZipFile zip = new ZipFile( file.toFile() ) )
        {
            zip.stream().forEach( entry ->
            {
                String name = entry.getName();
                // ignore files like package-info.class and module-info.class
                if ( name.endsWith( ".class" ) && name.indexOf( '-' ) == -1 )
                {
                    visitClass( name, () -> zip.getInputStream( entry ), visitor );
                }
            } );
        }
        catch ( Exception e )
        {
            throw new ProjectDependencyAnalyzerException( "Cannot process jar entry on path: " + file, e );
        }
    }

    private static void acceptJarUrl( URL url, ClassFileVisitor visitor )
            throws IOException
    {
        try ( JarInputStream jis = new JarInputStream( url.openStream() ) )
        {
            JarEntry entry;
            while ( ( entry = jis.getNextJarEntry() ) != null )
            {
                String name = entry.getName();
                // ignore files like package-info.class and module-info.class
                if ( name.endsWith( ".class" ) && name.indexOf( '-' ) == -1 )
                {
                    visitClass( name, () -> new FilterInputStream( jis )
                    {
                        @Override
                        public void close() throws IOException
                        {
                            jis.closeEntry();
                        }
                    }, visitor );
                }
            }
        }
        catch ( Exception e )
        {
            throw new ProjectDependencyAnalyzerException( "Cannot process jar entry on url: " + url, e );
        }
    }

    private static void acceptDirectory( Path directory, ClassFileVisitor visitor )
    {
        if ( !Files.isDirectory( directory ) )
        {
            throw new IllegalArgumentException( "File is not a directory" );
        }

        try
        {
            DirectoryScanner scanner = new DirectoryScanner();

            scanner.setBasedir( directory.toFile() );
            scanner.setIncludes( new String[] {"**/*.class"} );

            scanner.scan();

            String[] paths = scanner.getIncludedFiles();

            for ( String path : paths )
            {
                String normpath = path.replace( File.separatorChar, '/' );
                visitClass( normpath, () -> Files.newInputStream( directory.resolve( normpath ) ), visitor );
            }
        }
        catch ( Exception e )
        {
            throw new ProjectDependencyAnalyzerException( "Cannot process directory on path: " + directory, e );
        }
    }

    private static void visitClass( String path, InputStreamProvider in, ClassFileVisitor visitor )
    {
        if ( !path.endsWith( ".class" ) )
        {
            throw new IllegalArgumentException( "Path is not a class" );
        }

        String className = path.substring( 0, path.length() - 6 );

        className = className.replace( '/', '.' );

        visitor.visitClass( className, in );
    }
}
