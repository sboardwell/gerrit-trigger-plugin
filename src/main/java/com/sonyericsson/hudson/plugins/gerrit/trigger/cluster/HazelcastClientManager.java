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

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig;
import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages Hazelcast client connection to sidecar container.
 * <p>
 * In HA/HS environments with sidecar architecture, Jenkins connects
 * to an external Hazelcast cluster running in a sidecar container rather than
 * embedding Hazelcast in the Jenkins JVM.
 * <p>
 * This provides better isolation, independent lifecycle management, and easier
 * resource allocation between Jenkins and Hazelcast.
 *
 */
public final class HazelcastClientManager {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastClientManager.class);

    /**
     * System property to specify Hazelcast addresses.
     * Format: comma-separated list of host:port pairs
     * Example: -Dgerrit.trigger.hazelcast.addresses=localhost:5702
     */
    private static final String ADDRESSES_PROPERTY = "gerrit.trigger.hazelcast.addresses";

    /**
     * System property to override cluster name.
     * Must match the sidecar cluster name.
     * Example: -Dgerrit.trigger.hazelcast.cluster.name=gerrit-trigger-cluster
     */
    private static final String CLUSTER_NAME_PROPERTY = "gerrit.trigger.hazelcast.cluster.name";

    /**
     * System property to configure connection timeout (milliseconds).
     * Example: -Dgerrit.trigger.hazelcast.connection.timeout=60000
     */
    private static final String CONNECTION_TIMEOUT_PROPERTY = "gerrit.trigger.hazelcast.connection.timeout";

    /**
     * Default Hazelcast address (localhost sidecar).
     */
    private static final String DEFAULT_ADDRESS = "localhost:5702";

    /**
     * Default cluster name.
     */
    private static final String DEFAULT_CLUSTER_NAME = "gerrit-trigger-cluster";

    /**
     * Default connection timeout (60 seconds).
     */
    private static final int DEFAULT_CONNECTION_TIMEOUT = 60000;

    /**
     * Initial backoff for connection retry (1 second).
     */
    private static final int INITIAL_BACKOFF_MILLIS = 1000;

    /**
     * Maximum backoff for connection retry (30 seconds).
     */
    private static final int MAX_BACKOFF_MILLIS = 30000;

    /**
     * Backoff multiplier for exponential retry.
     */
    private static final double BACKOFF_MULTIPLIER = 2.0;

    /**
     * Hazelcast client instance (connected to sidecar).
     */
    private static volatile HazelcastInstance clientInstance = null;

    /**
     * Private constructor to prevent instantiation.
     */
    private HazelcastClientManager() {
        // Utility class
    }

    /**
     * Initializes Hazelcast client connection to sidecar container.
     * <p>
     * Connects to the Hazelcast cluster running in the sidecar container.
     * Connection parameters can be configured via system properties.
     * <p>
     * If initialization fails, logs error and leaves clientInstance null.
     * Callers should handle null gracefully (fail-open behavior).
     */
    public static synchronized void initialize() {
        if (clientInstance != null) {
            logger.debug("Hazelcast client already initialized");
            return;
        }

        try {
            logger.info("Initializing Hazelcast client for sidecar connection...");

            ClientConfig clientConfig = new ClientConfig();

            // Cluster name (must match sidecar)
            String clusterName = System.getProperty(CLUSTER_NAME_PROPERTY, DEFAULT_CLUSTER_NAME);
            clientConfig.setClusterName(clusterName);
            logger.info("Hazelcast client cluster name: {}", clusterName);

            // Register Compact Serializers for event claiming and build memory
            // This enables cross-JVM serialization without requiring the class on the sidecar
            clientConfig.getSerializationConfig()
                    .getCompactSerializationConfig()
                    .addSerializer(new EventClaimSerializer())
                    .addSerializer(new EntryDataSerializer())
                    .addSerializer(new MemoryImprintDataSerializer());
            logger.debug("Registered Compact Serializers for EventClaim, EntryData, and MemoryImprintData");

            // Network addresses (sidecar endpoints)
            String addresses = System.getProperty(ADDRESSES_PROPERTY, DEFAULT_ADDRESS);
            for (String address : addresses.split(",")) {
                String trimmedAddress = address.trim();
                clientConfig.getNetworkConfig().addAddress(trimmedAddress);
                logger.info("Hazelcast client address: {}", trimmedAddress);
            }

            // Connection strategy configuration
            ClientConnectionStrategyConfig connectionStrategy = clientConfig.getConnectionStrategyConfig();
            connectionStrategy.setAsyncStart(false); // Wait for connection
            connectionStrategy.setReconnectMode(ClientConnectionStrategyConfig.ReconnectMode.ON);

            // Connection retry configuration
            int connectionTimeout = Integer.getInteger(CONNECTION_TIMEOUT_PROPERTY, DEFAULT_CONNECTION_TIMEOUT);
            connectionStrategy.getConnectionRetryConfig()
                    .setInitialBackoffMillis(INITIAL_BACKOFF_MILLIS)
                    .setMaxBackoffMillis(MAX_BACKOFF_MILLIS)
                    .setMultiplier(BACKOFF_MULTIPLIER)
                    .setClusterConnectTimeoutMillis(connectionTimeout);

            logger.info("Hazelcast client connection timeout: {}ms", connectionTimeout);

            // Create client instance
            clientInstance = HazelcastClient.newHazelcastClient(clientConfig);

            logger.info("Hazelcast client successfully connected to sidecar cluster: {} (cluster: {})",
                    addresses, clusterName);

        } catch (Exception e) {
            logger.error("Failed to initialize Hazelcast client for sidecar connection", e);
            clientInstance = null;
        }
    }

    /**
     * Gets the Hazelcast client instance.
     * <p>
     * Returns the client instance connected to the sidecar, or null if not initialized
     * or initialization failed.
     *
     * @return Hazelcast client instance, or null if unavailable
     */
    public static HazelcastInstance getInstance() {
        return clientInstance;
    }

    /**
     * Shuts down the Hazelcast client connection.
     * <p>
     * Gracefully disconnects from the sidecar cluster. The sidecar cluster continues
     * running independently.
     */
    public static synchronized void shutdown() {
        if (clientInstance != null) {
            logger.info("Shutting down Hazelcast client...");
            try {
                clientInstance.shutdown();
                logger.info("Hazelcast client shut down successfully");
            } catch (Exception e) {
                logger.error("Error shutting down Hazelcast client", e);
            } finally {
                clientInstance = null;
            }
        }
    }

    /**
     * Checks if the client is currently connected to the cluster.
     *
     * @return true if client is initialized and running
     */
    public static boolean isConnected() {
        if (clientInstance == null) {
            return false;
        }
        try {
            return clientInstance.getLifecycleService().isRunning();
        } catch (Exception e) {
            logger.debug("Error checking client connection status", e);
            return false;
        }
    }
}
