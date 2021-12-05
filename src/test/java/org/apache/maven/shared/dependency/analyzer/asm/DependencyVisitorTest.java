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

import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.signature.SignatureVisitor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests <code>DependencyVisitor</code>.
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public class DependencyVisitorTest
{
    private final ResultCollector resultCollector = new ResultCollector();
    private DefaultClassVisitor visitor;
    private MethodVisitor mv;

    @Before
    public void setUp()
    {
        AnnotationVisitor annotationVisitor = new DefaultAnnotationVisitor( resultCollector );
        SignatureVisitor signatureVisitor = new DefaultSignatureVisitor( resultCollector );
        FieldVisitor fieldVisitor = new DefaultFieldVisitor( annotationVisitor, resultCollector );
        mv = new DefaultMethodVisitor( annotationVisitor, signatureVisitor, resultCollector);
        visitor = new DefaultClassVisitor( signatureVisitor, annotationVisitor, fieldVisitor, mv, resultCollector);
    }

    @Test
    public void testVisitWithDefaultSuperclass()
    {
        // class a.b.c
        visitor.visit( 50, 0, "a/b/c", null, "java/lang/Object", null );

        assertThat( resultCollector.getDependencies() ).containsOnly( "java.lang.Object" );
    }

    @Test
    public void testVisitWithSuperclass()
    {
        // class a.b.c
        visitor.visit( 50, 0, "a/b/c", null, "x/y/z", null );

        assertThat( resultCollector.getDependencies() ).containsOnly( "x.y.z" );
    }

    @Test
    public void testVisitWithInterface()
    {
        // class a.b.c implements x.y.z
        visitor.visit( 50, 0, "a/b/c", null, "java/lang/Object", new String[] { "x/y/z" } );

        assertThat( resultCollector.getDependencies() ).containsOnly( "java.lang.Object", "x.y.z" );
    }

    @Test
    public void testVisitWithInterfaces()
    {
        // class a.b.c implements p.q.r, x.y.z
        visitor.visit( 50, 0, "a/b/c", null, "java/lang/Object", new String[] { "p/q/r", "x/y/z" } );

        assertThat( resultCollector.getDependencies() ).containsOnly( "java.lang.Object", "p.q.r", "x.y.z" );
    }

    @Test
    public void testVisitWithUnboundedClassTypeParameter()
    {
        // class a.b.c<T>
        String signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", null );

        assertThat( resultCollector.getDependencies() ).containsOnly( "java.lang.Object" );
    }

    @Test
    public void testVisitWithBoundedClassTypeParameter()
    {
        // class a.b.c<T extends x.y.z>
        String signature = "<T:Lx/y/z;>Ljava/lang/Object;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", null );

        assertThat( resultCollector.getDependencies() ).containsOnly( "java.lang.Object", "x.y.z" );
    }

    @Test
    public void testVisitWithBoundedClassTypeParameters()
    {
        // class a.b.c<K extends p.q.r, V extends x.y.z>
        String signature = "<K:Lp/q/r;V:Lx/y/z;>Ljava/lang/Object;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", null );

        assertThat( resultCollector.getDependencies() ).containsOnly( "java.lang.Object", "p.q.r", "x.y.z" );
    }

    @Test
    public void testVisitWithGenericInterface()
    {
        // class a.b.c implements p.q.r<x.y.z>
        String signature = "Ljava/lang/Object;Lp/q/r<Lx/y/z;>;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", new String[] { "p.q.r" } );

        assertThat( resultCollector.getDependencies() ).containsOnly( "java.lang.Object", "p.q.r", "x.y.z" );
    }

    @Test
    public void testVisitWithInterfaceBound()
    {
        // class a.b.c<T> implements x.y.z<T>
        String signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;Lx/y/z<TT;>;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", new String[] { "x.y.z" } );

        assertThat( resultCollector.getDependencies() ).containsOnly( "java.lang.Object", "x.y.z" );
    }

    // visitSource tests ------------------------------------------------------

    @Test
    public void testVisitSource()
    {
        visitor.visitSource( null, null );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitOuterClass tests --------------------------------------------------

    @Test
    public void testVisitOuterClass()
    {
        // class a.b.c
        // {
        //     class ...
        //     {
        //     }
        // }
        visitor.visitOuterClass( "a/b/c", null, null );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    @Test
    public void testVisitOuterClassInMethod()
    {
        // class a.b.c
        // {
        //     x.y.z x(p.q.r p)
        //     {
        //         class ...
        //         {
        //         }
        //     }
        // }
        visitor.visitOuterClass( "a/b/c", "x", "(Lp/q/r;)Lx/y/z;" );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitAnnotation tests --------------------------------------------------

    @Test
    public void testVisitAnnotation()
    {
        assertVisitor( visitor.visitAnnotation( "La/b/c;", false ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitAnnotationWithRuntimeVisibility()
    {
        assertVisitor( visitor.visitAnnotation( "La/b/c;", true ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    // visitAttribute tests ---------------------------------------------------

    @Test
    public void testVisitAttribute()
    {
        visitor.visitAttribute( new MockAttribute( "a" ) );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitInnerClass tests --------------------------------------------------

    @Test
    public void testVisitInnerClass()
    {
        // TODO: ensure innerName is correct

        // class a.b.c { class x.y.z { } }
        visitor.visitInnerClass( "x/y/z", "a/b/c", "z", 0 );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    @Test
    public void testVisitInnerClassAnonymous()
    {
        // class a.b.c { new class x.y.z { } }
        visitor.visitInnerClass( "x/y/z$1", "a/b/c", null, 0 );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitField tests -------------------------------------------------------

    @Test
    public void testVisitField()
    {
        // a.b.c a
        assertVisitor( visitor.visitField( 0, "a", "La/b/c;", null, null ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    // TODO: determine actual use of default values
    // public void testVisitFieldWithValue()
    // {
    // }

    @Test
    public void testVisitFieldArray()
    {
        // a.b.c[] a
        assertVisitor( visitor.visitField( 0, "a", "[La/b/c;", null, null ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitFieldGeneric()
    {
        // a.b.c<x.y.z> a
        assertVisitor( visitor.visitField( 0, "a", "La/b/c;", "La/b/c<Lx/y/z;>;", null ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c", "x.y.z" );
    }

    // visitMethod tests ------------------------------------------------------

    @Test
    public void testVisitMethod()
    {
        // void a()
        assertVisitor( visitor.visitMethod( 0, "a", "()V", null, null ) );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    @Test
    public void testVisitMethodWithPrimitiveArgument()
    {
        // void a(int)
        assertVisitor( visitor.visitMethod( 0, "a", "(I)V", null, null ) );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    @Test
    public void testVisitMethodWithPrimitiveArrayArgument()
    {
        // void a(int[])
        assertVisitor( visitor.visitMethod( 0, "a", "([I)V", null, null ) );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    @Test
    public void testVisitMethodWithObjectArgument()
    {
        // void a(a.b.c)
        assertVisitor( visitor.visitMethod( 0, "a", "(La/b/c;)V", null, null ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodWithObjectArguments()
    {
        // void a(a.b.c, x.y.z)
        assertVisitor( visitor.visitMethod( 0, "a", "(La/b/c;Lx/y/z;)V", null, null ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c", "x.y.z" );
    }

    @Test
    public void testVisitMethodWithObjectArrayArgument()
    {
        // void a(a.b.c[])
        assertVisitor( visitor.visitMethod( 0, "a", "([La/b/c;)V", null, null ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodWithGenericArgument()
    {
        // void a(a.b.c<x.y.z>)
        assertVisitor( visitor.visitMethod( 0, "a", "(La/b/c;)V", "(La/b/c<Lx/y/z;>;)V", null ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c", "x.y.z" );
    }

    @Test
    public void testVisitMethodWithPrimitiveReturnType()
    {
        // int a()
        assertVisitor( visitor.visitMethod( 0, "a", "()I", null, null ) );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    @Test
    public void testVisitMethodWithPrimitiveArrayReturnType()
    {
        // int[] a()
        assertVisitor( visitor.visitMethod( 0, "a", "()[I", null, null ) );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    @Test
    public void testVisitMethodWithObjectReturnType()
    {
        // a.b.c a()
        assertVisitor( visitor.visitMethod( 0, "a", "()La/b/c;", null, null ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodWithObjectArrayReturnType()
    {
        // a.b.c[] a()
        assertVisitor( visitor.visitMethod( 0, "a", "()[La/b/c;", null, null ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodWithException()
    {
        // void a() throws a.b.c
        assertVisitor( visitor.visitMethod( 0, "a", "()V", null, new String[] { "a/b/c" } ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodWithExceptions()
    {
        // void a() throws a.b.c, x.y.z
        assertVisitor( visitor.visitMethod( 0, "a", "()V", null, new String[] { "a/b/c", "x/y/z" } ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c", "x.y.z" );
    }

    // visitAnnotationDefault tests -------------------------------------------

    @Test
    public void testVisitAnnotationDefault()
    {
        assertVisitor( mv.visitAnnotationDefault() );
        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitParameterAnnotation tests -------------------------------------------

    @Test
    public void testVisitParameterAnnotation()
    {
        // @a.b.c
        assertVisitor( mv.visitParameterAnnotation( 0, "La/b/c;", false ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    // visitCode tests --------------------------------------------------------

    @Test
    public void testVisitCode()
    {
        mv.visitCode();

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitFrame tests -------------------------------------------------------

    @Test
    public void testVisitFrame()
    {
        mv.visitFrame( Opcodes.F_NEW, 0, new Object[0], 0, new Object[0] );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitInsn tests --------------------------------------------------------

    @Test
    public void testVisitInsn()
    {
        mv.visitInsn( Opcodes.NOP );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitIntInsn tests -----------------------------------------------------

    @Test
    public void testVisitIntInsn()
    {
        mv.visitIntInsn( Opcodes.BIPUSH, 0 );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitVarInsn tests -----------------------------------------------------

    @Test
    public void testVisitVarInsn()
    {
        mv.visitVarInsn( Opcodes.ILOAD, 0 );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitTypeInsn tests ----------------------------------------------------

    @Test
    public void testVisitTypeInsn()
    {
        mv.visitTypeInsn( Opcodes.NEW, "a/b/c" );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    // visitFieldInsn tests ---------------------------------------------------

    @Test
    public void testVisitFieldInsnWithPrimitive()
    {
        mv.visitFieldInsn( Opcodes.GETFIELD, "a/b/c", "x", "I" );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitFieldInsnWithObject()
    {
        mv.visitFieldInsn( Opcodes.GETFIELD, "a/b/c", "x", "Lx/y/z;" );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    // visitMethodInsn tests --------------------------------------------------

    @Test
    public void testVisitMethodInsn()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()V", false );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithPrimitiveArgument()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(I)V", false );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithPrimitiveArrayArgument()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "([I)V", false );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithObjectArgument()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(Lx/y/z;)V", false );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithObjectArguments()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(Lp/q/r;Lx/y/z;)V", false );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithObjectArrayArgument()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "([Lx/y/z;)V", false );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithPrimitiveReturnType()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()I", false );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithPrimitiveArrayReturnType()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()[I", false );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithObjectReturnType()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()Lx/y/z;", false );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithObjectArrayReturnType()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()[Lx/y/z;", false );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    // visitJumpInsn tests ----------------------------------------------------

    @Test
    public void testVisitJumpInsn()
    {
        mv.visitJumpInsn( Opcodes.IFEQ, new Label() );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitLabel tests -------------------------------------------------------

    @Test
    public void testVisitLabel()
    {
        mv.visitLabel( new Label() );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitLdcInsn tests -----------------------------------------------------

    @Test
    public void testVisitLdcInsnWithNonType()
    {
        mv.visitLdcInsn( "a" );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    @Test
    public void testVisitLdcInsnWithPrimitiveType()
    {
        mv.visitLdcInsn( Type.INT_TYPE );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    @Test
    public void testVisitLdcInsnWithObjectType()
    {
        mv.visitLdcInsn( Type.getType( "La/b/c;" ) );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    // visitIincInsn tests ----------------------------------------------------

    @Test
    public void testVisitIincInsn()
    {
        mv.visitIincInsn( 0, 1 );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitTableSwitchInsn tests ---------------------------------------------

    @Test
    public void testVisitTableSwitchInsn()
    {
        mv.visitTableSwitchInsn( 0, 1, new Label(), new Label() );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitLookupSwitchInsn tests --------------------------------------------

    @Test
    public void testVisitLookupSwitchInsn()
    {
        mv.visitLookupSwitchInsn( new Label(), new int[] { 0 }, new Label[] { new Label() } );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitMultiANewArrayInsn tests ------------------------------------------

    @Test
    public void testVisitMultiANewArrayInsnWithPrimitive()
    {
        mv.visitMultiANewArrayInsn( "I", 2 );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    @Test
    public void testVisitMultiANewArrayInsnWithObject()
    {
        mv.visitMultiANewArrayInsn( "La/b/c;", 2 );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    // visitTryCatchBlock tests -----------------------------------------------

    @Test
    public void testVisitTryCatchBlock()
    {
        mv.visitTryCatchBlock( new Label(), new Label(), new Label(), "a/b/c" );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitTryCatchBlockForFinally()
    {
        mv.visitTryCatchBlock( new Label(), new Label(), new Label(), null );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitLocalVariable tests -----------------------------------------------

    @Test
    public void testVisitLocalVariableWithPrimitive()
    {
        mv.visitLocalVariable( "a", "I", null, new Label(), new Label(), 0 );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    @Test
    public void testVisitLocalVariableWithPrimitiveArray()
    {
        mv.visitLocalVariable( "a", "[I", null, new Label(), new Label(), 0 );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    @Test
    public void testVisitLocalVariableWithObject()
    {
        mv.visitLocalVariable( "a", "La/b/c;", null, new Label(), new Label(), 0 );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitLocalVariableWithObjectArray()
    {
        mv.visitLocalVariable( "a", "[La/b/c;", null, new Label(), new Label(), 0 );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c" );
    }

    @Test
    public void testVisitLocalVariableWithGenericObject()
    {
        mv.visitLocalVariable( "a", "La/b/c;", "La/b/c<Lx/y/z;>;", new Label(), new Label(), 0 );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c", "x.y.z" );
    }

    @Test
    public void testVisitLocalVariableWithGenericObjectArray()
    {
        mv.visitLocalVariable( "a", "La/b/c;", "[La/b/c<Lx/y/z;>;", new Label(), new Label(), 0 );

        assertThat( resultCollector.getDependencies() ).containsOnly( "a.b.c", "x.y.z" );
    }

    // visitLineNumber tests --------------------------------------------------

    @Test
    public void testVisitLineNumber()
    {
        mv.visitLineNumber( 0, new Label() );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    // visitMaxs tests --------------------------------------------------------

    @Test
    public void testVisitMaxs()
    {
        mv.visitMaxs( 0, 0 );

        assertThat( resultCollector.getDependencies() ).isEmpty();
    }

    private void assertVisitor( Object actualVisitor )
    {
        //assertEquals( visitor, actualVisitor );
    }

    /**
     * A simple ASM <code>Attribute</code> for use in tests.
     *
     * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
     */
    static class MockAttribute extends Attribute
    {
        public MockAttribute( String type )
        {
            super( type );
        }
    }
}
