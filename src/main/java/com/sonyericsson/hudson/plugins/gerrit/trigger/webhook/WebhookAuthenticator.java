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

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import java.nio.charset.StandardCharsets;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;

/**
 * Handles authentication and security validation for webhook requests.
 * Supports multiple authentication methods including secret tokens,
 * HMAC signatures, and IP address whitelisting.
 *
 * @author Your Name &lt;your.email@domain.com&gt;
 */
public class WebhookAuthenticator {

    private static final Logger LOGGER = Logger.getLogger(WebhookAuthenticator.class.getName());

    // HTTP headers commonly used for webhook authentication
    private static final String HEADER_SIGNATURE = "X-Gerrit-Signature";
    private static final String HEADER_TOKEN = "X-Gerrit-Token";
    private static final String HEADER_AUTH = "Authorization";

    // Signature algorithm constants
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SHA256_PREFIX = "sha256=";

    // Bearer token prefix length
    private static final int BEARER_PREFIX_LENGTH = 7;

    /**
     * Default constructor.
     */
    public WebhookAuthenticator() {
        // Empty constructor
    }

    /**
     * Authenticates a webhook request from Gerrit.
     * Checks various authentication methods based on server configuration.
     *
     * @param request the HTTP request to authenticate
     * @param server the Gerrit server configuration
     * @return true if authentication succeeds, false otherwise
     */
    public boolean authenticate(HttpServletRequest request, GerritServer server) {
        if (request == null || server == null) {
            LOGGER.warning("Cannot authenticate null request or server");
            return false;
        }

        try {
            // TODO: Get webhook configuration from server config
            // For now, we'll use a simplified approach

            // 1. Check IP address whitelist (if configured)
            if (!isIpAllowed(request, server)) {
                LOGGER.warning("Request from IP address " + request.getRemoteAddr() + " not allowed");
                return false;
            }

            // 2. Check secret token (if configured)
            if (!isTokenValid(request, server)) {
                LOGGER.warning("Invalid or missing webhook token");
                return false;
            }

            // 3. Check HMAC signature (if configured)
            if (!isSignatureValid(request, server)) {
                LOGGER.warning("Invalid HMAC signature");
                return false;
            }

            LOGGER.log(Level.FINE, "Webhook authentication successful for server {0}", server.getName());
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during webhook authentication", e);
            return false;
        }
    }

    /**
     * Checks if the request IP address is in the allowed list.
     *
     * @param request the HTTP request
     * @param server the Gerrit server configuration
     * @return true if IP is allowed or no restriction is configured
     */
    private boolean isIpAllowed(HttpServletRequest request, GerritServer server) {
        // TODO: Implement IP whitelist checking
        // For now, allow all IPs

        String clientIp = getClientIpAddress(request);
        LOGGER.log(Level.FINE, "Webhook request from IP: {0}", clientIp);

        // In a real implementation, you would check against configured allowed IPs
        // String[] allowedIps = getWebhookConfig(server).getAllowedIps();
        // if (allowedIps != null && allowedIps.length > 0) {
        //     return Arrays.asList(allowedIps).contains(clientIp);
        // }

        return true; // No IP restrictions for now
    }

    /**
     * Validates the webhook secret token.
     *
     * @param request the HTTP request
     * @param server the Gerrit server configuration
     * @return true if token is valid or no token is configured
     */
    private boolean isTokenValid(HttpServletRequest request, GerritServer server) {
        String configuredToken = getWebhookSecret(server);

        if (configuredToken == null || configuredToken.trim().isEmpty()) {
            // No token configured, so no validation required
            LOGGER.fine("No webhook token configured, skipping token validation");
            return true;
        }

        // Check various header locations for the token
        String providedToken = request.getHeader(HEADER_TOKEN);
        if (providedToken == null) {
            providedToken = request.getHeader(HEADER_AUTH);
            if (providedToken != null && providedToken.startsWith("Bearer ")) {
                providedToken = providedToken.substring(BEARER_PREFIX_LENGTH); // Remove "Bearer " prefix
            }
        }

        if (providedToken == null) {
            // Also check query parameter as fallback
            providedToken = request.getParameter("token");
        }

        if (providedToken == null || providedToken.trim().isEmpty()) {
            LOGGER.warning("No webhook token provided in request");
            return false;
        }

        // Use secure comparison to prevent timing attacks
        return secureEquals(configuredToken, providedToken);
    }

    /**
     * Validates the HMAC signature of the webhook payload.
     *
     * @param request the HTTP request
     * @param server the Gerrit server configuration
     * @return true if signature is valid or no signature validation is configured
     */
    private boolean isSignatureValid(HttpServletRequest request, GerritServer server) {
        String hmacSecret = getWebhookHmacSecret(server);

        if (hmacSecret == null || hmacSecret.trim().isEmpty()) {
            // No HMAC secret configured, so no validation required
            LOGGER.fine("No HMAC secret configured, skipping signature validation");
            return true;
        }

        String providedSignature = request.getHeader(HEADER_SIGNATURE);
        if (providedSignature == null || providedSignature.trim().isEmpty()) {
            LOGGER.warning("No HMAC signature provided in request");
            return false;
        }

        try {
            // Get the request body (payload) for signature calculation
            String payload = getRequestPayload(request);
            if (payload == null) {
                LOGGER.warning("Unable to read request payload for signature validation");
                return false;
            }

            // Calculate expected signature
            String expectedSignature = calculateHmacSignature(payload, hmacSecret);

            // Compare signatures securely
            return secureEquals(expectedSignature, providedSignature);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error validating HMAC signature", e);
            return false;
        }
    }

    /**
     * Gets the client's IP address, handling proxy headers.
     *
     * @param request the HTTP request
     * @return the client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // Handle comma-separated list of IPs (take the first one)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * Calculates HMAC-SHA256 signature for the given payload.
     *
     * @param payload the payload to sign
     * @param secret the HMAC secret
     * @return the HMAC signature as hex string with sha256= prefix
     * @throws Exception if signature calculation fails
     */
    private String calculateHmacSignature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKeySpec);

        byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        String signature = bytesToHex(signatureBytes);

        return SHA256_PREFIX + signature;
    }

    /**
     * Converts byte array to lowercase hexadecimal string.
     *
     * @param bytes the byte array
     * @return the hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Performs secure string comparison to prevent timing attacks.
     *
     * @param a first string
     * @param b second string
     * @return true if strings are equal
     */
    private boolean secureEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }

        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }

        return result == 0;
    }

    /**
     * Gets the webhook secret token from server configuration.
     *
     * @param server the Gerrit server
     * @return the webhook secret token, or null if not configured
     */
    private String getWebhookSecret(GerritServer server) {
        WebhookConfig webhookConfig = getWebhookConfig(server);
        if (webhookConfig != null) {
            return webhookConfig.getWebhookSecretPlainText();
        }
        return null;
    }

    /**
     * Gets the webhook HMAC secret from server configuration.
     *
     * @param server the Gerrit server
     * @return the HMAC secret, or null if not configured
     */
    private String getWebhookHmacSecret(GerritServer server) {
        WebhookConfig webhookConfig = getWebhookConfig(server);
        if (webhookConfig != null) {
            return webhookConfig.getHmacSecretPlainText();
        }
        return null;
    }

    /**
     * Gets the webhook configuration from the server.
     *
     * @param server the Gerrit server
     * @return the webhook configuration, or null if not available
     */
    private WebhookConfig getWebhookConfig(GerritServer server) {
        if (server == null || server.getConfig() == null) {
            return null;
        }

        // Check if config is a Config instance (not just the interface)
        if (server.getConfig() instanceof com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config) {
            com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config config =
                    (com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config)server.getConfig();
            return config.getWebhookConfig();
        }

        return null;
    }

    /**
     * Reads the request payload/body.
     * TODO: This needs to be implemented to read from the request input stream.
     *
     * @param request the HTTP request
     * @return the request payload as string, or null if reading fails
     */
    private String getRequestPayload(HttpServletRequest request) {
        // TODO: Implement payload reading
        // This should read the request body that was already consumed
        // We may need to cache it during the initial read in WebhookEventReceiver
        return null;
    }
}
