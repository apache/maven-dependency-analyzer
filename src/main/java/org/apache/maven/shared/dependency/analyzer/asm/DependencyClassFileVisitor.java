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

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.maven.shared.dependency.analyzer.ClassFileVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.signature.SignatureVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes the set of classes referenced by visited class files, using
 * <a href="DependencyVisitor.html">DependencyVisitor</a>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see #getDependencies()
 */
public class DependencyClassFileVisitor implements ClassFileVisitor {
    private final ResultCollector resultCollector = new ResultCollector();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * <p>Constructor for DependencyClassFileVisitor.</p>
     */
    public DependencyClassFileVisitor() {}

    /** {@inheritDoc} */
    public void visitClass(String className, InputStream in) {
        try {
            byte[] byteCode = IOUtils.toByteArray(in);
            ClassReader reader = new ClassReader(byteCode);

            final Set<String> constantPoolClassRefs = ConstantPoolParser.getConstantPoolClassReferences(byteCode);
            for (String string : constantPoolClassRefs) {
                resultCollector.addName(string);
            }

            AnnotationVisitor annotationVisitor = new DefaultAnnotationVisitor(resultCollector);
            SignatureVisitor signatureVisitor = new DefaultSignatureVisitor(resultCollector);
            FieldVisitor fieldVisitor = new DefaultFieldVisitor(annotationVisitor, resultCollector);
            MethodVisitor mv = new DefaultMethodVisitor(annotationVisitor, signatureVisitor, resultCollector);
            ClassVisitor classVisitor =
                    new DefaultClassVisitor(signatureVisitor, annotationVisitor, fieldVisitor, mv, resultCollector);

            reader.accept(classVisitor, 0);
        } catch (IOException exception) {
            exception.printStackTrace();
        } catch (IndexOutOfBoundsException e) {
            // some bug inside ASM causes an IOB exception. Log it and move on?
            // this happens when the class isn't valid.
            logger.warn("Unable to process: " + className);
        }
    }

    /**
     * <p>getDependencies.</p>
     *
     * @return the set of classes referenced by visited class files
     */
    public Set<String> getDependencies() {
        return resultCollector.getDependencies();
    }
}
