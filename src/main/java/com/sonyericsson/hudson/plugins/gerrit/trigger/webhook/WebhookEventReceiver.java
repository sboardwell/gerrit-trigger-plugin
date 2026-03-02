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

import com.google.gson.JsonSyntaxException;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * REST endpoint for receiving webhook events from Gerrit.
 * Provides an alternative to SSH connections for receiving Gerrit events.
 * CSRF protection is handled by WebhookCrumbExclusion (separate extension).
 * Security is enforced via WebhookAuthenticator (tokens, HMAC, IP filtering).
 *
 * This class must be SEPARATE from WebhookCrumbExclusion.
 * Both classes have @Extension and are registered independently by Jenkins.
 *
 * @author Your Name &lt;your.email@domain.com&gt;
 */
@Extension
public class WebhookEventReceiver extends WebhookCrumbExclusion implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(WebhookEventReceiver.class.getName());

    /** The URL name for accessing the webhook endpoint. */
    public static final String URL_NAME = "gerrit-webhook";

    private final WebhookEventProcessor eventProcessor;
    private final WebhookAuthenticator authenticator;

    /**
     * Default constructor.
     */
    public WebhookEventReceiver() {
        this.eventProcessor = new WebhookEventProcessor();
        this.authenticator = new WebhookAuthenticator();
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return URL_NAME;
    }

    /**
     * Handles webhook POST requests from Gerrit.
     * URL pattern: /gerrit-webhook/ (with trailing slash)
     * This is called by Stapler when a request arrives at the root of our action.
     * Following BitbucketHookReceiver pattern - single parameter.
     *
     * @param req the HTTP request
     * @throws IOException if I/O error occurs
     */
    public void doIndex(StaplerRequest req) throws IOException {
        // Get response from Stapler context
        StaplerResponse rsp = org.kohsuke.stapler.Stapler.getCurrentResponse();

        // Only handle POST requests
        if (!"POST".equals(req.getMethod())) {
            LOGGER.log(Level.FINE, "Rejecting non-POST request: {0}", req.getMethod());
            if (rsp != null) {
                rsp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "Only POST method is supported for webhook events");
            }
            return;
        }

        handleWebhookEvent(req, rsp);
    }

    /**
     * Processes the webhook event.
     *
     * @param req the HTTP request
     * @param rsp the HTTP response
     * @throws IOException if I/O error occurs
     */
    private void handleWebhookEvent(HttpServletRequest req, HttpServletResponse rsp)
            throws IOException {
        LOGGER.log(Level.FINE, "handleWebhookEvent() - processing request from {0}", req.getRemoteAddr());
        try {
            // Read the payload first
            String payload = IOUtils.toString(req.getReader());
            if (payload == null || payload.trim().isEmpty()) {
                sendError(rsp, HttpServletResponse.SC_BAD_REQUEST,
                         "Empty webhook payload");
                return;
            }

            // Identify the server from the payload
            GerritServer server = identifyServerFromPayload(payload);
            if (server == null) {
                sendError(rsp, HttpServletResponse.SC_NOT_FOUND,
                         "No matching Gerrit server configured for webhook");
                return;
            }

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Webhook request received for server {0}", server.getName());
                LOGGER.log(Level.FINE, "Webhook payload: {0}", payload);
                LOGGER.log(Level.FINE, "Request headers: {0}", formatHeaders(req));
            }

            // Check if webhooks are enabled for this server
            if (!isWebhookEnabled(server)) {
                sendError(rsp, HttpServletResponse.SC_FORBIDDEN,
                         "Webhooks are not enabled for server: " + server.getName());
                return;
            }

            // Authenticate the request
            if (!authenticator.authenticate(req, server, payload)) {
                sendError(rsp, HttpServletResponse.SC_UNAUTHORIZED,
                         "Webhook authentication failed");
                return;
            }

            // Process the webhook payload
            GerritEvent event = eventProcessor.processWebhookPayload(payload, server);
            if (event == null) {
                sendError(rsp, HttpServletResponse.SC_BAD_REQUEST,
                         "Unable to process webhook payload");
                return;
            }

            // Inject the event into the existing Gerrit event handling pipeline
            injectEventToServer(server, event);

            // Send success response
            rsp.setStatus(HttpServletResponse.SC_OK);
            rsp.setContentType("application/json");
            rsp.getWriter().write("{\"status\":\"success\",\"message\":\"Event processed successfully\"}");

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Successfully processed webhook event {0} for server {1}",
                          new Object[]{event.getClass().getSimpleName(), server.getName()});
            }

        } catch (JsonSyntaxException e) {
            LOGGER.log(Level.WARNING, "Invalid JSON in webhook payload", e);
            sendError(rsp, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON payload: " + e.getMessage());

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing webhook", e);
            sendError(rsp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                     "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Identifies the appropriate GerritServer from the webhook payload.
     * Attempts to match by change URL first, then falls back to first webhook-enabled server.
     *
     * @param payload the webhook JSON payload
     * @return the matching GerritServer instance, or null if not found
     */
    private GerritServer identifyServerFromPayload(String payload) {
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin == null || plugin.getServers() == null || plugin.getServers().isEmpty()) {
            LOGGER.log(Level.WARNING, "No Gerrit servers configured");
            return null;
        }
        String changeUrl = null;
        try {
            // Extract change URL from payload if available
            changeUrl = extractChangeUrl(payload);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse payload for server identification", e);
            return null;
        }

        boolean serverWithWebhookConfiguredFound = false;
        if (changeUrl != null && !changeUrl.isEmpty()) {
            for (GerritServer server : plugin.getServers()) {
                if (server.getConfig() instanceof com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config) {
                    com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config config =
                            (com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config)server.getConfig();
                    if (config.getConnectionType()
                            == com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config.ConnectionType.WEBHOOK) {
                        serverWithWebhookConfiguredFound = true;
                        String frontendUrl = server.getFrontEndUrl();
                        if (frontendUrl != null && changeUrl.startsWith(frontendUrl)) {
                            if (LOGGER.isLoggable(Level.FINE)) {
                                LOGGER.log(Level.FINE,
                                        "Matched webhook to server {0} by Frontend URL: {1}",
                                        new Object[]{server.getName(), frontendUrl});
                            }
                            return server;
                        }
                    }
                }
            }
            if (!serverWithWebhookConfiguredFound) {
                LOGGER.log(Level.WARNING,
                        "No server configured for webhook mode. Please configure at least one server "
                                + "with connection type set to WEBHOOK to receive webhook events.");
            } else {
                LOGGER.log(Level.WARNING,
                        "No server matched payload's changeUrl");
            }
            return null;

        } else {
            LOGGER.log(Level.WARNING,
                    "ChangeURL is null or empty, and it is mandatory. The server cannot be identified.");
            return null;
        }
    }

    /**
     * Extracts the change URL from the webhook payload.
     *
     * @param payload the JSON payload
     * @return the change URL, or null if not found
     */
    private String extractChangeUrl(String payload) {
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(payload).getAsJsonObject();

            // Check for change.url (most event types)
            if (json.has("change")) {
                com.google.gson.JsonObject change = json.getAsJsonObject("change");
                if (change.has("url")) {
                    return change.get("url").getAsString();
                }
            }

            return null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not extract change URL from payload", e);
            return null;
        }
    }

    /**
     * Checks if webhooks are enabled for the given server.
     * Validates that the server is configured for webhook connection type
     * (not SSH mode) and that webhook configuration is enabled.
     *
     * @param server the Gerrit server
     * @return true if webhooks are enabled, false otherwise
     */
    private boolean isWebhookEnabled(GerritServer server) {
        if (server == null) {
            LOGGER.log(Level.WARNING, "Cannot check webhook status - server is null");
            return false;
        }

        // Check if server config is available
        if (server.getConfig() == null) {
            LOGGER.log(Level.WARNING, "Cannot check webhook status - server config is null for server {0}",
                      server.getName());
            return false;
        }

        // Check if config is a Config instance (not just the interface)
        if (!(server.getConfig() instanceof com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config)) {
            LOGGER.log(Level.WARNING, "Server {0} config is not a Config instance, cannot check connection type",
                      server.getName());
            return false;
        }

        com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config config =
                (com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config)server.getConfig();

        // Check if server is configured for webhook mode (not SSH)
        if (config.getConnectionType()
                != com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config.ConnectionType.WEBHOOK) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,
                        "Server {0} is not configured for webhook mode (current mode: {1}). "
                                + "Webhooks can only be received when connection type is set to WEBHOOK.",
                        new Object[]{server.getName(), config.getConnectionType()});
            }
            return false;
        }

        // Webhook mode is enabled - webhookConfig is only required for authentication
        // If webhookConfig is null, it means webhook mode without authentication, which is valid
        String authStatus = "disabled";
        if (config.getWebhookConfig() != null) {
            authStatus = "enabled";
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Webhook validation passed for server {0} (authentication: {1})",
                    new Object[]{server.getName(), authStatus});
        }
        return true;
    }

    /**
     * Injects a processed Gerrit event into the server's event handling pipeline.
     *
     * @param server the Gerrit server
     * @param event the Gerrit event to inject
     */
    private void injectEventToServer(GerritServer server, GerritEvent event) {
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            // Use the server's existing triggerEvent method to inject the event
            // This ensures it goes through the same pipeline as SSH events
            server.triggerEvent(event);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to inject webhook event into server pipeline", e);
            throw new RuntimeException("Failed to process webhook event", e);
        }
    }

    /**
     * Sends an error response.
     *
     * @param rsp the HTTP response
     * @param statusCode the HTTP status code
     * @param message the error message
     * @throws IOException if I/O error occurs
     */
    private void sendError(HttpServletResponse rsp, int statusCode, String message) throws IOException {
        rsp.setStatus(statusCode);
        rsp.setContentType("application/json");
        rsp.getWriter().write(String.format(
            "{\"status\":\"error\",\"message\":\"%s\"}",
            message.replace("\"", "\\\"")
        ));
    }

    /**
     * Formats HTTP request headers for logging.
     *
     * @param req the HTTP request
     * @return formatted string of headers
     */
    private String formatHeaders(HttpServletRequest req) {
        StringBuilder headers = new StringBuilder();
        java.util.Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = req.getHeader(headerName);
            headers.append(headerName).append(": ").append(headerValue).append("; ");
        }
        return headers.toString();
    }
}
