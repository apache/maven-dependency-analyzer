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

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

/**
 * Inspired by <code>org.objectweb.asm.depend.DependencyVisitor</code> in the ASM dependencies example.
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public class DependencyVisitor
    implements AnnotationVisitor, SignatureVisitor, ClassVisitor, FieldVisitor, MethodVisitor
{
    // fields -----------------------------------------------------------------

    private final Set classes;

    // constructors -----------------------------------------------------------

    public DependencyVisitor()
    {
        classes = new HashSet();
    }

    // ClassVisitor methods ---------------------------------------------------

    /*
     * @see org.objectweb.asm.ClassVisitor#visit(int, int, java.lang.String, java.lang.String, java.lang.String,
     *      java.lang.String[])
     */
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
     * @see org.objectweb.asm.ClassVisitor#visitSource(java.lang.String, java.lang.String)
     */
    public void visitSource( final String source, final String debug )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.ClassVisitor#visitOuterClass(java.lang.String, java.lang.String, java.lang.String)
     */
    public void visitOuterClass( final String owner, final String name, final String desc )
    {
        // addName(owner);
        // addMethodDesc(desc);
    }

    /*
     * @see org.objectweb.asm.ClassVisitor#visitAnnotation(java.lang.String, boolean)
     */
    public AnnotationVisitor visitAnnotation( final String desc, final boolean visible )
    {
        addDesc( desc );
        
        return this;
    }

    /*
     * @see org.objectweb.asm.ClassVisitor#visitAttribute(org.objectweb.asm.Attribute)
     */
    public void visitAttribute( final Attribute attr )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.ClassVisitor#visitInnerClass(java.lang.String, java.lang.String, java.lang.String, int)
     */
    public void visitInnerClass( final String name, final String outerName, final String innerName, final int access )
    {
        // addName( outerName);
        // addName( innerName);
    }

    /*
     * @see org.objectweb.asm.ClassVisitor#visitField(int, java.lang.String, java.lang.String, java.lang.String,
     *      java.lang.Object)
     */
    public FieldVisitor visitField( final int access, final String name, final String desc, final String signature,
                                    final Object value )
    {
        if ( signature == null )
            addDesc( desc );
        else
            addTypeSignature( signature );

        if ( value instanceof Type )
            addType( (Type) value );

        return this;
    }

    /*
     * @see org.objectweb.asm.ClassVisitor#visitMethod(int, java.lang.String, java.lang.String, java.lang.String,
     *      java.lang.String[])
     */
    public MethodVisitor visitMethod( final int access, final String name, final String desc, final String signature,
                                      final String[] exceptions )
    {
        if ( signature == null )
            addMethodDesc( desc );
        else
            addSignature( signature );

        addNames( exceptions );

        return this;
    }

    // MethodVisitor methods --------------------------------------------------

    /*
     * @see org.objectweb.asm.MethodVisitor#visitAnnotationDefault()
     */
    public AnnotationVisitor visitAnnotationDefault()
    {
        return this;
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitParameterAnnotation(int, java.lang.String, boolean)
     */
    public AnnotationVisitor visitParameterAnnotation( final int parameter, final String desc, final boolean visible )
    {
        addDesc( desc );

        return this;
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitCode()
     */
    public void visitCode()
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitFrame(int, int, java.lang.Object[], int, java.lang.Object[])
     */
    public void visitFrame( final int type, final int nLocal, final Object[] local, final int nStack,
                            final Object[] stack )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitInsn(int)
     */
    public void visitInsn( final int opcode )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitIntInsn(int, int)
     */
    public void visitIntInsn( final int opcode, final int operand )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitVarInsn(int, int)
     */
    public void visitVarInsn( final int opcode, final int var )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitTypeInsn(int, java.lang.String)
     */
    public void visitTypeInsn( final int opcode, final String desc )
    {
        if ( desc.charAt( 0 ) == '[' )
            addDesc( desc );
        else
            addName( desc );
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitFieldInsn(int, java.lang.String, java.lang.String, java.lang.String)
     */
    public void visitFieldInsn( final int opcode, final String owner, final String name, final String desc )
    {
        addName( owner );
        addDesc( desc );
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitMethodInsn(int, java.lang.String, java.lang.String, java.lang.String)
     */
    public void visitMethodInsn( final int opcode, final String owner, final String name, final String desc )
    {
        addName( owner );
        addMethodDesc( desc );
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitJumpInsn(int, org.objectweb.asm.Label)
     */
    public void visitJumpInsn( final int opcode, final Label label )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitLabel(org.objectweb.asm.Label)
     */
    public void visitLabel( final Label label )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitLdcInsn(java.lang.Object)
     */
    public void visitLdcInsn( final Object cst )
    {
        if ( cst instanceof Type )
            addType( (Type) cst );
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitIincInsn(int, int)
     */
    public void visitIincInsn( final int var, final int increment )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitTableSwitchInsn(int, int, org.objectweb.asm.Label,
     *      org.objectweb.asm.Label[])
     */
    public void visitTableSwitchInsn( final int min, final int max, final Label dflt, final Label[] labels )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitLookupSwitchInsn(org.objectweb.asm.Label, int[],
     *      org.objectweb.asm.Label[])
     */
    public void visitLookupSwitchInsn( final Label dflt, final int[] keys, final Label[] labels )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitMultiANewArrayInsn(java.lang.String, int)
     */
    public void visitMultiANewArrayInsn( final String desc, final int dims )
    {
        addDesc( desc );
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitTryCatchBlock(org.objectweb.asm.Label, org.objectweb.asm.Label,
     *      org.objectweb.asm.Label, java.lang.String)
     */
    public void visitTryCatchBlock( final Label start, final Label end, final Label handler, final String type )
    {
        addName( type );
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitLocalVariable(java.lang.String, java.lang.String, java.lang.String,
     *      org.objectweb.asm.Label, org.objectweb.asm.Label, int)
     */
    public void visitLocalVariable( final String name, final String desc, final String signature, final Label start,
                                    final Label end, final int index )
    {
        if ( signature == null )
        {
            addDesc( desc );
        }
        else
        {
            addTypeSignature( signature );
        }
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitLineNumber(int, org.objectweb.asm.Label)
     */
    public void visitLineNumber( final int line, final Label start )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.MethodVisitor#visitMaxs(int, int)
     */
    public void visitMaxs( final int maxStack, final int maxLocals )
    {
        // no-op
    }

    // AnnotationVisitor methods ----------------------------------------------

    /*
     * @see org.objectweb.asm.AnnotationVisitor#visit(java.lang.String, java.lang.Object)
     */
    public void visit( final String name, final Object value )
    {
        if ( value instanceof Type )
            addType( (Type) value );
    }

    /*
     * @see org.objectweb.asm.AnnotationVisitor#visitEnum(java.lang.String, java.lang.String, java.lang.String)
     */
    public void visitEnum( final String name, final String desc, final String value )
    {
        addDesc( desc );
    }

    /*
     * @see org.objectweb.asm.AnnotationVisitor#visitAnnotation(java.lang.String, java.lang.String)
     */
    public AnnotationVisitor visitAnnotation( final String name, final String desc )
    {
        addDesc( desc );

        return this;
    }

    /*
     * @see org.objectweb.asm.AnnotationVisitor#visitArray(java.lang.String)
     */
    public AnnotationVisitor visitArray( final String name )
    {
        return this;
    }

    /*
     * @see org.objectweb.asm.AnnotationVisitor#visitEnd()
     */
    public void visitEnd()
    {
        // no-op
    }

    // SignatureVisitor methods -----------------------------------------------

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitFormalTypeParameter(java.lang.String)
     */
    public void visitFormalTypeParameter( final String name )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitClassBound()
     */
    public SignatureVisitor visitClassBound()
    {
        return this;
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitInterfaceBound()
     */
    public SignatureVisitor visitInterfaceBound()
    {
        return this;
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitSuperclass()
     */
    public SignatureVisitor visitSuperclass()
    {
        return this;
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitInterface()
     */
    public SignatureVisitor visitInterface()
    {
        return this;
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitParameterType()
     */
    public SignatureVisitor visitParameterType()
    {
        return this;
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitReturnType()
     */
    public SignatureVisitor visitReturnType()
    {
        return this;
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitExceptionType()
     */
    public SignatureVisitor visitExceptionType()
    {
        return this;
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitBaseType(char)
     */
    public void visitBaseType( final char descriptor )
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitTypeVariable(java.lang.String)
     */
    public void visitTypeVariable( final String name )
    {
        // TODO: verify
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitArrayType()
     */
    public SignatureVisitor visitArrayType()
    {
        return this;
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitClassType(java.lang.String)
     */
    public void visitClassType( final String name )
    {
        addName( name );
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitInnerClassType(java.lang.String)
     */
    public void visitInnerClassType( final String name )
    {
        addName( name );
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitTypeArgument()
     */
    public void visitTypeArgument()
    {
        // no-op
    }

    /*
     * @see org.objectweb.asm.signature.SignatureVisitor#visitTypeArgument(char)
     */
    public SignatureVisitor visitTypeArgument( final char wildcard )
    {
        return this;
    }

    // public methods ---------------------------------------------------------

    public Set getClasses()
    {
        return classes;
    }

    // private methods --------------------------------------------------------

    private void addName( String name )
    {
        if ( name == null )
            return;

        // decode arrays
        if ( name.startsWith( "[L" ) && name.endsWith( ";" ) )
            name = name.substring( 2, name.length() - 1 );

        // decode internal representation
        name = name.replace( '/', '.' );

        classes.add( name );
    }

    private void addNames( final String[] names )
    {
        if ( names == null )
            return;
        
        for ( int i = 0; i < names.length; i++ )
            addName( names[i] );
    }

    private void addDesc( final String desc )
    {
        addType( Type.getType( desc ) );
    }

    private void addMethodDesc( final String desc )
    {
        addType( Type.getReturnType( desc ) );
        
        Type[] types = Type.getArgumentTypes( desc );

        for ( int i = 0; i < types.length; i++ )
            addType( types[i] );
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
            new SignatureReader( signature ).accept( this );
    }

    private void addTypeSignature( final String signature )
    {
        if ( signature != null )
            new SignatureReader( signature ).acceptType( this );
    }
}
