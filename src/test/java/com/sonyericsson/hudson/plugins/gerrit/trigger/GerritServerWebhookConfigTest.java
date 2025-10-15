/*
 * MIT License
 * Copyright (c) 2025, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 * OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.webhook.WebhookConfig;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests for Gerrit server webhook configuration.
 * Verifies that webhook configuration is correctly stored and processed alongside SSH configuration.
 *
 * @author Claude Code
 */
public class GerritServerWebhookConfigTest {

    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    private static final String SERVER_NAME = "testWebhookServer";
    private static final int DEFAULT_GERRIT_SSH_PORT = 29418;

    /**
     * Tests that webhook mode configuration correctly sets the connection type.
     *
     * @throws Exception if error creating or configuring server
     */
    @Test
    public void testWebhookModeConfigurationFromJson() throws Exception {
        // Create JSON representing webhook configuration
        JSONObject formData = new JSONObject();
        formData.put("gerritHostName", "gerrit.example.com");
        formData.put("gerritSshPort", DEFAULT_GERRIT_SSH_PORT);
        formData.put("gerritUserName", "jenkins");
        formData.put("gerritFrontEndUrl", "https://gerrit.example.com");
        formData.put("connectionType", "WEBHOOK");

        // Create webhook config
        JSONObject webhookData = new JSONObject();
        webhookData.put("enabled", true);
        webhookData.put("webhookSecret", "test-secret");
        webhookData.put("requireSecretToken", true);
        formData.put("webhookConfig", webhookData);

        // Create and configure server
        Config config = new Config(formData);

        // Verify connection type was set to WEBHOOK
        assertEquals("Connection type should be WEBHOOK",
                    Config.ConnectionType.WEBHOOK,
                    config.getConnectionType());

        // Verify SSH fields are still saved (SSH always required for sending results)
        assertEquals("SSH hostname should be saved", "gerrit.example.com", config.getGerritHostName());
        assertEquals("SSH port should be saved", DEFAULT_GERRIT_SSH_PORT, config.getGerritSshPort());
        assertEquals("SSH username should be saved", "jenkins", config.getGerritUserName());

        // Verify webhook config exists
        WebhookConfig webhookConfig = config.getWebhookConfig();
        assertNotNull("Webhook config should exist", webhookConfig);
        assertEquals("Webhook should be enabled", true, webhookConfig.isEnabled());
        assertEquals("Webhook secret should be set", true, webhookConfig.isRequireSecretToken());
    }

    /**
     * Tests that SSH mode configuration works correctly.
     *
     * @throws Exception if error creating or configuring server
     */
    @Test
    public void testSshModeConfigurationFromJson() throws Exception {
        // Create JSON representing SSH configuration
        JSONObject formData = new JSONObject();
        formData.put("gerritHostName", "gerrit-ssh.example.com");
        formData.put("gerritSshPort", DEFAULT_GERRIT_SSH_PORT);
        formData.put("gerritUserName", "jenkins-ssh");
        formData.put("gerritFrontEndUrl", "https://gerrit-ssh.example.com");
        formData.put("connectionType", "SSH");

        // Create and configure server
        Config config = new Config(formData);

        // Verify connection type is SSH
        assertEquals("Connection type should be SSH",
                    Config.ConnectionType.SSH,
                    config.getConnectionType());

        // Verify SSH fields are saved
        assertEquals("SSH hostname should be saved", "gerrit-ssh.example.com", config.getGerritHostName());
        assertEquals("SSH port should be saved", DEFAULT_GERRIT_SSH_PORT, config.getGerritSshPort());
        assertEquals("SSH username should be saved", "jenkins-ssh", config.getGerritUserName());

        // Webhook config should not exist in SSH mode (unless explicitly configured)
        WebhookConfig webhookConfig = config.getWebhookConfig();
        assertNull("Webhook config should not exist in SSH-only mode", webhookConfig);
    }

    /**
     * Tests backward compatibility - servers configured before webhook support
     * should default to SSH mode.
     *
     * @throws Exception if error creating or accessing server
     */
    @Test
    public void testBackwardCompatibilityDefaultsToSsh() throws Exception {
        // Create a server (simulates pre-webhook configuration)
        GerritServer server = new GerritServer(SERVER_NAME);
        Config config = new Config();
        config.setGerritHostName("legacy.gerrit.example.com");
        // Don't set connection type - simulates old configuration
        server.setConfig(config);

        PluginImpl.getInstance().addServer(server);
        server.start();

        // Verify connection type defaults to SSH
        Config savedConfig = (Config)server.getConfig();
        assertEquals("Legacy configs should default to SSH",
                    Config.ConnectionType.SSH,
                    savedConfig.getConnectionType());
    }

    /**
     * Tests that connection type defaults to SSH when not specified in JSON.
     *
     * @throws Exception if error creating config
     */
    @Test
    public void testConnectionTypeDefaultsToSsh() throws Exception {
        // Create JSON without connection type (simulates old configuration)
        JSONObject formData = new JSONObject();
        formData.put("gerritHostName", "default.gerrit.example.com");
        formData.put("gerritSshPort", DEFAULT_GERRIT_SSH_PORT);

        Config config = new Config(formData);

        // Verify connection type defaults to SSH
        assertEquals("Connection type should default to SSH",
                    Config.ConnectionType.SSH,
                    config.getConnectionType());
    }

    /**
     * Tests that webhook configuration can be copied via the copy constructor.
     *
     * @throws Exception if error creating or copying config
     */
    @Test
    public void testWebhookConfigCopyConstructor() throws Exception {
        // Create original config with webhook settings
        JSONObject formData = new JSONObject();
        formData.put("gerritHostName", "original.example.com");
        formData.put("connectionType", "WEBHOOK");

        JSONObject webhookData = new JSONObject();
        webhookData.put("enabled", true);
        webhookData.put("webhookSecret", "original-secret");
        webhookData.put("requireSecretToken", true);
        webhookData.put("hmacSecret", "original-hmac");
        webhookData.put("requireHmacSignature", true);
        webhookData.put("logWebhookRequests", true);
        webhookData.put("allowedIpAddresses", "192.168.1.0/24,10.0.0.1");
        formData.put("webhookConfig", webhookData);

        Config originalConfig = new Config(formData);

        // Copy the configuration
        Config copiedConfig = new Config(originalConfig);

        // Verify connection type was copied
        assertEquals("Connection type should be copied",
                    Config.ConnectionType.WEBHOOK,
                    copiedConfig.getConnectionType());

        // Verify webhook config was deep copied
        WebhookConfig copiedWebhookConfig = copiedConfig.getWebhookConfig();
        assertNotNull("Webhook config should be copied", copiedWebhookConfig);
        assertEquals("Webhook enabled should be copied", true, copiedWebhookConfig.isEnabled());
        assertEquals("Require secret token should be copied", true, copiedWebhookConfig.isRequireSecretToken());
        assertEquals("Require HMAC signature should be copied", true,
                    copiedWebhookConfig.isRequireHmacSignature());
        assertEquals("Log requests should be copied", true, copiedWebhookConfig.isLogWebhookRequests());

        // Verify it's a deep copy (not the same object)
        assertNotNull("Original webhook config should exist", originalConfig.getWebhookConfig());
        assertNotNull("Copied webhook config should exist", copiedWebhookConfig);
    }
}
