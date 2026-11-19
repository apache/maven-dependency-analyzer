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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import org.apache.maven.shared.dependency.analyzer.testcases.ArrayCases;
import org.apache.maven.shared.dependency.analyzer.testcases.InnerClassCase;
import org.apache.maven.shared.dependency.analyzer.testcases.MethodHandleCases;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultCollectorTest {

    private static final String ROOT = "src/test/resources/org/apache/maven/shared/dependency/analyzer";

    Set<String> getDependencies(Class<?> inspectClass) throws IOException {
        String className = inspectClass.getName();
        String path = '/' + className.replace('.', '/') + ".class";
        DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
        try (InputStream is = inspectClass.getResourceAsStream(path)) {
            visitor.visitClass(className, is);
        }
        return visitor.getDependencies();
    }

    @Test
    void testJava11Invoke() throws Exception {
        Path path = Paths.get(
                "src/test/resources/org/apache/maven/shared/dependency/analyzer/commons-bcel-issue362/Bcel362.classx");
        DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
        try (InputStream is = Files.newInputStream(path)) {
            visitor.visitClass("issue362.Bcel362", is);
        }
    }

    @Test
    void testJava17DynamicInvokeRecord() throws Exception {
        Path path = Paths.get(
                "src/test/resources/org/apache/maven/shared/dependency/analyzer/record-invokedynamic/RecordInvokeDynamic.classx");
        DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
        try (InputStream is = Files.newInputStream(path)) {
            visitor.visitClass("recordinvokedynamic.RecordInvokeDynamic", is);
        }
        assertTrue(visitor.getDependencies().contains("recordinvokedynamic.RecordInvokeDynamic"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "issue51980",
                "issue51989",
                "issue52168",
                "issue53543",
                "issue53544a",
                "issue53620",
                "issue53676",
                "issue54119",
                "issue54254"
            })
    void testOssFuzz(String name) {
        // Add a non-"class" suffix so that surefire does not try to read the file and fail the build
        VisitClassException exception = assertThrows(
                VisitClassException.class, () -> visitClass(ROOT + "/ossfuzz/" + name + "/Test.class.clazz"));
        assertEquals(VisitClassException.class, exception.getClass());
    }

    private void visitClass(String location) throws IOException {
        Path path = Paths.get(location);
        DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
        try (InputStream is = Files.newInputStream(path)) {
            visitor.visitClass("Test", is);
        }
    }

    @Test
    void testArrayCases() throws Exception {
        Set<String> dependencies = getDependencies(ArrayCases.class);
        assertFalse(dependencies.contains("[I"));
        for (String dependency : dependencies) {
            assertFalse(dependency.startsWith("["));
        }
        assertTrue(dependencies.contains("java.lang.annotation.Annotation"));
        assertTrue(dependencies.contains("java.lang.reflect.Constructor"));
    }

    @Test
    void testNoMethodHandle() throws Exception {
        Set<String> dependencies = getDependencies(MethodHandleCases.class);
        for (String dependency : dependencies) {
            assertFalse(dependency.startsWith("("));
        }
    }

    @Test
    void testInnerClassNamePreserved() throws Exception {
        Set<String> dependencies = getDependencies(InnerClassCase.class);
        assertTrue(dependencies.contains("java.lang.System"));
        assertTrue(dependencies.contains("java.lang.invoke.MethodHandles$Lookup"));
    }

    @Test
    void testInnerClassWithoutContainerClassInConstantPool() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "consumer/Example", null, "java/lang/Object", null);

        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "use", "()V", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "dependency/Outer$Inner");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "dependency/Outer$Inner", "<init>", "()V", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
        writer.visitEnd();

        DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
        visitor.visitClass("consumer.Example", new ByteArrayInputStream(writer.toByteArray()));

        assertTrue(visitor.getDependencies().contains("dependency.Outer$Inner"));
        assertFalse(visitor.getDependencies().contains("dependency.Outer"));
    }
}
