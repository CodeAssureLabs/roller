/*
 * Copyright 2005 Sun Microsystems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.roller.weblogger.planet.ui;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.RollerException;
import org.apache.roller.planet.business.PlanetManager;
import org.apache.roller.planet.business.fetcher.FeedFetcher;
import org.apache.roller.planet.business.fetcher.FetcherException;
import org.apache.roller.planet.pojos.Planet;
import org.apache.roller.planet.pojos.PlanetGroup;
import org.apache.roller.planet.pojos.Subscription;
import org.apache.roller.planet.util.OpmlFeedListParser;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.pojos.GlobalPermission;
import org.apache.struts2.interceptor.ServletRequestAware;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;


/**
 * Manage planet group subscriptions, default group is "all".
 */
// TODO: make this work @AllowedMethods({"execute","saveSubscription","saveGroup","deleteSubscription"})
public class PlanetGroupSubs extends PlanetUIAction implements ServletRequestAware {

    private static final Log log = LogFactory.getLog(PlanetGroupSubs.class);

    // the planet group we are working in
    private PlanetGroup group = null;

    // the subscription to deal with
    private String subUrl = null;

    private Boolean createNew = null;

    // OPML file uploaded by the "Import OPML" form
    private File opmlFile = null;
    private String opmlFileContentType = null;
    private String opmlFileFileName = null;

    private static final long MAX_OPML_BYTES = 1024 * 1024;

    public PlanetGroupSubs() {
        this.actionName = "planetGroupSubs";
        this.desiredMenu = "admin";
    }


    @Override
    public List<String> requiredGlobalPermissionActions() {
        return Collections.singletonList(GlobalPermission.ADMIN);
    }

    @Override
    public boolean isWeblogRequired() {
        return false;
    }


    @Override
    public void setServletRequest(HttpServletRequest request) {
        if (request.getParameter("createNew") != null) {
            group = new PlanetGroup();
        } else {
            group = getGroupFromRequest(request, getPlanet());
        }
    }


    static PlanetGroup getGroupFromRequest(HttpServletRequest request, Planet planet) {
        PlanetManager pmgr = WebloggerFactory.getWeblogger().getPlanetManager();
        PlanetGroup planetGroup = null;
        String action = null;
        try {
            if (request.getParameter("group.id") != null) {
                String groupId = request.getParameter("group.id");
                action = "looking up planet group by id: " + groupId;
                planetGroup = pmgr.getGroupById(groupId);

            } else if (request.getParameter("group.handle") != null) {
                String groupHandle = request.getParameter("group.handle");
                action = "looking up planet group by handle: " + groupHandle;
                planetGroup = pmgr.getGroup(planet, groupHandle);

            } else {
                action = "getting default group";
                planetGroup = pmgr.getGroup(planet, "all");
            }
        } catch (Exception ex) {
            log.error("Error " + action, ex);
        }
        return planetGroup;
    }

    /**
     * Populate page model and forward to subscription page
     */
    @Override
    public String execute() {
        return LIST;
    }

    /**
     * Save group.
     */
    public String saveGroup() {

        validateGroup();

        if (!hasActionErrors()) {
            try {
                PlanetManager planetManager = WebloggerFactory.getWeblogger().getPlanetManager();

                PlanetGroup existingGroup = planetManager.getGroup(getPlanet(), getGroup().getHandle());

                if (existingGroup == null) {
                    log.debug("Adding New Group: " + getGroup().getHandle());
                    planetManager.saveNewPlanetGroup(getPlanet(), getGroup());

                } else {
                    log.debug("Updating Existing Group: " + existingGroup.getHandle());
                    existingGroup.setTitle( getGroup().getTitle() );
                    existingGroup.setHandle( getGroup().getHandle() );
                    planetManager.saveGroup(existingGroup);
                }

                WebloggerFactory.getWeblogger().flush();
                addMessage("planetGroups.success.saved");

            } catch (Exception ex) {
                log.error("Error saving planet group", ex);
                addError("planetGroups.error.saved");
            }
        }

        return LIST;
    }

    /**
     * Import every feed listed in an uploaded OPML file into the current group.
     */
    public String importOpml() {

        if (opmlFile == null) {
            addError("planetGroupSubs.import.noFile");
            return LIST;
        }

        List<String> feedUrls;
        try {
            if (opmlFile.length() > MAX_OPML_BYTES) {
                addError("planetGroupSubs.import.tooLarge");
                return LIST;
            }
            String opml = new String(Files.readAllBytes(opmlFile.toPath()), StandardCharsets.UTF_8);
            feedUrls = OpmlFeedListParser.parseFeedUrls(opml);
        } catch (IOException | OpmlFeedListParser.OpmlParseException ex) {
            log.debug("Rejected OPML upload " + opmlFileFileName, ex);
            addError("planetGroupSubs.import.invalid", ex.getMessage());
            return LIST;
        }

        PlanetManager pmgr = WebloggerFactory.getWeblogger().getPlanetManager();
        FeedFetcher fetcher = WebloggerFactory.getWeblogger().getFeedFetcher();
        int added = 0;
        List<String> failed = new ArrayList<>();
        for (String feedUrl : feedUrls) {
            try {
                Subscription sub = pmgr.getSubscription(feedUrl);
                if (sub == null) {
                    sub = fetcher.fetchSubscription(feedUrl);
                    pmgr.saveSubscription(sub);
                }
                if (!group.getSubscriptions().contains(sub)) {
                    group.getSubscriptions().add(sub);
                    sub.getGroups().add(group);
                    added++;
                }
            } catch (Exception ex) {
                log.debug("Skipping feed from OPML import: " + feedUrl, ex);
                failed.add(feedUrl);
            }
        }

        try {
            pmgr.saveGroup(group);
            WebloggerFactory.getWeblogger().flush();
        } catch (Exception ex) {
            log.error("Error saving planet group after OPML import", ex);
            addError("planetGroupSubs.error.duringSave", ex.getMessage());
            return LIST;
        }

        addMessage("planetGroupSubs.import.success", Arrays.asList(added, feedUrls.size()));
        if (!failed.isEmpty()) {
            addError("planetGroupSubs.import.someFailed", String.join(", ", failed));
        }
        return LIST;
    }

    /**
     * Validate posted group
     */
    private void validateGroup() {

        if (StringUtils.isEmpty(getGroup().getTitle())) {
            addError("planetGroups.error.title");
        }

        if (StringUtils.isEmpty(getGroup().getHandle())) {
            addError("planetGroups.error.handle");
        }

        if (getGroup().getHandle() != null && "all".equals(getGroup().getHandle())) {
            addError("planetGroups.error.nameReserved");
        }

        // make sure duplicate group handles are prevented
    }


    /**
     * Save subscription, add to current group
     */
    public String saveSubscription() {

        valudateNewSub();

        if (!hasActionErrors()) {
            try {
                PlanetManager pmgr = WebloggerFactory.getWeblogger().getPlanetManager();

                // check if this subscription already exists before adding it
                Subscription sub = pmgr.getSubscription(getSubUrl());
                if (sub == null) {
                    log.debug("Adding New Subscription - " + getSubUrl());

                    // sub doesn't exist yet, so we need to fetch it
                    FeedFetcher fetcher = WebloggerFactory.getWeblogger().getFeedFetcher();
                    sub = fetcher.fetchSubscription(getSubUrl());

                    // save new sub
                    pmgr.saveSubscription(sub);

                } else {
                    // Subscription already exists
                    log.debug("Adding Existing Subscription - " + getSubUrl());
                }

                // add the sub to the group
                group.getSubscriptions().add(sub);
                sub.getGroups().add(group);
                pmgr.saveGroup(group);
                WebloggerFactory.getWeblogger().flush();

                // clear field after success
                setSubUrl(null);

                addMessage("planetSubscription.success.saved");

            } catch (FetcherException ex) {
                addError("planetGroupSubs.error.fetchingFeed", ex.getRootCauseMessage());

            } catch (RollerException ex) {
                log.error("Unexpected error saving subscription", ex);
                addError("planetGroupSubs.error.duringSave", ex.getRootCauseMessage());
            }
        }

        return LIST;
    }


    /**
     * Delete subscription, reset form
     */
    public String deleteSubscription() {

        if (getSubUrl() != null) {
            try {

                PlanetManager pmgr = WebloggerFactory.getWeblogger().getPlanetManager();

                Subscription sub = pmgr.getSubscription(getSubUrl());

                // remove sub from group
                getGroup().getSubscriptions().remove(sub);
                pmgr.saveGroup(getGroup());

                // remove group from sub
                sub.getGroups().remove(getGroup());
                pmgr.saveSubscription(sub);

                WebloggerFactory.getWeblogger().flush();

                // clear field after success
                setSubUrl(null);

                addMessage("planetSubscription.success.deleted");

            } catch (RollerException ex) {
                log.error("Error removing planet subscription", ex);
                addError("planetSubscription.error.deleting");
            }
        }

        return LIST;
    }


    /**
     * Validate posted subscription
     */
    private void valudateNewSub() {

        if (StringUtils.isEmpty(getSubUrl())) {
            addError("planetSubscription.error.feedUrl");
        }
    }


    @Override
    public String getPageTitle() {
        if (pageTitle == null) {
            if (getCreateNew()) {
                pageTitle = getText("planetGroupSubs.custom.title.new");
            } else if (getGroup().getHandle().equals("all")) {
                pageTitle = getText("planetGroupSubs.default.title");
            } else {
                pageTitle = getText("planetGroupSubs.custom.title", new String[]{getGroup().getHandle()});
            }
        }
        return pageTitle;
    }


    public List<Subscription> getSubscriptions() {

        List<Subscription> subs = Collections.emptyList();
        if (getGroup() != null) {
            Set<Subscription> subsSet = getGroup().getSubscriptions();

            // iterate over list and build display list
            subs = new ArrayList<>();
            for (Subscription sub : subsSet) {
                // only include external subs for display
                if (!sub.getFeedURL().startsWith("weblogger:")) {
                    subs.add(sub);
                }
            }
        }

        return subs;
    }

    public PlanetGroup getGroup() {
        return group;
    }

    public void setGroup(PlanetGroup group) {
        this.group = group;
    }

    public File getOpmlFile() {
        return opmlFile;
    }

    public void setOpmlFile(File opmlFile) {
        this.opmlFile = opmlFile;
    }

    public String getOpmlFileContentType() {
        return opmlFileContentType;
    }

    public void setOpmlFileContentType(String opmlFileContentType) {
        this.opmlFileContentType = opmlFileContentType;
    }

    public String getOpmlFileFileName() {
        return opmlFileFileName;
    }

    public void setOpmlFileFileName(String opmlFileFileName) {
        this.opmlFileFileName = opmlFileFileName;
    }

    public String getSubUrl() {
        return subUrl;
    }

    public void setSubUrl(String subUrl) {
        this.subUrl = subUrl;
    }

    public boolean getCreateNew() {
        if (createNew == null) {
            PlanetManager pmgr = WebloggerFactory.getWeblogger().getPlanetManager();
            PlanetGroup existingGroup = null;
            try {
                existingGroup = pmgr.getGroupById(group.getId());
            } catch (RollerException e) {
                log.error("Error getting group by ID", e);
            }
            createNew = (existingGroup == null);
        }
        return createNew;
    }

    public void setCreateNew(boolean createNew) {
        // no op
    }

    public String getGroupHandle() {
        return group.getHandle();
    }

}


