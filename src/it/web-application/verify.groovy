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

def analysis = new File( basedir, 'target/analysis.txt' ).text

def expected = '''
UsedDeclaredArtifacts:

UsedUndeclaredArtifactsWithClasses:

UnusedDeclaredArtifacts:
 org.apache.maven:maven-core:jar:3.9.12:compile
 org.apache.maven:maven-model-builder:jar:3.9.12:compile
 org.apache.maven:maven-settings-builder:jar:3.9.12:compile

TestArtifactsWithNonTestScope:
'''

assert analysis == expected

def analysisWeb1 = new File( basedir, 'web1/target/analysis.txt' ).text
def analysisWeb2 = new File( basedir, 'web2/target/analysis.txt' ).text

def expectedWeb = '''
UsedDeclaredArtifacts:
 org.apache.maven:maven-core:jar:3.9.12:compile
 org.apache.maven:maven-model-builder:jar:3.9.12:compile

UsedUndeclaredArtifactsWithClasses:
 org.apache.maven:maven-model:jar:3.9.12:compile
  org.apache.maven.model.Plugin

UnusedDeclaredArtifacts:
 org.apache.maven:maven-settings-builder:jar:3.9.12:compile

TestArtifactsWithNonTestScope:
'''

assert analysisWeb1 == expectedWeb
assert analysisWeb2 == expectedWeb
