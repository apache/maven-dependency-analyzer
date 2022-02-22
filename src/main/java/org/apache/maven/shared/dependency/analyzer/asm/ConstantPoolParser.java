package org.apache.maven.shared.dependency.analyzer.asm;

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

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A small parser to read the constant pool directly, in case it contains references
 * ASM does not support.
 *
 * Adapted from http://stackoverflow.com/a/32278587/23691
 *
 * Constant pool types:
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4">JVM 9 Sepc</a>
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se10/html/jvms-4.html#jvms-4.4">JVM 10 Sepc</a>
 */
public class ConstantPoolParser
{
    /** Constant <code>HEAD=0xcafebabe</code> */
    public static final int HEAD = 0xcafebabe;

    // Constant pool types

    /** Constant <code>CONSTANT_UTF8=1</code> */
    public static final byte CONSTANT_UTF8 = 1;

    /** Constant <code>CONSTANT_INTEGER=3</code> */
    public static final byte CONSTANT_INTEGER = 3;

    /** Constant <code>CONSTANT_FLOAT=4</code> */
    public static final byte CONSTANT_FLOAT = 4;

    /** Constant <code>CONSTANT_LONG=5</code> */
    public static final byte CONSTANT_LONG = 5;

    /** Constant <code>CONSTANT_DOUBLE=6</code> */
    public static final byte CONSTANT_DOUBLE = 6;

    /** Constant <code>CONSTANT_CLASS=7</code> */
    public static final byte CONSTANT_CLASS = 7;

    /** Constant <code>CONSTANT_STRING=8</code> */
    public static final byte CONSTANT_STRING = 8;

    /** Constant <code>CONSTANT_FIELDREF=9</code> */
    public static final byte CONSTANT_FIELDREF = 9;

    /** Constant <code>CONSTANT_METHODREF=10</code> */
    public static final byte CONSTANT_METHODREF = 10;

    /** Constant <code>CONSTANT_INTERFACEMETHODREF=11</code> */
    public static final byte CONSTANT_INTERFACEMETHODREF = 11;

    /** Constant <code>CONSTANT_NAME_AND_TYPE=12</code> */
    public static final byte CONSTANT_NAME_AND_TYPE = 12;

    /** Constant <code>CONSTANT_METHODHANDLE=15</code> */
    public static final byte CONSTANT_METHODHANDLE = 15;

    /** Constant <code>CONSTANT_METHOD_TYPE=16</code> */
    public static final byte CONSTANT_METHOD_TYPE = 16;

    /** Constant <code>CONSTANT_INVOKE_DYNAMIC=18</code> */
    public static final byte CONSTANT_INVOKE_DYNAMIC = 18;

    /** Constant <code>CONSTANT_MODULE=19</code> */
    public static final byte CONSTANT_MODULE = 19;

    /** Constant <code>CONSTANT_PACKAGE=20</code> */
    public static final byte CONSTANT_PACKAGE = 20;

    private static final int OXF0 = 0xf0;

    private static final int OXE0 = 0xe0;

    private static final int OX3F = 0x3F;

    static Set<String> getConstantPoolClassReferences( byte[] b )
    {
        return parseConstantPoolClassReferences( ByteBuffer.wrap( b ) );
    }

    static Set<String> parseConstantPoolClassReferences( ByteBuffer buf )
    {
        if ( buf.order( ByteOrder.BIG_ENDIAN )
                .getInt() != HEAD )
        {
            return Collections.emptySet();
        }
        buf.getChar() ; buf.getChar(); // minor + ver
        Set<Integer> classes = new HashSet<>();
        Map<Integer, String> stringConstants = new HashMap<>();
        for ( int ix = 1, num = buf.getChar(); ix < num; ix++ )
        {
            byte tag = buf.get();
            switch ( tag )
            {
                default:
                    throw new RuntimeException( "Unknown constant pool type '" + tag + "'" );
                case CONSTANT_UTF8:
                    stringConstants.put( ix, decodeString( buf ) );
                    break;
                case CONSTANT_CLASS:
                    classes.add( (int) buf.getChar() );
                    break;
                case CONSTANT_METHOD_TYPE:
                    consumeMethodType( buf );
                    break;
                case CONSTANT_FIELDREF:
                case CONSTANT_METHODREF:
                case CONSTANT_INTERFACEMETHODREF:
                case CONSTANT_NAME_AND_TYPE:
                    consumeReference( buf );
                    break;
                case CONSTANT_INTEGER:
                    consumeInt( buf );
                    break;
                case CONSTANT_FLOAT:
                    consumeFloat( buf );
                    break;
                case CONSTANT_DOUBLE:
                    consumeDouble( buf );
                    ix++;
                    break;
                case CONSTANT_LONG:
                    consumeLong( buf );
                    ix++;
                    break;
                case CONSTANT_STRING:
                    consumeString( buf );
                    break;
                case CONSTANT_METHODHANDLE:
                    consumeMethodHandle( buf );
                    break;
                case CONSTANT_INVOKE_DYNAMIC:
                    consumeInvokeDynamic( buf );
                    break;
                case CONSTANT_MODULE:
                    consumeModule( buf );
                    break;
                case CONSTANT_PACKAGE:
                    consumePackage( buf );
                    break;
            }
        }
        Set<String> result = new HashSet<>();
        for ( Integer aClass : classes )
        {
            String className = stringConstants.get( aClass );

            // filter out things from unnamed package, probably a false-positive
            if ( isImportableClass( className ) )
            {
                result.add( className );
            }
        }
        return result;
    }

    private static String decodeString( ByteBuffer buf )
    {
        int size = buf.getChar();
        // Explicit cast for compatibility with covariant return type on JDK 9's ByteBuffer
        @SuppressWarnings( "RedundantCast" )
        int oldLimit = ( (Buffer) buf ).limit();
        ( (Buffer) buf ).limit( buf.position() + size );
        StringBuilder sb = new StringBuilder( size + ( size >> 1 ) + 16 );
        while ( buf.hasRemaining() )
        {
            byte b = buf.get();
            if ( b > 0 )
            {
                sb.append( (char) b );
            }
            else
            {
                int b2 = buf.get();
                if ( ( b & OXF0 ) != OXE0 )
                {
                    sb.append( (char) ( ( b & 0x1F ) << 6 | b2 & OX3F ) );
                }
                else
                {
                    int b3 = buf.get();
                    sb.append( (char) ( ( b & 0x0F ) << 12 | ( b2 & OX3F ) << 6 | b3 & OX3F ) );
                }
            }
        }
        ( (Buffer) buf ).limit( oldLimit );
        return sb.toString();
    }

    private static boolean isImportableClass( String className )
    {
        // without a slash, class must be in unnamed package, which can't be imported
        return className.indexOf( '/' ) != -1;
    }

    private static void consumeMethodType( ByteBuffer buf )
    {
        buf.getChar();
    }

    private static void consumeReference( ByteBuffer buf )
    {
        buf.getChar();
        buf.getChar();
    }

    private static void consumeInt( ByteBuffer buf )
    {
        buf.getInt();
    }

    private static void consumeFloat( ByteBuffer buf )
    {
        buf.getFloat();
    }

    private static void consumeDouble( ByteBuffer buf )
    {
        buf.getDouble();
    }

    private static void consumeLong( ByteBuffer buf )
    {
        buf.getLong();
    }

    private static void consumeString( ByteBuffer buf )
    {
        buf.getChar();
    }

    private static void consumeMethodHandle( ByteBuffer buf )
    {
        buf.get();
        buf.getChar();
    }

    private static void consumeInvokeDynamic( ByteBuffer buf )
    {
        buf.getChar();
        buf.getChar();
    }

    private static void consumeModule( ByteBuffer buf )
    {
        buf.getChar();
    }

    private static void consumePackage( ByteBuffer buf )
    {
        buf.getChar();
    }
}
