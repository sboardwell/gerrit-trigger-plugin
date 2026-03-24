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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model;

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager class for coordinating BuildMemory data migration between local and distributed modes.
 * <p>
 * This class provides static methods to trigger data migration when cluster mode is enabled or disabled.
 * It acts as a bridge between {@link com.sonyericsson.hudson.plugins.gerrit.trigger.cluster.ClusterModeListener}
 * and {@link BuildMemory}.
 * <p>
 * The manager accesses BuildMemory through the {@link ToGerritRunListener} singleton instance.
 *
 * @author CloudBees, Inc.
 */
public final class BuildMemoryManager {

    private static final Logger logger = LoggerFactory.getLogger(BuildMemoryManager.class);

    /**
     * Private constructor to prevent instantiation.
     */
    private BuildMemoryManager() {
        // Utility class
    }

    /**
     * Migrates BuildMemory data from local storage to Hazelcast distributed storage.
     * <p>
     * This method should be called when cluster mode is enabled.
     * It copies all entries from the local TreeMap to the Hazelcast IMap.
     * <p>
     * If the ToGerritRunListener singleton is not available, logs a warning and returns.
     * This is fail-safe behavior - the plugin will continue to work in local mode.
     */
    public static void migrateToDistributed() {
        logger.info("BuildMemoryManager: Starting migration to distributed storage...");

        ToGerritRunListener listener = ToGerritRunListener.getInstance();
        if (listener == null) {
            logger.warn("ToGerritRunListener instance not available, cannot migrate BuildMemory");
            return;
        }

        BuildMemory memory = listener.getMemory();
        if (memory == null) {
            logger.warn("BuildMemory instance not available, cannot migrate");
            return;
        }

        try {
            memory.migrateToDistributed();
            logger.info("BuildMemoryManager: Successfully migrated BuildMemory to distributed storage");
        } catch (Exception e) {
            logger.error("BuildMemoryManager: Error migrating BuildMemory to distributed storage", e);
            // Non-fatal - plugin continues to work, new data will use distributed mode
        }
    }

    /**
     * Migrates BuildMemory data from Hazelcast distributed storage to local storage.
     * <p>
     * This method should be called when cluster mode is disabled.
     * It copies all entries from the Hazelcast IMap to the local TreeMap.
     * <p>
     * If the ToGerritRunListener singleton is not available, logs a warning and returns.
     * This is fail-safe behavior - the plugin will continue to work.
     */
    public static void migrateToLocal() {
        logger.info("BuildMemoryManager: Starting migration to local storage...");

        ToGerritRunListener listener = ToGerritRunListener.getInstance();
        if (listener == null) {
            logger.warn("ToGerritRunListener instance not available, cannot migrate BuildMemory");
            return;
        }

        BuildMemory memory = listener.getMemory();
        if (memory == null) {
            logger.warn("BuildMemory instance not available, cannot migrate");
            return;
        }

        try {
            memory.migrateToLocal();
            logger.info("BuildMemoryManager: Successfully migrated BuildMemory to local storage");
        } catch (Exception e) {
            logger.error("BuildMemoryManager: Error migrating BuildMemory to local storage", e);
            // Non-fatal - plugin continues to work in local mode
        }
    }

    /**
     * Checks if BuildMemory is available for migration.
     * Useful for testing or diagnostic purposes.
     *
     * @return true if BuildMemory is accessible, false otherwise
     */
    public static boolean isAvailable() {
        ToGerritRunListener listener = ToGerritRunListener.getInstance();
        return listener != null && listener.getMemory() != null;
    }
}
