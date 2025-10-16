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

import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration settings for webhook support in the Gerrit Trigger Plugin.
 * Encapsulates all webhook-related configuration options including
 * authentication, security, and networking settings.
 *
 * @author Your Name &lt;your.email@domain.com&gt;
 */
public class WebhookConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Whether webhook support is enabled */
    private boolean enabled = false;

    /** Secret token for webhook authentication */
    private Secret webhookSecret;

    /** HMAC secret for webhook signature validation */
    private Secret hmacSecret;

    /** List of allowed IP addresses/subnets for webhook requests */
    private List<String> allowedIpAddresses = new ArrayList<>();

    /** Whether to require HMAC signature validation */
    private boolean requireHmacSignature = false;

    /** Whether to require secret token authentication */
    private boolean requireSecretToken = false;

    /**
     * Default constructor for data binding.
     */
    @DataBoundConstructor
    public WebhookConfig() {
        // Default constructor
    }

    /**
     * Constructor with basic configuration.
     *
     * @param enabled whether webhook support is enabled
     */
    public WebhookConfig(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets whether webhook support is enabled.
     *
     * @return true if webhooks are enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether webhook support is enabled.
     *
     * @param enabled true to enable webhooks, false to disable
     */
    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the webhook secret token.
     *
     * @return the secret token, or null if not set
     */
    public Secret getWebhookSecret() {
        return webhookSecret;
    }

    /**
     * Sets the webhook secret token.
     *
     * @param webhookSecret the secret token for webhook authentication
     */
    @DataBoundSetter
    public void setWebhookSecret(Secret webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    /**
     * Gets the webhook secret token as plain text.
     *
     * @return the decrypted secret token, or null if not set
     */
    public String getWebhookSecretPlainText() {
        if (webhookSecret != null) {
            return webhookSecret.getPlainText();
        }
        return null;
    }

    /**
     * Gets the HMAC secret for signature validation.
     *
     * @return the HMAC secret, or null if not set
     */
    public Secret getHmacSecret() {
        return hmacSecret;
    }

    /**
     * Sets the HMAC secret for signature validation.
     *
     * @param hmacSecret the HMAC secret
     */
    @DataBoundSetter
    public void setHmacSecret(Secret hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    /**
     * Gets the HMAC secret as plain text.
     *
     * @return the decrypted HMAC secret, or null if not set
     */
    public String getHmacSecretPlainText() {
        if (hmacSecret != null) {
            return hmacSecret.getPlainText();
        }
        return null;
    }

    /**
     * Gets the list of allowed IP addresses/subnets.
     *
     * @return the list of allowed IP addresses, never null
     */
    public List<String> getAllowedIpAddresses() {
        if (allowedIpAddresses != null) {
            return allowedIpAddresses;
        }
        return new ArrayList<>();
    }

    /**
     * Sets the list of allowed IP addresses/subnets.
     *
     * @param allowedIpAddresses the list of allowed IP addresses
     */
    @DataBoundSetter
    public void setAllowedIpAddresses(List<String> allowedIpAddresses) {
        if (allowedIpAddresses != null) {
            this.allowedIpAddresses = allowedIpAddresses;
        } else {
            this.allowedIpAddresses = new ArrayList<>();
        }
    }

    /**
     * Gets the allowed IP addresses as a comma-separated string.
     *
     * @return comma-separated IP addresses, or empty string if none
     */
    public String getAllowedIpAddressesAsString() {
        if (allowedIpAddresses != null) {
            return String.join(",", allowedIpAddresses);
        }
        return "";
    }

    /**
     * Sets the allowed IP addresses from a comma-separated string.
     *
     * @param ipAddresses comma-separated list of IP addresses
     */
    public void setAllowedIpAddressesFromString(String ipAddresses) {
        if (ipAddresses != null && !ipAddresses.trim().isEmpty()) {
            this.allowedIpAddresses = Arrays.asList(ipAddresses.split(","));
            // Trim whitespace from each address
            this.allowedIpAddresses.replaceAll(String::trim);
        } else {
            this.allowedIpAddresses = new ArrayList<>();
        }
    }

    /**
     * Gets whether HMAC signature validation is required.
     *
     * @return true if HMAC validation is required, false otherwise
     */
    public boolean isRequireHmacSignature() {
        return requireHmacSignature;
    }

    /**
     * Sets whether HMAC signature validation is required.
     *
     * @param requireHmacSignature true to require HMAC validation, false otherwise
     */
    @DataBoundSetter
    public void setRequireHmacSignature(boolean requireHmacSignature) {
        this.requireHmacSignature = requireHmacSignature;
    }

    /**
     * Gets whether secret token authentication is required.
     *
     * @return true if secret token is required, false otherwise
     */
    public boolean isRequireSecretToken() {
        return requireSecretToken;
    }

    /**
     * Sets whether secret token authentication is required.
     *
     * @param requireSecretToken true to require secret token, false otherwise
     */
    @DataBoundSetter
    public void setRequireSecretToken(boolean requireSecretToken) {
        this.requireSecretToken = requireSecretToken;
    }

    /**
     * Checks if webhook authentication is configured and properly set up.
     *
     * @return true if webhook authentication is properly configured
     */
    public boolean isWebhookAuthenticationConfigured() {
        if (!enabled) {
            return false;
        }

        // At least one authentication method should be configured
        boolean hasSecretToken = requireSecretToken && webhookSecret != null
                                && !webhookSecret.getPlainText().trim().isEmpty();
        boolean hasHmacSignature = requireHmacSignature && hmacSecret != null
                                && !hmacSecret.getPlainText().trim().isEmpty();
        boolean hasIpRestriction = allowedIpAddresses != null && !allowedIpAddresses.isEmpty();

        return hasSecretToken || hasHmacSignature || hasIpRestriction;
    }

    /**
     * Gets a user-friendly status message about the webhook configuration.
     *
     * @return status message describing the current configuration
     */
    public String getWebhookConfigurationStatus() {
        if (!enabled) {
            return "Webhooks are disabled";
        }

        List<String> authMethods = new ArrayList<>();

        if (requireSecretToken && webhookSecret != null) {
            authMethods.add("Secret Token");
        }

        if (requireHmacSignature && hmacSecret != null) {
            authMethods.add("HMAC Signature");
        }

        if (allowedIpAddresses != null && !allowedIpAddresses.isEmpty()) {
            authMethods.add("IP Whitelist (" + allowedIpAddresses.size() + " addresses)");
        }

        if (authMethods.isEmpty()) {
            return "Webhooks enabled (WARNING: No authentication configured!)";
        }

        return "Webhooks enabled with: " + String.join(", ", authMethods);
    }

    @Override
    public String toString() {
        int ipCount = 0;
        if (allowedIpAddresses != null) {
            ipCount = allowedIpAddresses.size();
        }
        return "WebhookConfig{"
                + "enabled=" + enabled
                + ", requireSecretToken=" + requireSecretToken
                + ", requireHmacSignature=" + requireHmacSignature
                + ", allowedIpCount=" + ipCount
                + '}';
    }
}
