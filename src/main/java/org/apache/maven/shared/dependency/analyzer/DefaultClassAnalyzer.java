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
package org.apache.maven.shared.dependency.analyzer;

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.zip.ZipException;

/**
 * <p>DefaultClassAnalyzer class.</p>
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
@Named
@Singleton
public class DefaultClassAnalyzer implements ClassAnalyzer {

    @Override
    public Set<String> analyze(URL url, ClassesPatterns excludedClasses) throws IOException {
        CollectorClassFileVisitor visitor = new CollectorClassFileVisitor(excludedClasses);

        try {
            ClassFileVisitorUtils.accept(url, visitor);
        } catch (ZipException e) {
            // since the current ZipException gives no indication what jar file is corrupted
            // we prefer to wrap another ZipException for better error visibility
            ZipException ze = new ZipException("Cannot process Jar entry on URL: " + url + " due to " + e.getMessage());
            ze.initCause(e);
            throw ze;
        }

        return visitor.getClasses();
    }
}
