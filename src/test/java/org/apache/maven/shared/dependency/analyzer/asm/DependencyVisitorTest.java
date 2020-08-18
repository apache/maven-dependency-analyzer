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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.signature.SignatureVisitor;

import static org.junit.Assert.assertEquals;

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

        assertClasses( "java.lang.Object" );
    }

    @Test
    public void testVisitWithSuperclass()
    {
        // class a.b.c
        visitor.visit( 50, 0, "a/b/c", null, "x/y/z", null );

        assertClasses( "x.y.z" );
    }

    @Test
    public void testVisitWithInterface()
    {
        // class a.b.c implements x.y.z
        visitor.visit( 50, 0, "a/b/c", null, "java/lang/Object", new String[] { "x/y/z" } );

        assertClasses( "java.lang.Object", "x.y.z" );
    }

    @Test
    public void testVisitWithInterfaces()
    {
        // class a.b.c implements p.q.r, x.y.z
        visitor.visit( 50, 0, "a/b/c", null, "java/lang/Object", new String[] { "p/q/r", "x/y/z" } );

        assertClasses( "java.lang.Object", "p.q.r", "x.y.z" );
    }

    @Test
    public void testVisitWithUnboundedClassTypeParameter()
    {
        // class a.b.c<T>
        String signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", null );

        assertClasses( "java.lang.Object" );
    }

    @Test
    public void testVisitWithBoundedClassTypeParameter()
    {
        // class a.b.c<T extends x.y.z>
        String signature = "<T:Lx/y/z;>Ljava/lang/Object;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", null );

        assertClasses( "java.lang.Object", "x.y.z" );
    }

    @Test
    public void testVisitWithBoundedClassTypeParameters()
    {
        // class a.b.c<K extends p.q.r, V extends x.y.z>
        String signature = "<K:Lp/q/r;V:Lx/y/z;>Ljava/lang/Object;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", null );

        assertClasses( "java.lang.Object", "p.q.r", "x.y.z" );
    }

    @Test
    public void testVisitWithGenericInterface()
    {
        // class a.b.c implements p.q.r<x.y.z>
        String signature = "Ljava/lang/Object;Lp/q/r<Lx/y/z;>;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", new String[] { "p.q.r" } );

        assertClasses( "java.lang.Object", "p.q.r", "x.y.z" );
    }

    @Test
    public void testVisitWithInterfaceBound()
    {
        // class a.b.c<T> implements x.y.z<T>
        String signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;Lx/y/z<TT;>;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", new String[] { "x.y.z" } );

        assertClasses( "java.lang.Object", "x.y.z" );
    }

    // visitSource tests ------------------------------------------------------

    @Test
    public void testVisitSource()
    {
        visitor.visitSource( null, null );

        assertNoClasses();
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

        assertNoClasses();
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

        assertNoClasses();
    }

    // visitAnnotation tests --------------------------------------------------

    @Test
    public void testVisitAnnotation()
    {
        assertVisitor( visitor.visitAnnotation( "La/b/c;", false ) );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitAnnotationWithRuntimeVisibility()
    {
        assertVisitor( visitor.visitAnnotation( "La/b/c;", true ) );

        assertClasses( "a.b.c" );
    }

    // visitAttribute tests ---------------------------------------------------

    @Test
    public void testVisitAttribute()
    {
        visitor.visitAttribute( new MockAttribute( "a" ) );

        assertNoClasses();
    }

    // visitInnerClass tests --------------------------------------------------

    @Test
    public void testVisitInnerClass()
    {
        // TODO: ensure innerName is correct

        // class a.b.c { class x.y.z { } }
        visitor.visitInnerClass( "x/y/z", "a/b/c", "z", 0 );

        assertNoClasses();
    }

    @Test
    public void testVisitInnerClassAnonymous()
    {
        // class a.b.c { new class x.y.z { } }
        visitor.visitInnerClass( "x/y/z$1", "a/b/c", null, 0 );

        assertNoClasses();
    }

    // visitField tests -------------------------------------------------------

    @Test
    public void testVisitField()
    {
        // a.b.c a
        assertVisitor( visitor.visitField( 0, "a", "La/b/c;", null, null ) );

        assertClasses( "a.b.c" );
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

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitFieldGeneric()
    {
        // a.b.c<x.y.z> a
        assertVisitor( visitor.visitField( 0, "a", "La/b/c;", "La/b/c<Lx/y/z;>;", null ) );

        assertClasses( "a.b.c", "x.y.z" );
    }

    // visitMethod tests ------------------------------------------------------

    @Test
    public void testVisitMethod()
    {
        // void a()
        assertVisitor( visitor.visitMethod( 0, "a", "()V", null, null ) );

        assertNoClasses();
    }

    @Test
    public void testVisitMethodWithPrimitiveArgument()
    {
        // void a(int)
        assertVisitor( visitor.visitMethod( 0, "a", "(I)V", null, null ) );

        assertNoClasses();
    }

    @Test
    public void testVisitMethodWithPrimitiveArrayArgument()
    {
        // void a(int[])
        assertVisitor( visitor.visitMethod( 0, "a", "([I)V", null, null ) );

        assertNoClasses();
    }

    @Test
    public void testVisitMethodWithObjectArgument()
    {
        // void a(a.b.c)
        assertVisitor( visitor.visitMethod( 0, "a", "(La/b/c;)V", null, null ) );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodWithObjectArguments()
    {
        // void a(a.b.c, x.y.z)
        assertVisitor( visitor.visitMethod( 0, "a", "(La/b/c;Lx/y/z;)V", null, null ) );

        assertClasses( "a.b.c", "x.y.z" );
    }

    @Test
    public void testVisitMethodWithObjectArrayArgument()
    {
        // void a(a.b.c[])
        assertVisitor( visitor.visitMethod( 0, "a", "([La/b/c;)V", null, null ) );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodWithGenericArgument()
    {
        // void a(a.b.c<x.y.z>)
        assertVisitor( visitor.visitMethod( 0, "a", "(La/b/c;)V", "(La/b/c<Lx/y/z;>;)V", null ) );

        assertClasses( "a.b.c", "x.y.z" );
    }

    @Test
    public void testVisitMethodWithPrimitiveReturnType()
    {
        // int a()
        assertVisitor( visitor.visitMethod( 0, "a", "()I", null, null ) );

        assertNoClasses();
    }

    @Test
    public void testVisitMethodWithPrimitiveArrayReturnType()
    {
        // int[] a()
        assertVisitor( visitor.visitMethod( 0, "a", "()[I", null, null ) );

        assertNoClasses();
    }

    @Test
    public void testVisitMethodWithObjectReturnType()
    {
        // a.b.c a()
        assertVisitor( visitor.visitMethod( 0, "a", "()La/b/c;", null, null ) );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodWithObjectArrayReturnType()
    {
        // a.b.c[] a()
        assertVisitor( visitor.visitMethod( 0, "a", "()[La/b/c;", null, null ) );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodWithException()
    {
        // void a() throws a.b.c
        assertVisitor( visitor.visitMethod( 0, "a", "()V", null, new String[] { "a/b/c" } ) );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodWithExceptions()
    {
        // void a() throws a.b.c, x.y.z
        assertVisitor( visitor.visitMethod( 0, "a", "()V", null, new String[] { "a/b/c", "x/y/z" } ) );

        assertClasses( "a.b.c", "x.y.z" );
    }

    // visitAnnotationDefault tests -------------------------------------------

    @Test
    public void testVisitAnnotationDefault()
    {
        assertVisitor( mv.visitAnnotationDefault() );
        assertNoClasses();
    }

    // visitParameterAnnotation tests -------------------------------------------

    @Test
    public void testVisitParameterAnnotation()
    {
        // @a.b.c
        assertVisitor( mv.visitParameterAnnotation( 0, "La/b/c;", false ) );

        assertClasses( "a.b.c" );
    }

    // visitCode tests --------------------------------------------------------

    @Test
    public void testVisitCode()
    {
        mv.visitCode();

        assertNoClasses();
    }

    // visitFrame tests -------------------------------------------------------

    @Test
    public void testVisitFrame()
    {
        mv.visitFrame( Opcodes.F_NEW, 0, new Object[0], 0, new Object[0] );

        assertNoClasses();
    }

    // visitInsn tests --------------------------------------------------------

    @Test
    public void testVisitInsn()
    {
        mv.visitInsn( Opcodes.NOP );

        assertNoClasses();
    }

    // visitIntInsn tests -----------------------------------------------------

    @Test
    public void testVisitIntInsn()
    {
        mv.visitIntInsn( Opcodes.BIPUSH, 0 );

        assertNoClasses();
    }

    // visitVarInsn tests -----------------------------------------------------

    @Test
    public void testVisitVarInsn()
    {
        mv.visitVarInsn( Opcodes.ILOAD, 0 );

        assertNoClasses();
    }

    // visitTypeInsn tests ----------------------------------------------------

    @Test
    public void testVisitTypeInsn()
    {
        mv.visitTypeInsn( Opcodes.NEW, "a/b/c" );

        assertClasses( "a.b.c" );
    }

    // visitFieldInsn tests ---------------------------------------------------

    @Test
    public void testVisitFieldInsnWithPrimitive()
    {
        mv.visitFieldInsn( Opcodes.GETFIELD, "a/b/c", "x", "I" );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitFieldInsnWithObject()
    {
        mv.visitFieldInsn( Opcodes.GETFIELD, "a/b/c", "x", "Lx/y/z;" );

        assertClasses( "a.b.c" );
    }

    // visitMethodInsn tests --------------------------------------------------

    @Test
    public void testVisitMethodInsn()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()V", false );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithPrimitiveArgument()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(I)V", false );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithPrimitiveArrayArgument()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "([I)V", false );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithObjectArgument()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(Lx/y/z;)V", false );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithObjectArguments()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(Lp/q/r;Lx/y/z;)V", false );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithObjectArrayArgument()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "([Lx/y/z;)V", false );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithPrimitiveReturnType()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()I", false );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithPrimitiveArrayReturnType()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()[I", false );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithObjectReturnType()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()Lx/y/z;", false );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitMethodInsnWithObjectArrayReturnType()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()[Lx/y/z;", false );

        assertClasses( "a.b.c" );
    }

    // visitJumpInsn tests ----------------------------------------------------

    @Test
    public void testVisitJumpInsn()
    {
        mv.visitJumpInsn( Opcodes.IFEQ, new Label() );

        assertNoClasses();
    }

    // visitLabel tests -------------------------------------------------------

    @Test
    public void testVisitLabel()
    {
        mv.visitLabel( new Label() );

        assertNoClasses();
    }

    // visitLdcInsn tests -----------------------------------------------------

    @Test
    public void testVisitLdcInsnWithNonType()
    {
        mv.visitLdcInsn( "a" );

        assertNoClasses();
    }

    @Test
    public void testVisitLdcInsnWithPrimitiveType()
    {
        mv.visitLdcInsn( Type.INT_TYPE );

        assertNoClasses();
    }

    @Test
    public void testVisitLdcInsnWithObjectType()
    {
        mv.visitLdcInsn( Type.getType( "La/b/c;" ) );

        assertClasses( "a.b.c" );
    }

    // visitIincInsn tests ----------------------------------------------------

    @Test
    public void testVisitIincInsn()
    {
        mv.visitIincInsn( 0, 1 );

        assertNoClasses();
    }

    // visitTableSwitchInsn tests ---------------------------------------------

    @Test
    public void testVisitTableSwitchInsn()
    {
        mv.visitTableSwitchInsn( 0, 1, new Label(), new Label() );

        assertNoClasses();
    }

    // visitLookupSwitchInsn tests --------------------------------------------

    @Test
    public void testVisitLookupSwitchInsn()
    {
        mv.visitLookupSwitchInsn( new Label(), new int[] { 0 }, new Label[] { new Label() } );

        assertNoClasses();
    }

    // visitMultiANewArrayInsn tests ------------------------------------------

    @Test
    public void testVisitMultiANewArrayInsnWithPrimitive()
    {
        mv.visitMultiANewArrayInsn( "I", 2 );

        assertNoClasses();
    }

    @Test
    public void testVisitMultiANewArrayInsnWithObject()
    {
        mv.visitMultiANewArrayInsn( "La/b/c;", 2 );

        assertClasses( "a.b.c" );
    }

    // visitTryCatchBlock tests -----------------------------------------------

    @Test
    public void testVisitTryCatchBlock()
    {
        mv.visitTryCatchBlock( new Label(), new Label(), new Label(), "a/b/c" );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitTryCatchBlockForFinally()
    {
        mv.visitTryCatchBlock( new Label(), new Label(), new Label(), null );

        assertNoClasses();
    }

    // visitLocalVariable tests -----------------------------------------------

    @Test
    public void testVisitLocalVariableWithPrimitive()
    {
        mv.visitLocalVariable( "a", "I", null, new Label(), new Label(), 0 );

        assertNoClasses();
    }

    @Test
    public void testVisitLocalVariableWithPrimitiveArray()
    {
        mv.visitLocalVariable( "a", "[I", null, new Label(), new Label(), 0 );

        assertNoClasses();
    }

    @Test
    public void testVisitLocalVariableWithObject()
    {
        mv.visitLocalVariable( "a", "La/b/c;", null, new Label(), new Label(), 0 );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitLocalVariableWithObjectArray()
    {
        mv.visitLocalVariable( "a", "[La/b/c;", null, new Label(), new Label(), 0 );

        assertClasses( "a.b.c" );
    }

    @Test
    public void testVisitLocalVariableWithGenericObject()
    {
        mv.visitLocalVariable( "a", "La/b/c;", "La/b/c<Lx/y/z;>;", new Label(), new Label(), 0 );

        assertClasses( "a.b.c", "x.y.z" );
    }

    @Test
    public void testVisitLocalVariableWithGenericObjectArray()
    {
        mv.visitLocalVariable( "a", "La/b/c;", "[La/b/c<Lx/y/z;>;", new Label(), new Label(), 0 );

        assertClasses( "a.b.c", "x.y.z" );
    }

    // visitLineNumber tests --------------------------------------------------

    @Test
    public void testVisitLineNumber()
    {
        mv.visitLineNumber( 0, new Label() );

        assertNoClasses();
    }

    // visitMaxs tests --------------------------------------------------------

    @Test
    public void testVisitMaxs()
    {
        mv.visitMaxs( 0, 0 );

        assertNoClasses();
    }

    private void assertVisitor( Object actualVisitor )
    {
        //assertEquals( visitor, actualVisitor );
    }

    private void assertNoClasses()
    {
        assertClasses( Collections.<String>emptySet() );
    }

    private void assertClasses( String element )
    {
        assertClasses( Collections.singleton( element ) );
    }

    private void assertClasses( String expectedClass1, String expectedClass2 )
    {
        assertClasses( new String[] { expectedClass1, expectedClass2 } );
    }

    private void assertClasses( String expectedClass1, String expectedClass2, String expectedClass3 )
    {
        assertClasses( new String[] { expectedClass1, expectedClass2, expectedClass3 } );
    }

    private void assertClasses( String[] expectedClasses )
    {
        assertClasses( new HashSet<>( Arrays.asList( expectedClasses ) ) );
    }

    private void assertClasses( Set<String> expectedClasses )
    {
        assertEquals( expectedClasses, resultCollector.getDependencies() );
    }
}
