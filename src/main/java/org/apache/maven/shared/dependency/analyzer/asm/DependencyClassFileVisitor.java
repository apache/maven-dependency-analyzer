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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;

import org.apache.maven.shared.dependency.analyzer.ClassFileVisitor;
import org.apache.maven.shared.dependency.analyzer.ClassesPatterns;
import org.apache.maven.shared.dependency.analyzer.DependencyUsage;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.signature.SignatureVisitor;

/**
 * Computes the set of classes referenced by visited class files, using
 * <a href="DependencyVisitor.html">DependencyVisitor</a>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see #getDependencies()
 */
public class DependencyClassFileVisitor implements ClassFileVisitor {
    private static final int BUF_SIZE = 8192;

    private final ResultCollector resultCollector = new ResultCollector();

    private final ClassesPatterns excludedClasses;

    /**
     * <p>Constructor for DependencyClassFileVisitor.</p>
     */
    public DependencyClassFileVisitor(ClassesPatterns excludedClasses) {

        this.excludedClasses = excludedClasses;
    }

    /**
     * <p>Constructor for DependencyClassFileVisitor.</p>
     */
    public DependencyClassFileVisitor() {
        this(new ClassesPatterns());
    }

    /** {@inheritDoc} */
    @Override
    public void visitClass(String className, InputStream in) {
        try {
            byte[] byteCode = toByteArray(in);

            if (excludedClasses.isMatch(className)) {
                return;
            }

            ClassReader reader = new ClassReader(byteCode);

            final Set<String> constantPoolClassRefs = ConstantPoolParser.getConstantPoolClassReferences(byteCode);
            for (String string : constantPoolClassRefs) {
                resultCollector.addName(className, string);
            }

            AnnotationVisitor annotationVisitor = new DefaultAnnotationVisitor(resultCollector, className);
            SignatureVisitor signatureVisitor = new DefaultSignatureVisitor(resultCollector, className);
            FieldVisitor fieldVisitor = new DefaultFieldVisitor(annotationVisitor, resultCollector, className);
            MethodVisitor mv =
                    new DefaultMethodVisitor(annotationVisitor, signatureVisitor, resultCollector, className);
            ClassVisitor classVisitor = new DefaultClassVisitor(
                    signatureVisitor, annotationVisitor, fieldVisitor, mv, resultCollector, className);

            reader.accept(classVisitor, 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (IndexOutOfBoundsException e) {
            // some bug inside ASM causes an IOB exception.
            // this happens when the class isn't valid.
            throw new VisitClassException("Unable to process: " + className, e);
        } catch (IllegalArgumentException e) {
            throw new VisitClassException("Byte code of '" + className + "' is corrupt", e);
        }
    }

    private byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUF_SIZE];
        int i;
        while ((i = in.read(buffer)) > 0) {
            out.write(buffer, 0, i);
        }
        return out.toByteArray();
    }

    /**
     * <p>getDependencies.</p>
     *
     * @return the set of classes referenced by visited class files
     */
    public Set<String> getDependencies() {
        return resultCollector.getDependencies();
    }

    /**
     * <p>getDependencyUsages.</p>
     *
     * @return the set of classes referenced by visited class files, paired with
     * classes declaring the references.
     */
    public Set<DependencyUsage> getDependencyUsages() {
        return resultCollector.getDependencyUsages();
    }
}
