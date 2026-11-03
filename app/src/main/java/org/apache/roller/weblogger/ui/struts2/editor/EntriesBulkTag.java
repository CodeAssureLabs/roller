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

package org.apache.roller.weblogger.ui.struts2.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.pojos.WeblogPermission;
import org.apache.roller.weblogger.ui.struts2.util.UIAction;
import org.apache.roller.weblogger.util.cache.CacheManager;

/**
 * Add or remove tags on several weblog entries at once from the Entries list.
 */
public class EntriesBulkTag extends UIAction {

    private static final Log log = LogFactory.getLog(EntriesBulkTag.class);

    private static final int MAX_ENTRIES = 100;

    // ids of the entries ticked in the Entries list
    private String[] selectedEntries = new String[0];

    // space separated tag names, same format as the entry editor
    private String addTags = null;
    private String removeTags = null;

    public EntriesBulkTag() {
        this.actionName = "entriesBulkTag";
        this.desiredMenu = "editor";
        this.pageTitle = "weblogEntryQuery.title";
    }

    @Override
    public List<String> requiredWeblogPermissionActions() {
        return Collections.singletonList(WeblogPermission.POST);
    }

    // the entry ids identify the entries to update, the weblog param is only
    // used to return to the right Entries page afterwards
    @Override
    public boolean isWeblogRequired() {
        return false;
    }

    @Override
    public String execute() {

        List<String> toAdd = splitTags(addTags);
        List<String> toRemove = splitTags(removeTags);

        if (selectedEntries.length == 0 || (toAdd.isEmpty() && toRemove.isEmpty())) {
            addError("entriesBulkTag.nothingToDo");
            return SUCCESS;
        }
        if (selectedEntries.length > MAX_ENTRIES) {
            addError("entriesBulkTag.tooMany", String.valueOf(MAX_ENTRIES));
            return SUCCESS;
        }

        WeblogEntryManager wmgr = WebloggerFactory.getWeblogger().getWeblogEntryManager();
        int updated = 0;
        try {
            for (String entryId : selectedEntries) {
                WeblogEntry entry = wmgr.getWeblogEntry(entryId);
                if (entry == null) {
                    continue;
                }
                for (String tag : toAdd) {
                    entry.addTag(tag);
                }
                if (!toRemove.isEmpty()) {
                    List<String> remaining = new ArrayList<>(Arrays.asList(
                            StringUtils.split(StringUtils.defaultString(entry.getTagsAsString()), ' ')));
                    remaining.removeAll(toRemove);
                    entry.setTagsAsString(String.join(" ", remaining));
                }
                wmgr.saveWeblogEntry(entry);
                CacheManager.invalidate(entry);
                updated++;
            }
            WebloggerFactory.getWeblogger().flush();
            addMessage("entriesBulkTag.updated", String.valueOf(updated));
        } catch (WebloggerException ex) {
            log.error("Error bulk tagging entries", ex);
            addError("generic.error.check.logs");
        }
        return SUCCESS;
    }

    private static List<String> splitTags(String tags) {
        if (StringUtils.isBlank(tags)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String tag : StringUtils.split(tags.toLowerCase(), " ,")) {
            if (StringUtils.isNotBlank(tag)) {
                result.add(tag.trim());
            }
        }
        return result;
    }

    public String[] getSelectedEntries() {
        return selectedEntries;
    }

    public void setSelectedEntries(String[] selectedEntries) {
        this.selectedEntries = selectedEntries == null ? new String[0] : selectedEntries;
    }

    public String getAddTags() {
        return addTags;
    }

    public void setAddTags(String addTags) {
        this.addTags = addTags;
    }

    public String getRemoveTags() {
        return removeTags;
    }

    public void setRemoveTags(String removeTags) {
        this.removeTags = removeTags;
    }
}
