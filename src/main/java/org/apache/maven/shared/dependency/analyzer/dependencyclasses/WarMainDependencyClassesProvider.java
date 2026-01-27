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

import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ClassesPatterns;
import org.apache.maven.shared.dependency.analyzer.DependencyUsage;
import org.apache.maven.shared.dependency.analyzer.MainDependencyClassesProvider;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Implementation of {@link MainDependencyClassesProvider} for web applications.
 */
@Named
@Singleton
class WarMainDependencyClassesProvider implements MainDependencyClassesProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(WarMainDependencyClassesProvider.class);

    @Override
    public Set<DependencyUsage> getDependencyClasses(MavenProject project, ClassesPatterns excludedClasses)
            throws IOException {
        if (!"war".equals(project.getPackaging())) {
            return Collections.emptySet();
        }

        File webXml = findWebXml(project);
        if (webXml == null) {
            LOGGER.debug("No web.xml found for project {}", project);
            return Collections.emptySet();
        }

        if (!webXml.isFile()) {
            LOGGER.debug("{} is not a file in project {}", webXml, project);
            return Collections.emptySet();
        }

        try {
            return processWebXml(webXml, excludedClasses);
        } catch (SAXException | ParserConfigurationException e) {
            LOGGER.warn("Error parsing web.xml file {}: {}", webXml, e.getMessage());
            return Collections.emptySet();
        }
    }

    private File findWebXml(MavenProject project) {
        // standard location
        File webXmlFile = new File(project.getBasedir(), "src/main/webapp/WEB-INF/web.xml");
        if (webXmlFile.isFile()) {
            return webXmlFile;
        }

        // check maven-war-plugin configuration for custom location of web.xml
        Plugin plugin = project.getBuild().getPluginsAsMap().get("org.apache.maven.plugins:maven-war-plugin");
        if (plugin == null) {
            // should not happen as we are in a war project
            LOGGER.debug("No war plugin found for project {}", project);
            return null;
        }

        return Optional.ofNullable(plugin.getConfiguration())
                .map(Xpp3Dom.class::cast)
                .map(config -> config.getChild("webXml"))
                .map(Xpp3Dom::getValue)
                .map(path -> new File(project.getBasedir(), path))
                .orElse(null);
    }

    private Set<DependencyUsage> processWebXml(File webXml, ClassesPatterns excludedClasses)
            throws IOException, SAXException, ParserConfigurationException {

        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        documentBuilderFactory.setNamespaceAware(true);
        documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

        Document doc = documentBuilder.parse(webXml);

        List<String> classes = new ArrayList<>();

        processClassesFromTags(doc, classes, "filter-class");
        processClassesFromTags(doc, classes, "listener-class");
        processClassesFromTags(doc, classes, "servlet-class");

        return classes.stream()
                .filter(className -> !excludedClasses.isMatch(className))
                .map(className -> new DependencyUsage(className, webXml.toString()))
                .collect(Collectors.toSet());
    }

    private void processClassesFromTags(Document doc, List<String> classes, String tagName) {
        NodeList tags = doc.getElementsByTagName(tagName);
        for (int i = 0; i < tags.getLength(); i++) {
            Node node = tags.item(i);
            Optional.ofNullable(node.getTextContent())
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .ifPresent(classes::add);
        }
    }
}
