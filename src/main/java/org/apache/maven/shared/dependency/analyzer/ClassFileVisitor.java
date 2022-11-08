package org.apache.maven.shared.dependency.analyzer;

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

import java.io.IOException;
import java.io.InputStream;

/**
 * <p>ClassFileVisitor interface.</p>
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public interface ClassFileVisitor
{
    /**
     * <p>visitClass.</p>
     *
     * @param className a {@link java.lang.String} object.
     * @param in a {@link java.io.InputStream} object.
     */
    void visitClass( String className, InputStreamProvider in );

    /**
     * Provider for the input stream on the class file
     */
    interface InputStreamProvider
    {
        InputStream open() throws IOException;
    }
}
