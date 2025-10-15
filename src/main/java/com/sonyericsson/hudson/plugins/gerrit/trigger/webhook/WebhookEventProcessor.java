/*
 *  The MIT License
 *
 *  Copyright (c) 2025, CloudBees, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.webhook;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Account;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeRestored;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefUpdated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processes webhook payloads from Gerrit and converts them to GerritEvent objects.
 * Handles the conversion of JSON webhook payloads to the internal event format
 * used by the Gerrit Trigger plugin.
 *
 * @author Your Name &lt;your.email@domain.com&gt;
 */
public class WebhookEventProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebhookEventProcessor.class.getName());

    /** Conversion factor from seconds to milliseconds. */
    private static final int SECONDS_TO_MILLISECONDS = 1000;

    /**
     * Default constructor.
     */
    public WebhookEventProcessor() {
        // Constructor
    }

    /**
     * Processes a webhook payload and converts it to a GerritEvent.
     *
     * @param payload the JSON webhook payload from Gerrit
     * @param server the Gerrit server configuration
     * @return the processed GerritEvent, or null if processing failed
     * @throws JsonSyntaxException if the payload is not valid JSON
     */
    public GerritEvent processWebhookPayload(String payload, GerritServer server)
            throws JsonSyntaxException {

        if (payload == null || payload.trim().isEmpty()) {
            LOGGER.warning("Empty webhook payload received");
            return null;
        }

        try {
            JsonObject jsonEvent = JsonParser.parseString(payload).getAsJsonObject();

            // Determine the event type
            String eventType = getEventType(jsonEvent);
            if (eventType == null) {
                LOGGER.warning("Unable to determine event type from webhook payload");
                return null;
            }

            LOGGER.log(Level.FINE, "Processing webhook event of type: {0}", eventType);

            // Convert to appropriate GerritEvent based on type
            GerritEvent event = convertToGerritEvent(jsonEvent, eventType, server);

            if (event != null) {
                // Set the provider information
                setProviderInfo(event, server);
                LOGGER.log(Level.INFO, "Successfully converted webhook payload to {0}",
                          event.getClass().getSimpleName());
            }

            return event;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing webhook payload", e);
            return null;
        }
    }

    /**
     * Determines the event type from the JSON payload.
     *
     * @param jsonEvent the JSON event object
     * @return the event type string, or null if not found
     */
    private String getEventType(JsonObject jsonEvent) {
        if (jsonEvent.has("type")) {
            return jsonEvent.get("type").getAsString();
        }

        // Fallback: try to infer from structure
        if (jsonEvent.has("patchSet") && jsonEvent.has("change")) {
            if (jsonEvent.has("comment")) {
                return "comment-added";
            } else {
                return "patchset-created";
            }
        } else if (jsonEvent.has("change") && jsonEvent.has("submitter")) {
            return "change-merged";
        } else if (jsonEvent.has("refUpdate")) {
            return "ref-updated";
        }

        return null;
    }

    /**
     * Converts a JSON event to the appropriate GerritEvent object.
     *
     * @param jsonEvent the JSON event
     * @param eventType the event type
     * @param server the Gerrit server
     * @return the converted GerritEvent, or null if conversion failed
     */
    private GerritEvent convertToGerritEvent(JsonObject jsonEvent, String eventType, GerritServer server) {
        try {
            switch (eventType.toLowerCase()) {
                case "patchset-created":
                    return convertToPatchsetCreated(jsonEvent);
                case "comment-added":
                    return convertToCommentAdded(jsonEvent);
                case "change-merged":
                    return convertToChangeMerged(jsonEvent);
                case "change-abandoned":
                    return convertToChangeAbandoned(jsonEvent);
                case "change-restored":
                    return convertToChangeRestored(jsonEvent);
                case "ref-updated":
                    return convertToRefUpdated(jsonEvent);
                default:
                    LOGGER.log(Level.WARNING, "Unsupported event type: {0}", eventType);
                    return null;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error converting " + eventType + " event", e);
            return null;
        }
    }

    /**
     * Converts JSON to PatchsetCreated event.
     *
     * @param jsonEvent the JSON event object
     * @return the PatchsetCreated event
     */
    private PatchsetCreated convertToPatchsetCreated(JsonObject jsonEvent) {
        PatchsetCreated event = new PatchsetCreated();

        // Set basic event properties
        setCommonEventProperties(event, jsonEvent);

        // Set change information
        if (jsonEvent.has("change")) {
            Change change = convertChange(jsonEvent.getAsJsonObject("change"));
            event.setChange(change);
        }

        // Set patchset information
        if (jsonEvent.has("patchSet")) {
            PatchSet patchSet = convertPatchSet(jsonEvent.getAsJsonObject("patchSet"));
            event.setPatchset(patchSet);
        }

        // Set uploader information - handled in patchset
        if (jsonEvent.has("uploader")) {
            // Uploader is typically in the patchset, not the main event
            LOGGER.fine("Uploader information found in event (handled via patchset)");
        }

        return event;
    }

    /**
     * Converts JSON to CommentAdded event.
     *
     * @param jsonEvent the JSON event object
     * @return the CommentAdded event
     */
    private CommentAdded convertToCommentAdded(JsonObject jsonEvent) {
        CommentAdded event = new CommentAdded();

        setCommonEventProperties(event, jsonEvent);

        if (jsonEvent.has("change")) {
            event.setChange(convertChange(jsonEvent.getAsJsonObject("change")));
        }

        if (jsonEvent.has("patchSet")) {
            event.setPatchset(convertPatchSet(jsonEvent.getAsJsonObject("patchSet")));
        }

        if (jsonEvent.has("author")) {
            event.setAccount(convertAccount(jsonEvent.getAsJsonObject("author")));
        }

        if (jsonEvent.has("comment")) {
            event.setComment(jsonEvent.get("comment").getAsString());
        }

        // Handle approvals if present
        if (jsonEvent.has("approvals")) {
            // TODO: Convert approvals array to appropriate format
            LOGGER.fine("Approvals found in comment-added event (conversion not yet implemented)");
        }

        return event;
    }

    /**
     * Converts JSON to ChangeMerged event.
     *
     * @param jsonEvent the JSON event object
     * @return the ChangeMerged event
     */
    private ChangeMerged convertToChangeMerged(JsonObject jsonEvent) {
        ChangeMerged event = new ChangeMerged();

        setCommonEventProperties(event, jsonEvent);

        if (jsonEvent.has("change")) {
            event.setChange(convertChange(jsonEvent.getAsJsonObject("change")));
        }

        if (jsonEvent.has("patchSet")) {
            event.setPatchset(convertPatchSet(jsonEvent.getAsJsonObject("patchSet")));
        }

        if (jsonEvent.has("submitter")) {
            event.setAccount(convertAccount(jsonEvent.getAsJsonObject("submitter")));
        }

        return event;
    }

    /**
     * Converts JSON to ChangeAbandoned event.
     *
     * @param jsonEvent the JSON event object
     * @return the ChangeAbandoned event
     */
    private ChangeAbandoned convertToChangeAbandoned(JsonObject jsonEvent) {
        ChangeAbandoned event = new ChangeAbandoned();

        setCommonEventProperties(event, jsonEvent);

        if (jsonEvent.has("change")) {
            event.setChange(convertChange(jsonEvent.getAsJsonObject("change")));
        }

        if (jsonEvent.has("patchSet")) {
            event.setPatchset(convertPatchSet(jsonEvent.getAsJsonObject("patchSet")));
        }

        if (jsonEvent.has("abandoner")) {
            event.setAccount(convertAccount(jsonEvent.getAsJsonObject("abandoner")));
        }

        if (jsonEvent.has("reason")) {
            // TODO: Set abandon reason when API supports it
            LOGGER.fine("Abandon reason available but not yet supported by API");
        }

        return event;
    }

    /**
     * Converts JSON to ChangeRestored event.
     *
     * @param jsonEvent the JSON event object
     * @return the ChangeRestored event
     */
    private ChangeRestored convertToChangeRestored(JsonObject jsonEvent) {
        ChangeRestored event = new ChangeRestored();

        setCommonEventProperties(event, jsonEvent);

        if (jsonEvent.has("change")) {
            event.setChange(convertChange(jsonEvent.getAsJsonObject("change")));
        }

        if (jsonEvent.has("patchSet")) {
            event.setPatchset(convertPatchSet(jsonEvent.getAsJsonObject("patchSet")));
        }

        if (jsonEvent.has("restorer")) {
            event.setAccount(convertAccount(jsonEvent.getAsJsonObject("restorer")));
        }

        if (jsonEvent.has("reason")) {
            // TODO: Set restore reason when API supports it
            LOGGER.fine("Restore reason available but not yet supported by API");
        }

        return event;
    }

    /**
     * Converts JSON to RefUpdated event.
     *
     * @param jsonEvent the JSON event object
     * @return the RefUpdated event
     */
    private RefUpdated convertToRefUpdated(JsonObject jsonEvent) {
        RefUpdated event = new RefUpdated();

        setCommonEventProperties(event, jsonEvent);

        // TODO: Implement RefUpdated conversion
        LOGGER.warning("RefUpdated event conversion not yet fully implemented");

        return event;
    }

    /**
     * Sets common properties on all GerritEvent objects.
     *
     * @param event the Gerrit event to update
     * @param jsonEvent the JSON event object
     */
    private void setCommonEventProperties(GerritEvent event, JsonObject jsonEvent) {
        if (jsonEvent.has("eventCreatedOn")) {
            long timestamp = jsonEvent.get("eventCreatedOn").getAsLong();
            // TODO: Set timestamp when GerritEvent API supports it
            LOGGER.fine("Event timestamp found: " + timestamp);
        }
        // Note: GerritEvent doesn't have a standard setEventCreatedOn method
        // Different event types handle timestamps differently
    }

    /**
     * Converts a JSON change object to a Change object.
     *
     * @param changeJson the JSON change object
     * @return the converted Change object
     */
    private Change convertChange(JsonObject changeJson) {
        Change change = new Change();

        if (changeJson.has("project")) {
            change.setProject(changeJson.get("project").getAsString());
        }

        if (changeJson.has("branch")) {
            change.setBranch(changeJson.get("branch").getAsString());
        }

        if (changeJson.has("id")) {
            change.setId(changeJson.get("id").getAsString());
        }

        if (changeJson.has("number")) {
            // Use deprecated method for now - TODO: update when API changes
            String number = changeJson.get("number").getAsString();
            change.setNumber(number);
        }

        if (changeJson.has("subject")) {
            change.setSubject(changeJson.get("subject").getAsString());
        }

        if (changeJson.has("url")) {
            change.setUrl(changeJson.get("url").getAsString());
        }

        if (changeJson.has("owner")) {
            change.setOwner(convertAccount(changeJson.getAsJsonObject("owner")));
        }

        return change;
    }

    /**
     * Converts a JSON patchset object to a PatchSet object.
     *
     * @param patchSetJson the JSON patchset object
     * @return the converted PatchSet object
     */
    private PatchSet convertPatchSet(JsonObject patchSetJson) {
        PatchSet patchSet = new PatchSet();

        if (patchSetJson.has("number")) {
            patchSet.setNumber(patchSetJson.get("number").getAsString());
        }

        if (patchSetJson.has("revision")) {
            patchSet.setRevision(patchSetJson.get("revision").getAsString());
        }

        if (patchSetJson.has("ref")) {
            patchSet.setRef(patchSetJson.get("ref").getAsString());
        }

        if (patchSetJson.has("uploader")) {
            patchSet.setUploader(convertAccount(patchSetJson.getAsJsonObject("uploader")));
        }

        if (patchSetJson.has("createdOn")) {
            long timestamp = patchSetJson.get("createdOn").getAsLong();
            patchSet.setCreatedOn(new Date(timestamp * SECONDS_TO_MILLISECONDS));
        }

        return patchSet;
    }

    /**
     * Converts a JSON account object to an Account object.
     *
     * @param accountJson the JSON account object
     * @return the converted Account object
     */
    private Account convertAccount(JsonObject accountJson) {
        Account account = new Account();

        if (accountJson.has("name")) {
            account.setName(accountJson.get("name").getAsString());
        }

        if (accountJson.has("email")) {
            account.setEmail(accountJson.get("email").getAsString());
        }

        if (accountJson.has("username")) {
            account.setUsername(accountJson.get("username").getAsString());
        }

        return account;
    }

    /**
     * Sets provider information on the event.
     *
     * @param event the Gerrit event to update
     * @param server the Gerrit server configuration
     */
    private void setProviderInfo(GerritEvent event, GerritServer server) {
        Provider provider = new Provider();
        provider.setName(server.getName());
        provider.setHost(server.getConfig().getGerritHostName());
        provider.setPort(String.valueOf(server.getConfig().getGerritSshPort()));
        provider.setScheme("webhook"); // Mark this as a webhook-originated event
        provider.setUrl(server.getConfig().getGerritFrontEndUrl());
        provider.setVersion(server.getGerritVersion());

        // Set provider on specific event types that support it
        if (event instanceof com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent) {
            ((com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent)event).setProvider(provider);
        }
    }
}
