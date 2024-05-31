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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Computes the set of classes referenced by visited code.
 * Inspired by <code>org.objectweb.asm.depend.DependencyVisitor</code> in the ASM dependencies example.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public class DefaultAnnotationVisitor extends AnnotationVisitor {
    private final ResultCollector resultCollector;

    private final String usedByClass;

    /**
     * <p>Constructor for DefaultAnnotationVisitor.</p>
     *
     * @param resultCollector a {@link org.apache.maven.shared.dependency.analyzer.asm.ResultCollector} object.
     */
    public DefaultAnnotationVisitor(ResultCollector resultCollector, String usedByClass) {
        super(Opcodes.ASM9);
        this.resultCollector = resultCollector;
        this.usedByClass = usedByClass;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void visit(final String name, final Object value) {
        if (value instanceof Type) {
            resultCollector.addType(usedByClass, (Type) value);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void visitEnum(final String name, final String desc, final String value) {
        resultCollector.addDesc(usedByClass, desc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AnnotationVisitor visitAnnotation(final String name, final String desc) {
        resultCollector.addDesc(usedByClass, desc);

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AnnotationVisitor visitArray(final String name) {
        return this;
    }
}
