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

import junit.framework.TestCase;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Tests <code>DependencyVisitor</code>.
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @see DependencyVisitor
 */
public class DependencyVisitorTest extends TestCase
{
    // TODO: finish tests

    // fields -----------------------------------------------------------------

    private DependencyVisitor visitor;

    // TestCase methods -------------------------------------------------------

    /*
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception
    {
        visitor = new DependencyVisitor();
    }

    // visit tests ------------------------------------------------------------

    public void testVisitWithDefaultSuperclass()
    {
        // class a.b.c
        visitor.visit( 50, 0, "a/b/c", null, "java/lang/Object", null );

        assertClasses( "java.lang.Object" );
    }

    public void testVisitWithSuperclass()
    {
        // class a.b.c
        visitor.visit( 50, 0, "a/b/c", null, "x/y/z", null );

        assertClasses( "x.y.z" );
    }

    public void testVisitWithInterface()
    {
        // class a.b.c implements x.y.z
        visitor.visit( 50, 0, "a/b/c", null, "java/lang/Object", new String[] { "x/y/z" } );

        assertClasses( "java.lang.Object", "x.y.z" );
    }

    public void testVisitWithInterfaces()
    {
        // class a.b.c implements p.q.r, x.y.z
        visitor.visit( 50, 0, "a/b/c", null, "java/lang/Object", new String[] { "p/q/r", "x/y/z" } );

        assertClasses( "java.lang.Object", "p.q.r", "x.y.z" );
    }

    public void testVisitWithUnboundedClassTypeParameter()
    {
        // class a.b.c<T>
        String signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", null );

        assertClasses( "java.lang.Object" );
    }

    public void testVisitWithBoundedClassTypeParameter()
    {
        // class a.b.c<T extends x.y.z>
        String signature = "<T:Lx/y/z;>Ljava/lang/Object;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", null );

        assertClasses( "java.lang.Object", "x.y.z" );
    }

    public void testVisitWithBoundedClassTypeParameters()
    {
        // class a.b.c<K extends p.q.r, V extends x.y.z>
        String signature = "<K:Lp/q/r;V:Lx/y/z;>Ljava/lang/Object;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", null );

        assertClasses( "java.lang.Object", "p.q.r", "x.y.z" );
    }

    public void testVisitWithGenericInterface()
    {
        // class a.b.c implements p.q.r<x.y.z>
        String signature = "Ljava/lang/Object;Lp/q/r<Lx/y/z;>;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", new String[] { "p.q.r" } );

        assertClasses( "java.lang.Object", "p.q.r", "x.y.z" );
    }

    public void testVisitWithInterfaceBound()
    {
        // class a.b.c<T> implements x.y.z<T>
        String signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;Lx/y/z<TT;>;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", new String[] { "x.y.z" } );

        assertClasses( "java.lang.Object", "x.y.z" );
    }

    // visitSource tests ------------------------------------------------------

    public void testVisitSource()
    {
        visitor.visitSource( null, null );

        assertNoClasses();
    }

    // visitOuterClass tests --------------------------------------------------

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

    public void testVisitAnnotation()
    {
        assertVisitor( visitor.visitAnnotation( "La/b/c;", false ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitAnnotationWithRuntimeVisibility()
    {
        assertVisitor( visitor.visitAnnotation( "La/b/c;", true ) );

        assertClasses( "a.b.c" );
    }

    // visitAttribute tests ---------------------------------------------------

    public void testVisitAttribute()
    {
        visitor.visitAttribute( new MockAttribute( "a" ) );

        assertNoClasses();
    }

    // visitInnerClass tests --------------------------------------------------

    public void testVisitInnerClass()
    {
        // TODO: ensure innerName is correct

        // class a.b.c { class x.y.z { } }
        visitor.visitInnerClass( "x/y/z", "a/b/c", "z", 0 );

        assertNoClasses();
    }

    public void testVisitInnerClassAnonymous()
    {
        // class a.b.c { new class x.y.z { } }
        visitor.visitInnerClass( "x/y/z$1", "a/b/c", null, 0 );

        assertNoClasses();
    }

    // visitField tests -------------------------------------------------------

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

    public void testVisitFieldArray()
    {
        // a.b.c[] a
        assertVisitor( visitor.visitField( 0, "a", "[La/b/c;", null, null ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitFieldGeneric()
    {
        // a.b.c<x.y.z> a
        assertVisitor( visitor.visitField( 0, "a", "La/b/c;", "La/b/c<Lx/y/z;>;", null ) );

        assertClasses( "a.b.c", "x.y.z" );
    }

    // visitMethod tests ------------------------------------------------------

    public void testVisitMethod()
    {
        // void a()
        assertVisitor( visitor.visitMethod( 0, "a", "()V", null, null ) );

        assertNoClasses();
    }

    public void testVisitMethodWithPrimitiveArgument()
    {
        // void a(int)
        assertVisitor( visitor.visitMethod( 0, "a", "(I)V", null, null ) );

        assertNoClasses();
    }

    public void testVisitMethodWithPrimitiveArrayArgument()
    {
        // void a(int[])
        assertVisitor( visitor.visitMethod( 0, "a", "([I)V", null, null ) );

        assertNoClasses();
    }

    public void testVisitMethodWithObjectArgument()
    {
        // void a(a.b.c)
        assertVisitor( visitor.visitMethod( 0, "a", "(La/b/c;)V", null, null ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodWithObjectArguments()
    {
        // void a(a.b.c, x.y.z)
        assertVisitor( visitor.visitMethod( 0, "a", "(La/b/c;Lx/y/z;)V", null, null ) );

        assertClasses( "a.b.c", "x.y.z" );
    }

    public void testVisitMethodWithObjectArrayArgument()
    {
        // void a(a.b.c[])
        assertVisitor( visitor.visitMethod( 0, "a", "([La/b/c;)V", null, null ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodWithGenericArgument()
    {
        // void a(a.b.c<x.y.z>)
        assertVisitor( visitor.visitMethod( 0, "a", "(La/b/c;)V", "(La/b/c<Lx/y/z;>;)V", null ) );

        assertClasses( "a.b.c", "x.y.z" );
    }

    public void testVisitMethodWithPrimitiveReturnType()
    {
        // int a()
        assertVisitor( visitor.visitMethod( 0, "a", "()I", null, null ) );

        assertNoClasses();
    }

    public void testVisitMethodWithPrimitiveArrayReturnType()
    {
        // int[] a()
        assertVisitor( visitor.visitMethod( 0, "a", "()[I", null, null ) );

        assertNoClasses();
    }

    public void testVisitMethodWithObjectReturnType()
    {
        // a.b.c a()
        assertVisitor( visitor.visitMethod( 0, "a", "()La/b/c;", null, null ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodWithObjectArrayReturnType()
    {
        // a.b.c[] a()
        assertVisitor( visitor.visitMethod( 0, "a", "()[La/b/c;", null, null ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodWithException()
    {
        // void a() throws a.b.c
        assertVisitor( visitor.visitMethod( 0, "a", "()V", null, new String[] { "a/b/c" } ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodWithExceptions()
    {
        // void a() throws a.b.c, x.y.z
        assertVisitor( visitor.visitMethod( 0, "a", "()V", null, new String[] { "a/b/c", "x/y/z" } ) );

        assertClasses( "a.b.c", "x.y.z" );
    }

    // visitAnnotationDefault tests -------------------------------------------

    public void testVisitAnnotationDefault()
    {
        assertVisitor( visitor.visitAnnotationDefault() );
        assertNoClasses();
    }

    // visitParameterAnnotation tests -------------------------------------------

    public void testVisitParameterAnnotation()
    {
        // @a.b.c
        assertVisitor( visitor.visitParameterAnnotation( 0, "La/b/c;", false ) );

        assertClasses( "a.b.c" );
    }

    // visitCode tests --------------------------------------------------------

    public void testVisitCode()
    {
        visitor.visitCode();

        assertNoClasses();
    }

    // visitFrame tests -------------------------------------------------------

    public void testVisitFrame()
    {
        visitor.visitFrame( Opcodes.F_NEW, 0, new Object[0], 0, new Object[0] );

        assertNoClasses();
    }

    // visitInsn tests --------------------------------------------------------

    public void testVisitInsn()
    {
        visitor.visitInsn( Opcodes.NOP );

        assertNoClasses();
    }

    // visitIntInsn tests -----------------------------------------------------

    public void testVisitIntInsn()
    {
        visitor.visitIntInsn( Opcodes.BIPUSH, 0 );

        assertNoClasses();
    }

    // visitVarInsn tests -----------------------------------------------------

    public void testVisitVarInsn()
    {
        visitor.visitVarInsn( Opcodes.ILOAD, 0 );

        assertNoClasses();
    }

    // visitTypeInsn tests ----------------------------------------------------

    public void testVisitTypeInsn()
    {
        visitor.visitTypeInsn( Opcodes.NEW, "a/b/c" );

        assertClasses( "a.b.c" );
    }

    // visitFieldInsn tests ---------------------------------------------------

    public void testVisitFieldInsnWithPrimitive()
    {
        visitor.visitFieldInsn( Opcodes.GETFIELD, "a/b/c", "x", "I" );

        assertClasses( "a.b.c" );
    }

    public void testVisitFieldInsnWithObject()
    {
        visitor.visitFieldInsn( Opcodes.GETFIELD, "a/b/c", "x", "Lx/y/z;" );

        assertClasses( "a.b.c", "x.y.z" );
    }

    // visitMethodInsn tests --------------------------------------------------

    public void testVisitMethodInsn()
    {
        visitor.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()V" );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithPrimitiveArgument()
    {
        visitor.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(I)V" );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithPrimitiveArrayArgument()
    {
        visitor.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "([I)V" );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithObjectArgument()
    {
        visitor.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(Lx/y/z;)V" );

        assertClasses( "a.b.c", "x.y.z" );
    }

    public void testVisitMethodInsnWithObjectArguments()
    {
        visitor.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(Lp/q/r;Lx/y/z;)V" );

        assertClasses( "a.b.c", "p.q.r", "x.y.z" );
    }

    public void testVisitMethodInsnWithObjectArrayArgument()
    {
        visitor.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "([Lx/y/z;)V" );

        assertClasses( "a.b.c", "x.y.z" );
    }

    public void testVisitMethodInsnWithPrimitiveReturnType()
    {
        visitor.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()I" );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithPrimitiveArrayReturnType()
    {
        visitor.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()[I" );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithObjectReturnType()
    {
        visitor.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()Lx/y/z;" );

        assertClasses( "a.b.c", "x.y.z" );
    }

    public void testVisitMethodInsnWithObjectArrayReturnType()
    {
        visitor.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()[Lx/y/z;" );

        assertClasses( "a.b.c", "x.y.z" );
    }

    // visitJumpInsn tests ----------------------------------------------------

    public void testVisitJumpInsn()
    {
        visitor.visitJumpInsn( Opcodes.IFEQ, new Label() );

        assertNoClasses();
    }

    // visitLabel tests -------------------------------------------------------

    public void testVisitLabel()
    {
        visitor.visitLabel( new Label() );

        assertNoClasses();
    }

    // visitLdcInsn tests -----------------------------------------------------

    public void testVisitLdcInsnWithNonType()
    {
        visitor.visitLdcInsn( "a" );

        assertNoClasses();
    }

    public void testVisitLdcInsnWithPrimitiveType()
    {
        visitor.visitLdcInsn( Type.INT_TYPE );

        assertNoClasses();
    }

    public void testVisitLdcInsnWithObjectType()
    {
        visitor.visitLdcInsn( Type.getType( "La/b/c;" ) );

        assertClasses( "a.b.c" );
    }

    // visitIincInsn tests ----------------------------------------------------

    public void testVisitIincInsn()
    {
        visitor.visitIincInsn( 0, 1 );

        assertNoClasses();
    }

    // visitTableSwitchInsn tests ---------------------------------------------

    public void testVisitTableSwitchInsn()
    {
        visitor.visitTableSwitchInsn( 0, 1, new Label(), new Label[] { new Label() } );

        assertNoClasses();
    }

    // visitLookupSwitchInsn tests --------------------------------------------

    public void testVisitLookupSwitchInsn()
    {
        visitor.visitLookupSwitchInsn( new Label(), new int[] { 0 }, new Label[] { new Label() } );

        assertNoClasses();
    }

    // visitMultiANewArrayInsn tests ------------------------------------------

    public void testVisitMultiANewArrayInsnWithPrimitive()
    {
        visitor.visitMultiANewArrayInsn( "I", 2 );

        assertNoClasses();
    }

    public void testVisitMultiANewArrayInsnWithObject()
    {
        visitor.visitMultiANewArrayInsn( "La/b/c;", 2 );

        assertClasses( "a.b.c" );
    }

    // visitTryCatchBlock tests -----------------------------------------------

    public void testVisitTryCatchBlock()
    {
        visitor.visitTryCatchBlock( new Label(), new Label(), new Label(), "a/b/c" );

        assertClasses( "a.b.c" );
    }

    public void testVisitTryCatchBlockForFinally()
    {
        visitor.visitTryCatchBlock( new Label(), new Label(), new Label(), null );

        assertNoClasses();
    }

    // visitLocalVariable tests -----------------------------------------------

    public void testVisitLocalVariableWithPrimitive()
    {
        visitor.visitLocalVariable( "a", "I", null, new Label(), new Label(), 0 );

        assertNoClasses();
    }

    public void testVisitLocalVariableWithPrimitiveArray()
    {
        visitor.visitLocalVariable( "a", "[I", null, new Label(), new Label(), 0 );

        assertNoClasses();
    }

    public void testVisitLocalVariableWithObject()
    {
        visitor.visitLocalVariable( "a", "La/b/c;", null, new Label(), new Label(), 0 );

        assertClasses( "a.b.c" );
    }

    public void testVisitLocalVariableWithObjectArray()
    {
        visitor.visitLocalVariable( "a", "[La/b/c;", null, new Label(), new Label(), 0 );

        assertClasses( "a.b.c" );
    }

    public void testVisitLocalVariableWithGenericObject()
    {
        visitor.visitLocalVariable( "a", "La/b/c;", "La/b/c<Lx/y/z;>;", new Label(), new Label(), 0 );

        assertClasses( "a.b.c", "x.y.z" );
    }

    public void testVisitLocalVariableWithGenericObjectArray()
    {
        visitor.visitLocalVariable( "a", "La/b/c;", "[La/b/c<Lx/y/z;>;", new Label(), new Label(), 0 );

        assertClasses( "a.b.c", "x.y.z" );
    }

    // visitLineNumber tests --------------------------------------------------

    public void testVisitLineNumber()
    {
        visitor.visitLineNumber( 0, new Label() );

        assertNoClasses();
    }

    // visitMaxs tests --------------------------------------------------------

    public void testVisitMaxs()
    {
        visitor.visitMaxs( 0, 0 );

        assertNoClasses();
    }

    // private methods --------------------------------------------------------

    private void assertVisitor( Object actualVisitor )
    {
        assertEquals( visitor, actualVisitor );
    }

    private void assertNoClasses()
    {
        assertClasses( Collections.EMPTY_SET );
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
        assertClasses( new HashSet( Arrays.asList( expectedClasses ) ) );
    }

    private void assertClasses( Set expectedClasses )
    {
        assertEquals( expectedClasses, visitor.getClasses() );
    }
}
