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


import com.sonymobile.tools.gerrit.gerritevents.GerritJsonEventFactory;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
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

    /**
     * Default constructor.
     */
    public WebhookEventProcessor() {
        // Constructor
    }

    /**
     * Processes a webhook payload and converts it to a GerritEvent.
     * Uses GerritJsonEventFactory to parse the JSON payload into the appropriate event type.
     *
     * @param payload the JSON webhook payload from Gerrit
     * @param server the Gerrit server configuration
     * @return the processed GerritEvent, or null if processing failed
     */
    public GerritEvent processWebhookPayload(String payload, GerritServer server) {

        if (payload == null || payload.trim().isEmpty()) {
            LOGGER.warning("Empty webhook payload received");
            return null;
        }

        try {
            // Use the factory to parse and create the event - same as SSH events
            GerritEvent event = GerritJsonEventFactory.getEventIfInteresting(payload);

            if (event == null) {
                LOGGER.warning("Payload does not represent an interesting/usable Gerrit event");
                return null;
            }

            LOGGER.log(Level.FINE, "Successfully parsed webhook event of type: {0}",
                      event.getClass().getSimpleName());

            // Set the provider information on the event
            setProviderInfo(event, server);

            LOGGER.log(Level.INFO, "Successfully processed webhook event: {0}",
                      event.getClass().getSimpleName());

            return event;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing webhook payload", e);
            return null;
        }
    }


    /**
     * Sets provider information on the event.
     * Following the same pattern as ManualTriggerAction.createProvider().
     * The Provider contains server metadata needed for event processing.
     *
     * @param event the Gerrit event to update
     * @param server the Gerrit server configuration
     */
    private void setProviderInfo(GerritEvent event, GerritServer server) {
        if (!(event instanceof GerritTriggeredEvent)) {
            LOGGER.log(Level.WARNING, "Event {0} is not a GerritTriggeredEvent, cannot set provider",
                    event.getClass().getSimpleName());
            return;
        }

        // Create provider using same pattern as ManualTriggerAction.createProvider()
        Provider provider = new Provider(
                server.getName(),
                server.getConfig().getGerritHostName(),
                String.valueOf(server.getConfig().getGerritSshPort()),
                com.sonymobile.tools.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_SCHEME,
                server.getConfig().getGerritFrontEndUrl(),
                server.getGerritVersion()
        );

        ((GerritTriggeredEvent)event).setProvider(provider);

        LOGGER.log(Level.FINE, "Set provider {0} on event {1}",
                new Object[]{provider.getName(), event.getClass().getSimpleName()});
    }
}
