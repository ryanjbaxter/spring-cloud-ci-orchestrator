package io.github.ryanjbaxter.cireleaseorchestrator.graph;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Parses the parts of a pom.xml that matter for dependency-graph derivation, using the JDK's
 * built-in DOM parser (no extra XML dependency needed). Only direct children of the document
 * element are inspected, so a nested {@code <version>} inside {@code <dependencies>} or
 * {@code <build>} is never mistaken for the project's own version.
 */
public final class PomXml {

    private PomXml() {
    }

    public static PomInfo parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Element project = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();

            String artifactId = null;
            String ownVersion = null;
            String parentArtifactId = null;
            String parentVersion = null;
            Map<String, String> versionProperties = new LinkedHashMap<>();

            for (Node child = project.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof Element element)) {
                    continue;
                }
                switch (element.getTagName()) {
                    case "artifactId" -> artifactId = text(element);
                    case "version" -> ownVersion = text(element);
                    case "parent" -> {
                        parentArtifactId = childText(element, "artifactId");
                        parentVersion = childText(element, "version");
                    }
                    case "properties" -> {
                        for (Node prop = element.getFirstChild(); prop != null; prop = prop.getNextSibling()) {
                            if (prop instanceof Element propertyElement
                                    && propertyElement.getTagName().endsWith(".version")) {
                                String tag = propertyElement.getTagName();
                                String projectName = tag.substring(0, tag.length() - ".version".length());
                                versionProperties.put(projectName, text(propertyElement));
                            }
                        }
                    }
                    default -> {
                        // not relevant to graph derivation or branch resolution
                    }
                }
            }

            return new PomInfo(artifactId, parentArtifactId, ownVersion, parentVersion, versionProperties);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse pom.xml", e);
        }
    }

    private static String text(Element element) {
        String content = element.getTextContent();
        return content == null ? null : content.strip();
    }

    private static String childText(Element parent, String tagName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getTagName().equals(tagName)) {
                return text(element);
            }
        }
        return null;
    }
}
