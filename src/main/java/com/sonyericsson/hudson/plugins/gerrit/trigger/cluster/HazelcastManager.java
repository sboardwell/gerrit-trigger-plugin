/*
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.cluster;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.PluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the lifecycle of Hazelcast (embedded member or client).
 * <p>
 * Supports two modes:
 * <ul>
 *   <li><strong>Embedded mode</strong>: Creates Hazelcast member in Jenkins JVM</li>
 *   <li><strong>Client mode</strong>: Connects to sidecar Hazelcast cluster</li>
 * </ul>
 * <p>
 * Mode selection via {@link HazelcastInstanceProvider#getMode()}.
 *
 * @author CloudBees, Inc.
 */
public final class HazelcastManager {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastManager.class);

    private static volatile boolean initialized = false;
    private static final Object INIT_LOCK = new Object();

    /**
     * Private constructor to prevent instantiation.
     */
    private HazelcastManager() {
        // Utility class
    }

    /**
     * Initializes Hazelcast if cluster mode is enabled.
     * <p>
     * Behavior depends on configured mode:
     * <ul>
     *   <li><strong>Embedded mode</strong>: Creates Hazelcast member in Jenkins JVM</li>
     *   <li><strong>Client mode</strong>: Connects to sidecar Hazelcast cluster</li>
     * </ul>
     * <p>
     * This method is idempotent - calling it multiple times has no effect if already initialized.
     *
     * @return true if Hazelcast was initialized (or already initialized), false if cluster mode is disabled
     */
    public static boolean initialize() {
        // Check if cluster mode is enabled
        if (!isClusterModeEnabled()) {
            logger.info("Cluster mode is disabled. Hazelcast will not be initialized.");
            return false;
        }

        synchronized (INIT_LOCK) {
            if (initialized) {
                logger.debug("Hazelcast is already initialized");
                return true;
            }

            try {
                String mode = HazelcastInstanceProvider.getMode();
                logger.info("Initializing Hazelcast in {} mode...", mode);

                if ("client".equals(mode)) {
                    // Client mode - connect to sidecar
                    initializeClientMode();
                } else {
                    // Embedded mode - create member in JVM
                    initializeEmbeddedMode();
                }

                initialized = true;
                logger.info("Hazelcast initialized successfully in {} mode", mode);

                return true;

            } catch (Exception e) {
                logger.error("Failed to initialize Hazelcast", e);
                initialized = false;
                throw new RuntimeException("Failed to initialize Hazelcast cluster mode", e);
            }
        }
    }

    /**
     * Initializes Hazelcast in embedded mode (member in Jenkins JVM).
     */
    private static void initializeEmbeddedMode() {
        logger.info("Initializing Hazelcast embedded member...");

        // Create Hazelcast configuration
        com.hazelcast.config.Config config = HazelcastConfig.createConfig();

        // Create Hazelcast instance
        HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);

        // Register with provider
        HazelcastInstanceProvider.setInstance(hazelcastInstance);

        // Log cluster information
        int clusterSize = hazelcastInstance.getCluster().getMembers().size();
        logger.info("Hazelcast embedded member initialized. Cluster: {}, Instance: {}, Members: {}",
                config.getClusterName(),
                hazelcastInstance.getName(),
                clusterSize);
    }

    /**
     * Initializes Hazelcast in client mode (connect to sidecar).
     */
    private static void initializeClientMode() {
        logger.info("Initializing Hazelcast client for sidecar connection...");

        // Initialize client connection
        HazelcastClientManager.initialize();

        // Verify connection
        if (!HazelcastClientManager.isConnected()) {
            throw new RuntimeException("Failed to connect Hazelcast client to sidecar cluster");
        }

        logger.info("Hazelcast client connected to sidecar cluster");
    }

    /**
     * Shuts down Hazelcast gracefully.
     * <p>
     * Behavior depends on configured mode:
     * <ul>
     *   <li><strong>Embedded mode</strong>: Shuts down Hazelcast member</li>
     *   <li><strong>Client mode</strong>: Disconnects from sidecar cluster</li>
     * </ul>
     * <p>
     * This method is idempotent - calling it multiple times has no effect if already shut down.
     */
    public static void shutdown() {
        synchronized (INIT_LOCK) {
            if (!initialized) {
                logger.debug("Hazelcast is not initialized, nothing to shut down");
                return;
            }

            try {
                String mode = HazelcastInstanceProvider.getMode();
                logger.info("Shutting down Hazelcast ({} mode)...", mode);

                if ("client".equals(mode)) {
                    // Client mode - disconnect from sidecar
                    HazelcastClientManager.shutdown();
                } else {
                    // Embedded mode - shutdown member
                    HazelcastInstance instance = HazelcastInstanceProvider.getInstance();
                    if (instance != null) {
                        String instanceName = instance.getName();

                        // Shutdown the instance
                        instance.shutdown();

                        logger.info("Hazelcast embedded member shut down: {}", instanceName);
                    }

                    // Clear the provider
                    HazelcastInstanceProvider.clearInstance();
                }

                initialized = false;

                logger.info("Hazelcast shutdown complete ({} mode)", mode);

            } catch (Exception e) {
                logger.error("Error during Hazelcast shutdown", e);
                // Continue with cleanup even if error occurred
                HazelcastInstanceProvider.clearInstance();
                initialized = false;
            }
        }
    }

    /**
     * Checks if Hazelcast is currently initialized.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return initialized && HazelcastInstanceProvider.isInitialized();
    }

    /**
     * Checks if cluster mode is enabled in plugin configuration.
     *
     * @return true if cluster mode is enabled
     */
    private static boolean isClusterModeEnabled() {
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin == null) {
            logger.warn("PluginImpl instance not available, cluster mode considered disabled");
            return false;
        }

        PluginConfig config = plugin.getPluginConfig();
        if (config == null) {
            logger.warn("PluginConfig not available, cluster mode considered disabled");
            return false;
        }

        boolean enabled = config.isClusterModeEnabled();
        logger.debug("Cluster mode enabled: {}", enabled);
        return enabled;
    }

    /**
     * Reinitializes Hazelcast if configuration has changed.
     * This will shutdown the existing instance and create a new one if cluster mode is enabled.
     * Used when cluster mode is toggled via configuration.
     *
     * @return true if reinitialized successfully
     */
    public static boolean reinitialize() {
        logger.info("Reinitializing Hazelcast...");

        synchronized (INIT_LOCK) {
            // Shutdown existing instance
            if (initialized) {
                shutdown();
            }

            // Initialize if cluster mode is enabled
            return initialize();
        }
    }

    /**
     * Gets status information about Hazelcast cluster.
     *
     * @return status string with cluster information
     */
    public static String getStatus() {
        String mode = HazelcastInstanceProvider.getMode();

        if (!initialized) {
            return String.format("Hazelcast: Not initialized (cluster mode disabled or not started, mode: %s)", mode);
        }

        HazelcastInstance instance = HazelcastInstanceProvider.getInstance();
        if (instance == null) {
            return String.format("Hazelcast: Error - initialized flag is true but instance is null (mode: %s)", mode);
        }

        if (!instance.getLifecycleService().isRunning()) {
            return String.format("Hazelcast: Not running (mode: %s)", mode);
        }

        try {
            int clusterSize = instance.getCluster().getMembers().size();
            String clusterName;
            if ("client".equals(mode)) {
                clusterName = "N/A (client)";
            } else {
                clusterName = instance.getConfig().getClusterName();
            }
            String instanceName = instance.getName();

            return String.format("Hazelcast: Running | Mode: %s | Cluster: %s | Instance: %s | Members: %d",
                    mode, clusterName, instanceName, clusterSize);
        } catch (Exception e) {
            return String.format("Hazelcast: Error getting status (mode: %s): %s", mode, e.getMessage());
        }
    }
}
