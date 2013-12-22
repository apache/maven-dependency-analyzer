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

import org.objectweb.asm.*;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.HashSet;
import java.util.Set;

/**
 * Computes the set of classes referenced by visited code.
 * Inspired by <code>org.objectweb.asm.depend.DependencyVisitor</code> in the ASM dependencies example.
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public class DefaultClassVisitor extends ClassVisitor
{
    // fields -----------------------------------------------------------------

    private final ResultCollector classes;

    private final SignatureVisitor signatureVisitor;

    private final AnnotationVisitor annotationVisitor;

    private final FieldVisitor fieldVisitor;

    private final MethodVisitor methodVisitor;

    // constructors -----------------------------------------------------------

    public DefaultClassVisitor(SignatureVisitor signatureVisitor, AnnotationVisitor annotationVisitor, FieldVisitor fieldVisitor, MethodVisitor methodVisitor, ResultCollector resultCollector)
    {
        super(Opcodes.ASM4);
        this.signatureVisitor = signatureVisitor;
        this.annotationVisitor = annotationVisitor;
        this.fieldVisitor = fieldVisitor;
        this.methodVisitor = methodVisitor;
        this.classes = resultCollector;
    }

    public void visit( final int version, final int access, final String name, final String signature,
                       final String superName, final String[] interfaces )
    {
        if ( signature == null )
        {
            addName( superName );
            addNames( interfaces );
        }
        else
        {
            addSignature( signature );
        }
    }

    /*
     * @see org.objectweb.asm.ClassVisitor#visitAnnotation(java.lang.String, boolean)
     */
    public AnnotationVisitor visitAnnotation( final String desc, final boolean visible )
    {
        addDesc( desc );
        
        return annotationVisitor;
    }

    /*
     * @see org.objectweb.asm.ClassVisitor#visitField(int, java.lang.String, java.lang.String, java.lang.String,
     *      java.lang.Object)
     */
    public FieldVisitor visitField( final int access, final String name, final String desc, final String signature,
                                    final Object value )
    {
        if ( signature == null )
        {
            addDesc( desc );
        }
        else
        {
            addTypeSignature( signature );
        }

        if ( value instanceof Type )
        {
            addType( (Type) value );
        }

        return fieldVisitor;
    }

    /*
     * @see org.objectweb.asm.ClassVisitor#visitMethod(int, java.lang.String, java.lang.String, java.lang.String,
     *      java.lang.String[])
     */
    public MethodVisitor visitMethod( final int access, final String name, final String desc, final String signature,
                                      final String[] exceptions )
    {
        if ( signature == null )
        {
            addMethodDesc( desc );
        }
        else
        {
            addSignature( signature );
        }

        addNames( exceptions );

        return methodVisitor;
    }


    // private methods --------------------------------------------------------

    private void addName( String name )
    {
        if ( name == null )
        {
            return;
        }

        // decode arrays
        if ( name.startsWith( "[L" ) && name.endsWith( ";" ) )
        {
            name = name.substring( 2, name.length() - 1 );
        }

        // decode internal representation
        name = name.replace( '/', '.' );

        classes.add( name );
    }

    private void addNames( final String[] names )
    {
        if ( names == null )
        {
            return;
        }

        for ( String name : names )
        {
            addName( name );
        }
    }

    private void addDesc( final String desc )
    {
        addType( Type.getType( desc ) );
    }

    private void addMethodDesc( final String desc )
    {
        addType( Type.getReturnType( desc ) );

        Type[] types = Type.getArgumentTypes( desc );

        for ( Type type : types )
        {
            addType( type );
        }
    }

    private void addType( final Type t )
    {
        switch ( t.getSort() )
        {
            case Type.ARRAY:
                addType( t.getElementType() );
                break;

            case Type.OBJECT:
                addName( t.getClassName().replace( '.', '/' ) );
                break;
        }
    }

    private void addSignature( final String signature )
    {
        if ( signature != null )
        {
            new SignatureReader( signature ).accept( signatureVisitor );
        }
    }

    private void addTypeSignature( final String signature )
    {
        if ( signature != null )
        {
            new SignatureReader( signature ).acceptType( signatureVisitor );
        }
    }
}
