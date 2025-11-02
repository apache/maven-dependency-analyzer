/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.shared.dependency.analyzer.asm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureVisitor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests <code>DependencyVisitor</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
class DependencyVisitorTest {
    private final ResultCollector resultCollector = new ResultCollector();
    private DefaultClassVisitor visitor;
    private MethodVisitor mv;

    private String usedByClass = "com.example.MyClass";

    @BeforeEach
    void setUp() {
        AnnotationVisitor annotationVisitor = new DefaultAnnotationVisitor(resultCollector, usedByClass);
        SignatureVisitor signatureVisitor = new DefaultSignatureVisitor(resultCollector, usedByClass);
        FieldVisitor fieldVisitor = new DefaultFieldVisitor(annotationVisitor, resultCollector, usedByClass);
        mv = new DefaultMethodVisitor(annotationVisitor, signatureVisitor, resultCollector, usedByClass);
        visitor = new DefaultClassVisitor(
                signatureVisitor, annotationVisitor, fieldVisitor, mv, resultCollector, usedByClass);
    }

    @Test
    void visitWithDefaultSuperclass() {
        // class a.b.c
        visitor.visit(50, 0, "a/b/c", null, "java/lang/Object", null);

        assertThat(resultCollector.getDependencies()).containsOnly("java.lang.Object");
    }

    @Test
    void visitWithSuperclass() {
        // class a.b.c
        visitor.visit(50, 0, "a/b/c", null, "x/y/z", null);

        assertThat(resultCollector.getDependencies()).containsOnly("x.y.z");
    }

    @Test
    void visitWithInterface() {
        // class a.b.c implements x.y.z
        visitor.visit(50, 0, "a/b/c", null, "java/lang/Object", new String[] {"x/y/z"});

        assertThat(resultCollector.getDependencies()).containsOnly("java.lang.Object", "x.y.z");
    }

    @Test
    void visitWithInterfaces() {
        // class a.b.c implements p.q.r, x.y.z
        visitor.visit(50, 0, "a/b/c", null, "java/lang/Object", new String[] {"p/q/r", "x/y/z"});

        assertThat(resultCollector.getDependencies()).containsOnly("java.lang.Object", "p.q.r", "x.y.z");
    }

    @Test
    void visitWithUnboundedClassTypeParameter() {
        // class a.b.c<T>
        String signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;";

        visitor.visit(50, 0, "a/b/c", signature, "java/lang/Object", null);

        assertThat(resultCollector.getDependencies()).containsOnly("java.lang.Object");
    }

    @Test
    void visitWithBoundedClassTypeParameter() {
        // class a.b.c<T extends x.y.z>
        String signature = "<T:Lx/y/z;>Ljava/lang/Object;";

        visitor.visit(50, 0, "a/b/c", signature, "java/lang/Object", null);

        assertThat(resultCollector.getDependencies()).containsOnly("java.lang.Object", "x.y.z");
    }

    @Test
    void visitWithBoundedClassTypeParameters() {
        // class a.b.c<K extends p.q.r, V extends x.y.z>
        String signature = "<K:Lp/q/r;V:Lx/y/z;>Ljava/lang/Object;";

        visitor.visit(50, 0, "a/b/c", signature, "java/lang/Object", null);

        assertThat(resultCollector.getDependencies()).containsOnly("java.lang.Object", "p.q.r", "x.y.z");
    }

    @Test
    void visitWithGenericInterface() {
        // class a.b.c implements p.q.r<x.y.z>
        String signature = "Ljava/lang/Object;Lp/q/r<Lx/y/z;>;";

        visitor.visit(50, 0, "a/b/c", signature, "java/lang/Object", new String[] {"p.q.r"});

        assertThat(resultCollector.getDependencies()).containsOnly("java.lang.Object", "p.q.r", "x.y.z");
    }

    @Test
    void visitWithInterfaceBound() {
        // class a.b.c<T> implements x.y.z<T>
        String signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;Lx/y/z<TT;>;";

        visitor.visit(50, 0, "a/b/c", signature, "java/lang/Object", new String[] {"x.y.z"});

        assertThat(resultCollector.getDependencies()).containsOnly("java.lang.Object", "x.y.z");
    }

    // visitSource tests ------------------------------------------------------

    @Test
    void visitSource() {
        visitor.visitSource(null, null);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitOuterClass tests --------------------------------------------------

    @Test
    void visitOuterClass() {
        // class a.b.c
        // {
        //     class ...
        //     {
        //     }
        // }
        visitor.visitOuterClass("a/b/c", null, null);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    @Test
    void visitOuterClassInMethod() {
        // class a.b.c
        // {
        //     x.y.z x(p.q.r p)
        //     {
        //         class ...
        //         {
        //         }
        //     }
        // }
        visitor.visitOuterClass("a/b/c", "x", "(Lp/q/r;)Lx/y/z;");

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitAnnotation tests --------------------------------------------------

    @Test
    void visitAnnotation() {
        assertVisitor(visitor.visitAnnotation("La/b/c;", false));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitAnnotationWithRuntimeVisibility() {
        assertVisitor(visitor.visitAnnotation("La/b/c;", true));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    // visitAttribute tests ---------------------------------------------------

    @Test
    void visitAttribute() {
        visitor.visitAttribute(new MockAttribute("a"));

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitInnerClass tests --------------------------------------------------

    @Test
    void visitInnerClass() {
        // TODO: ensure innerName is correct

        // class a.b.c { class x.y.z { } }
        visitor.visitInnerClass("x/y/z", "a/b/c", "z", 0);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    @Test
    void visitInnerClassAnonymous() {
        // class a.b.c { new class x.y.z { } }
        visitor.visitInnerClass("x/y/z$1", "a/b/c", null, 0);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitField tests -------------------------------------------------------

    @Test
    void visitField() {
        // a.b.c a
        assertVisitor(visitor.visitField(0, "a", "La/b/c;", null, null));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    // TODO: determine actual use of default values
    // void testVisitFieldWithValue()
    // {
    // }

    @Test
    void visitFieldArray() {
        // a.b.c[] a
        assertVisitor(visitor.visitField(0, "a", "[La/b/c;", null, null));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitFieldGeneric() {
        // a.b.c<x.y.z> a
        assertVisitor(visitor.visitField(0, "a", "La/b/c;", "La/b/c<Lx/y/z;>;", null));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c", "x.y.z");
    }

    // visitMethod tests ------------------------------------------------------

    @Test
    void visitMethod() {
        // void a()
        assertVisitor(visitor.visitMethod(0, "a", "()V", null, null));

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    @Test
    void visitMethodWithPrimitiveArgument() {
        // void a(int)
        assertVisitor(visitor.visitMethod(0, "a", "(I)V", null, null));

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    @Test
    void visitMethodWithPrimitiveArrayArgument() {
        // void a(int[])
        assertVisitor(visitor.visitMethod(0, "a", "([I)V", null, null));

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    @Test
    void visitMethodWithObjectArgument() {
        // void a(a.b.c)
        assertVisitor(visitor.visitMethod(0, "a", "(La/b/c;)V", null, null));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodWithObjectArguments() {
        // void a(a.b.c, x.y.z)
        assertVisitor(visitor.visitMethod(0, "a", "(La/b/c;Lx/y/z;)V", null, null));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c", "x.y.z");
    }

    @Test
    void visitMethodWithObjectArrayArgument() {
        // void a(a.b.c[])
        assertVisitor(visitor.visitMethod(0, "a", "([La/b/c;)V", null, null));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodWithGenericArgument() {
        // void a(a.b.c<x.y.z>)
        assertVisitor(visitor.visitMethod(0, "a", "(La/b/c;)V", "(La/b/c<Lx/y/z;>;)V", null));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c", "x.y.z");
    }

    @Test
    void visitMethodWithPrimitiveReturnType() {
        // int a()
        assertVisitor(visitor.visitMethod(0, "a", "()I", null, null));

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    @Test
    void visitMethodWithPrimitiveArrayReturnType() {
        // int[] a()
        assertVisitor(visitor.visitMethod(0, "a", "()[I", null, null));

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    @Test
    void visitMethodWithObjectReturnType() {
        // a.b.c a()
        assertVisitor(visitor.visitMethod(0, "a", "()La/b/c;", null, null));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodWithObjectArrayReturnType() {
        // a.b.c[] a()
        assertVisitor(visitor.visitMethod(0, "a", "()[La/b/c;", null, null));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodWithException() {
        // void a() throws a.b.c
        assertVisitor(visitor.visitMethod(0, "a", "()V", null, new String[] {"a/b/c"}));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodWithExceptions() {
        // void a() throws a.b.c, x.y.z
        assertVisitor(visitor.visitMethod(0, "a", "()V", null, new String[] {"a/b/c", "x/y/z"}));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c", "x.y.z");
    }

    // visitAnnotationDefault tests -------------------------------------------

    @Test
    void visitAnnotationDefault() {
        assertVisitor(mv.visitAnnotationDefault());
        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitParameterAnnotation tests -------------------------------------------

    @Test
    void visitParameterAnnotation() {
        // @a.b.c
        assertVisitor(mv.visitParameterAnnotation(0, "La/b/c;", false));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    // visitCode tests --------------------------------------------------------

    @Test
    void visitCode() {
        mv.visitCode();

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitFrame tests -------------------------------------------------------

    @Test
    void visitFrame() {
        mv.visitFrame(Opcodes.F_NEW, 0, new Object[0], 0, new Object[0]);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitInsn tests --------------------------------------------------------

    @Test
    void visitInsn() {
        mv.visitInsn(Opcodes.NOP);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitIntInsn tests -----------------------------------------------------

    @Test
    void visitIntInsn() {
        mv.visitIntInsn(Opcodes.BIPUSH, 0);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitVarInsn tests -----------------------------------------------------

    @Test
    void visitVarInsn() {
        mv.visitVarInsn(Opcodes.ILOAD, 0);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitTypeInsn tests ----------------------------------------------------

    @Test
    void visitTypeInsn() {
        mv.visitTypeInsn(Opcodes.NEW, "a/b/c");

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    // visitFieldInsn tests ---------------------------------------------------

    @Test
    void visitFieldInsnWithPrimitive() {
        mv.visitFieldInsn(Opcodes.GETFIELD, "a/b/c", "x", "I");

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitFieldInsnWithObject() {
        mv.visitFieldInsn(Opcodes.GETFIELD, "a/b/c", "x", "Lx/y/z;");

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    // visitMethodInsn tests --------------------------------------------------

    @Test
    void visitMethodInsn() {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()V", false);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodInsnWithPrimitiveArgument() {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(I)V", false);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodInsnWithPrimitiveArrayArgument() {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "([I)V", false);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodInsnWithObjectArgument() {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(Lx/y/z;)V", false);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodInsnWithObjectArguments() {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(Lp/q/r;Lx/y/z;)V", false);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodInsnWithObjectArrayArgument() {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "([Lx/y/z;)V", false);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodInsnWithPrimitiveReturnType() {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()I", false);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodInsnWithPrimitiveArrayReturnType() {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()[I", false);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodInsnWithObjectReturnType() {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()Lx/y/z;", false);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitMethodInsnWithObjectArrayReturnType() {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()[Lx/y/z;", false);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    // visitJumpInsn tests ----------------------------------------------------

    @Test
    void visitJumpInsn() {
        mv.visitJumpInsn(Opcodes.IFEQ, new Label());

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitLabel tests -------------------------------------------------------

    @Test
    void visitLabel() {
        mv.visitLabel(new Label());

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitLdcInsn tests -----------------------------------------------------

    @Test
    void visitLdcInsnWithNonType() {
        mv.visitLdcInsn("a");

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    @Test
    void visitLdcInsnWithPrimitiveType() {
        mv.visitLdcInsn(Type.INT_TYPE);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    @Test
    void visitLdcInsnWithObjectType() {
        mv.visitLdcInsn(Type.getType("La/b/c;"));

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    // visitIincInsn tests ----------------------------------------------------

    @Test
    void visitIincInsn() {
        mv.visitIincInsn(0, 1);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitTableSwitchInsn tests ---------------------------------------------

    @Test
    void visitTableSwitchInsn() {
        mv.visitTableSwitchInsn(0, 1, new Label(), new Label());

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitLookupSwitchInsn tests --------------------------------------------

    @Test
    void visitLookupSwitchInsn() {
        mv.visitLookupSwitchInsn(new Label(), new int[] {0}, new Label[] {new Label()});

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitMultiANewArrayInsn tests ------------------------------------------

    @Test
    void visitMultiANewArrayInsnWithPrimitive() {
        mv.visitMultiANewArrayInsn("I", 2);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    @Test
    void visitMultiANewArrayInsnWithObject() {
        mv.visitMultiANewArrayInsn("La/b/c;", 2);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    // visitTryCatchBlock tests -----------------------------------------------

    @Test
    void visitTryCatchBlock() {
        mv.visitTryCatchBlock(new Label(), new Label(), new Label(), "a/b/c");

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitTryCatchBlockForFinally() {
        mv.visitTryCatchBlock(new Label(), new Label(), new Label(), null);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitLocalVariable tests -----------------------------------------------

    @Test
    void visitLocalVariableWithPrimitive() {
        mv.visitLocalVariable("a", "I", null, new Label(), new Label(), 0);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    @Test
    void visitLocalVariableWithPrimitiveArray() {
        mv.visitLocalVariable("a", "[I", null, new Label(), new Label(), 0);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    @Test
    void visitLocalVariableWithObject() {
        mv.visitLocalVariable("a", "La/b/c;", null, new Label(), new Label(), 0);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitLocalVariableWithObjectArray() {
        mv.visitLocalVariable("a", "[La/b/c;", null, new Label(), new Label(), 0);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c");
    }

    @Test
    void visitLocalVariableWithGenericObject() {
        mv.visitLocalVariable("a", "La/b/c;", "La/b/c<Lx/y/z;>;", new Label(), new Label(), 0);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c", "x.y.z");
    }

    @Test
    void visitLocalVariableWithGenericObjectArray() {
        mv.visitLocalVariable("a", "La/b/c;", "[La/b/c<Lx/y/z;>;", new Label(), new Label(), 0);

        assertThat(resultCollector.getDependencies()).containsOnly("a.b.c", "x.y.z");
    }

    // visitLineNumber tests --------------------------------------------------

    @Test
    void visitLineNumber() {
        mv.visitLineNumber(0, new Label());

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitMaxs tests --------------------------------------------------------

    @Test
    void visitMaxs() {
        mv.visitMaxs(0, 0);

        assertThat(resultCollector.getDependencies()).isEmpty();
    }

    // visitInvokeDynamicInsn tests -------------------------------------------
    @Test
    void visitInvokeDynamic() {
        Type type = Type.getType("(La/b/C;)V");
        mv.visitInvokeDynamicInsn("a", "", null, type);
        assertThat(resultCollector.getDependencies()).contains("a.b.C");
    }

    private void assertVisitor(Object actualVisitor) {
        // assertEquals( visitor, actualVisitor );
    }

    /**
     * A simple ASM <code>Attribute</code> for use in tests.
     *
     * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
     */
    static class MockAttribute extends Attribute {
        MockAttribute(String type) {
            super(type);
        }
    }
}
