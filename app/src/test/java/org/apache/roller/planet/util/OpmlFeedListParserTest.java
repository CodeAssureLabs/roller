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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpmlFeedListParserTest {

    @Test
    void readsNestedOutlinesInOrderAndDeduplicates() throws Exception {
        String opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<opml version=\"2.0\"><head><title>My feeds</title></head><body>"
                + "<outline text=\"Tech\">"
                + "  <outline text=\"A\" type=\"rss\" xmlUrl=\"https://a.example.org/feed\" htmlUrl=\"https://a.example.org\"/>"
                + "  <outline text=\"B\" type=\"rss\" url=\"http://b.example.org/rss.xml\"/>"
                + "</outline>"
                + "<outline text=\"A again\" xmlUrl=\"https://a.example.org/feed\"/>"
                + "</body></opml>";
        assertEquals(List.of("https://a.example.org/feed", "http://b.example.org/rss.xml"),
                OpmlFeedListParser.parseFeedUrls(opml));
    }

    @Test
    void skipsNonHttpUrls() throws Exception {
        String opml = "<opml version=\"1.0\"><body>"
                + "<outline text=\"local\" xmlUrl=\"file:///etc/hosts\"/>"
                + "<outline text=\"js\" xmlUrl=\"javascript:alert(1)\"/>"
                + "<outline text=\"ok\" xmlUrl=\"https://ok.example.org/atom\"/>"
                + "</body></opml>";
        assertEquals(List.of("https://ok.example.org/atom"), OpmlFeedListParser.parseFeedUrls(opml));
    }

    @Test
    void rejectsNonOpmlDocuments() {
        assertThrows(OpmlFeedListParser.OpmlParseException.class,
                () -> OpmlFeedListParser.parseFeedUrls("<rss version=\"2.0\"><channel/></rss>"));
        assertThrows(OpmlFeedListParser.OpmlParseException.class,
                () -> OpmlFeedListParser.parseFeedUrls("not xml at all"));
    }
}
