/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.cluster;

import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.PluginConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemoryManager;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener that detects when plugin configuration is saved and handles cluster mode changes.
 * <p>
 * This listener is responsible for:
 * <ul>
 *   <li>Detecting when cluster mode is enabled/disabled via UI or JCasC</li>
 *   <li>Initializing Hazelcast when cluster mode is enabled</li>
 *   <li>Migrating BuildMemory data between local and distributed storage</li>
 *   <li>Shutting down Hazelcast when cluster mode is disabled</li>
 * </ul>
 * <p>
 * Data migration is handled through {@link BuildMemoryManager}, which provides
 * a coordination layer between this listener and the actual data structures.
 *
 * @author ironcero
 */
@Extension
public final class ClusterModeListener extends SaveableListener {

    private static final Logger logger = LoggerFactory.getLogger(ClusterModeListener.class);

    @Override
    public void onChange(Saveable o, XmlFile file) {
        // Only interested in PluginImpl saves (which contain PluginConfig)
        if (!(o instanceof PluginImpl)) {
            return;
        }

        PluginImpl plugin = (PluginImpl)o;
        PluginConfig config = plugin.getPluginConfig();

        if (config == null) {
            logger.debug("PluginConfig is null, skipping cluster mode check");
            return;
        }

        // Check what the configuration says cluster mode should be
        boolean shouldBeEnabled = config.isClusterModeEnabled();

        // Check what Hazelcast is actually doing
        boolean isCurrentlyEnabled = HazelcastManager.isInitialized();

        logger.debug("Cluster mode check - Config says: {}, Hazelcast state: {}",
                shouldBeEnabled, isCurrentlyEnabled);

        // Synchronize Hazelcast state with configuration
        if (shouldBeEnabled && !isCurrentlyEnabled) {
            // Configuration says enabled, but Hazelcast is not running - start it
            logger.info("Cluster mode enabled in configuration but Hazelcast not running. Enabling...");
            enableClusterMode();
        } else if (!shouldBeEnabled && isCurrentlyEnabled) {
            // Configuration says disabled, but Hazelcast is running - stop it
            logger.info("Cluster mode disabled in configuration but Hazelcast is running. Disabling...");
            disableClusterMode();
        } else {
            // Already in sync, no action needed
            logger.debug("Cluster mode already in sync with configuration: {}", shouldBeEnabled);
        }
    }

    /**
     * Enables cluster mode by initializing Hazelcast and migrating data to distributed storage.
     * <p>
     * This method:
     * <ol>
     *   <li>Initializes Hazelcast cluster</li>
     *   <li>Migrates BuildMemory from local TreeMap to Hazelcast IMap</li>
     * </ol>
     * <p>
     * If migration fails, the plugin continues to work (new data will use distributed mode).
     */
    private void enableClusterMode() {
        logger.info("Enabling cluster mode...");
        try {
            boolean initialized = HazelcastManager.initialize();
            if (initialized) {
                logger.info("Hazelcast initialized successfully. Status: {}", HazelcastManager.getStatus());

                // Migrate BuildMemory data to distributed storage
                logger.info("Migrating BuildMemory to distributed storage...");
                BuildMemoryManager.migrateToDistributed();

                // TODO: Future enhancements
                // - Migrate RunningJobs ConcurrentHashMap -> Hazelcast IMap
                // - Migrate GerritProjectList -> Hazelcast distributed structure
                // - Migrate ReplicationCache -> Hazelcast IMap

                logger.info("Cluster mode enabled successfully");
            } else {
                logger.warn("Cluster mode initialization returned false. Check configuration.");
            }
        } catch (Exception e) {
            logger.error("Failed to enable cluster mode", e);
            // Plugin continues to work in standalone mode
        }
    }

    /**
     * Disables cluster mode by migrating data to local storage and shutting down Hazelcast.
     * <p>
     * This method:
     * <ol>
     *   <li>Migrates BuildMemory from Hazelcast IMap to local TreeMap</li>
     *   <li>Shuts down Hazelcast cluster</li>
     * </ol>
     * <p>
     * If migration fails, the plugin continues to work in local mode (data in Hazelcast will be lost).
     */
    private void disableClusterMode() {
        logger.info("Disabling cluster mode...");
        try {
            // Migrate BuildMemory data back to local storage
            logger.info("Migrating BuildMemory to local storage...");
            BuildMemoryManager.migrateToLocal();

            // TODO: Future enhancements
            // - Migrate RunningJobs from Hazelcast IMap -> ConcurrentHashMap
            // - Migrate GerritProjectList from Hazelcast -> local
            // - Note: Event claims are ephemeral and don't need migration

            // Shutdown Hazelcast after data migration
            HazelcastManager.shutdown();
            logger.info("Cluster mode disabled successfully. Plugin now running in standalone mode.");
        } catch (Exception e) {
            logger.error("Error disabling cluster mode", e);
            // Attempt to clean up anyway
            try {
                HazelcastManager.shutdown();
            } catch (Exception ex) {
                logger.error("Failed to shutdown Hazelcast during error recovery", ex);
            }
        }
    }

}
