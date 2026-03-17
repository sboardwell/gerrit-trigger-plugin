/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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
 * Manages the lifecycle of the Hazelcast cluster member.
 * Handles initialization and shutdown based on cluster mode configuration.
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
                logger.info("Initializing Hazelcast for cluster mode...");

                // Create Hazelcast configuration
                com.hazelcast.config.Config config = HazelcastConfig.createConfig();

                // Create Hazelcast instance
                HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);

                // Register with provider
                HazelcastInstanceProvider.setInstance(hazelcastInstance);

                initialized = true;

                // Log cluster information
                int clusterSize = hazelcastInstance.getCluster().getMembers().size();
                logger.info("Hazelcast initialized successfully. Cluster: {}, Instance: {}, Members: {}",
                        config.getClusterName(),
                        hazelcastInstance.getName(),
                        clusterSize);

                return true;

            } catch (Exception e) {
                logger.error("Failed to initialize Hazelcast", e);
                initialized = false;
                throw new RuntimeException("Failed to initialize Hazelcast cluster mode", e);
            }
        }
    }

    /**
     * Shuts down Hazelcast gracefully.
     * This method is idempotent - calling it multiple times has no effect if already shut down.
     */
    public static void shutdown() {
        synchronized (INIT_LOCK) {
            if (!initialized) {
                logger.debug("Hazelcast is not initialized, nothing to shut down");
                return;
            }

            try {
                logger.info("Shutting down Hazelcast...");

                HazelcastInstance instance = HazelcastInstanceProvider.getInstance();
                if (instance != null) {
                    String instanceName = instance.getName();

                    // Shutdown the instance
                    instance.shutdown();

                    logger.info("Hazelcast instance shut down: {}", instanceName);
                }

                // Clear the provider
                HazelcastInstanceProvider.clearInstance();

                initialized = false;

                logger.info("Hazelcast shutdown complete");

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
        if (!initialized) {
            return "Hazelcast: Not initialized (cluster mode disabled or not started)";
        }

        HazelcastInstance instance = HazelcastInstanceProvider.getInstance();
        if (instance == null) {
            return "Hazelcast: Error - initialized flag is true but instance is null";
        }

        if (!instance.getLifecycleService().isRunning()) {
            return "Hazelcast: Not running";
        }

        int clusterSize = instance.getCluster().getMembers().size();
        String clusterName = instance.getConfig().getClusterName();
        String instanceName = instance.getName();

        return String.format("Hazelcast: Running | Cluster: %s | Instance: %s | Members: %d",
                clusterName, instanceName, clusterSize);
    }
}
