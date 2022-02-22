package org.apache.maven.shared.dependency.analyzer.testcases;

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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;

import org.apache.maven.shared.dependency.analyzer.asm.DefaultMethodVisitor;

public class ArrayCases
{
    /**
     * Cause {@link DefaultMethodVisitor#visitMethodInsn(int, String, String, String, boolean)} to be called
     * with primitive array
     * @param source
     * @return
     */
    public int[] primitive( int[] source )
    {
        // causes
        return source.clone();
    }

    public <T> void arrayOfArrayCollectedAsReference( Class<T> cls )
    {
        Constructor<?>[] constructors = cls.getConstructors();
        for ( Constructor<?> constructor : constructors )
        {
            for ( Annotation[] parameters : constructor.getParameterAnnotations() )
            {
                for ( Annotation annotation : parameters )
                {
                    System.out.println("Class: "+cls+", Annotation: "+ annotation );
                }
            }
        }
    }
}
