/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.planet.util;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Extracts feed URLs from an OPML subscription list, as exported by feed
 * readers such as Feedly, Inoreader or NetNewsWire.
 */
public final class OpmlFeedListParser {

    /** Upper bound on feeds taken from one file, to keep imports bounded. */
    public static final int MAX_FEEDS = 200;

    private OpmlFeedListParser() {
    }

    /**
     * @param opml OPML document text
     * @return distinct http(s) feed URLs in document order, at most {@link #MAX_FEEDS}
     */
    public static List<String> parseFeedUrls(String opml) throws OpmlParseException {
        Document doc;
        try {
            doc = newDocumentBuilder().parse(new InputSource(new StringReader(opml)));
        } catch (SAXException | IOException e) {
            throw new OpmlParseException("Not a valid OPML document: " + e.getMessage(), e);
        }

        Element root = doc.getDocumentElement();
        if (root == null || !"opml".equalsIgnoreCase(root.getNodeName())) {
            throw new OpmlParseException("Not a valid OPML document: missing <opml> root element", null);
        }

        Set<String> feeds = new LinkedHashSet<>();
        NodeList outlines = root.getElementsByTagName("outline");
        for (int i = 0; i < outlines.getLength() && feeds.size() < MAX_FEEDS; i++) {
            Element outline = (Element) outlines.item(i);
            String url = outline.getAttribute("xmlUrl");
            if (url.isEmpty()) {
                // some exporters use the older "url" attribute
                url = outline.getAttribute("url");
            }
            if (isHttpUrl(url)) {
                feeds.add(url.trim());
            }
        }
        return new ArrayList<>(feeds);
    }

    private static DocumentBuilder newDocumentBuilder() throws OpmlParseException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.setIgnoringComments(true);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new OpmlParseException("XML parser is not available", e);
        }
    }

    private static boolean isHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String scheme = new URI(url.trim()).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** Raised when the uploaded file is not usable OPML. */
    public static class OpmlParseException extends Exception {
        public OpmlParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
