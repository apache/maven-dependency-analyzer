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
package org.apache.maven.shared.dependency.analyzer.dependencyclasses;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.api.Packaging;
import org.apache.maven.api.Project;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.xml.XmlNode;
import org.apache.maven.shared.dependency.analyzer.ClassesPatterns;
import org.apache.maven.shared.dependency.analyzer.DependencyUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarMainDependencyClassesProviderTest {

    @Mock
    private Project project;

    private final WarMainDependencyClassesProvider provider = new WarMainDependencyClassesProvider();

    @Test
    void parseDefaultWebXml() throws IOException, URISyntaxException {
        Path basePath = Paths.get(getClass().getResource("/webapp").toURI());
        when(project.getBasedir()).thenReturn(basePath);
        Packaging packaging = warPackaging();
        when(project.getPackaging()).thenReturn(packaging);

        Set<DependencyUsage> classes =
                provider.getDependencyClasses(project, new ClassesPatterns(Collections.singleton(".*\\.Servlet$")));

        assertThat(classes)
                .map(DependencyUsage::getDependencyClass)
                .containsExactlyInAnyOrder("org.example.test.Filter", "org.example.test.Listener");
    }

    @Test
    void noDefaultWebXml() throws IOException, URISyntaxException {
        Path basePath = Paths.get(getClass().getResource("/webapp/examples").toURI());
        when(project.getBasedir()).thenReturn(basePath);
        Packaging packaging = warPackaging();
        when(project.getPackaging()).thenReturn(packaging);
        when(project.getBuild()).thenReturn(Build.newInstance());

        Set<DependencyUsage> classes = provider.getDependencyClasses(project, new ClassesPatterns());

        assertThat(classes).isEmpty();
    }

    public static Stream<Arguments> examplesData() {
        return Stream.of(
                Arguments.of("empty-web.xml", new String[] {}),
                Arguments.of("nons-web.xml", new String[] {}),
                Arguments.of("multi-web-2.5.xml", new String[] {
                    "org.example.test.Filter1",
                    "org.example.test.Filter2",
                    "org.example.test.Listener1",
                    "org.example.test.Listener2"
                }),
                Arguments.of("multi-web-3.1.xml", new String[] {
                    "org.example.test.Filter1",
                    "org.example.test.Filter2",
                    "org.example.test.Listener1",
                    "org.example.test.Listener2"
                }),
                Arguments.of("wrong-web.xml", new String[] {}),
                Arguments.of("no-exists-web.xml", new String[] {}));
    }

    @ParameterizedTest
    @MethodSource("examplesData")
    void examples(String webXmlName, String[] expectedClasses) throws Exception {
        setupProjectWithWebXml(webXmlName);

        Set<DependencyUsage> classes = provider.getDependencyClasses(project, new ClassesPatterns());

        assertThat(classes).map(DependencyUsage::getDependencyClass).containsExactlyInAnyOrder(expectedClasses);
    }

    private void setupProjectWithWebXml(String webXmlName) throws URISyntaxException {
        Path basePath = Paths.get(getClass().getResource("/webapp/examples").toURI());

        when(project.getBasedir()).thenReturn(basePath);
        Packaging packaging = warPackaging();
        when(project.getPackaging()).thenReturn(packaging);

        Plugin plugin = Plugin.newBuilder()
                .groupId("org.apache.maven.plugins")
                .artifactId("maven-war-plugin")
                .configuration(XmlNode.newInstance(
                        "configuration", Collections.singletonList(XmlNode.newInstance("webXml", webXmlName))))
                .build();

        when(project.getBuild())
                .thenReturn(Build.newBuilder()
                        .plugins(Collections.singletonList(plugin))
                        .build());
    }

    private static Packaging warPackaging() {
        Packaging packaging = mock(Packaging.class);
        when(packaging.id()).thenReturn("war");
        return packaging;
    }
}
