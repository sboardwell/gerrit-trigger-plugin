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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

/**
 * Service for claiming Gerrit events in a distributed cluster.
 * <p>
 * In CloudBees HA/HS environments with multiple replicas, each Gerrit event
 * arrives at all replicas via SSH event stream. To prevent duplicate builds,
 * replicas use distributed event claiming:
 * <ul>
 *   <li>First replica to claim an event processes it</li>
 *   <li>Other replicas skip the event (already claimed)</li>
 * </ul>
 * <p>
 * Claims are stored in a Hazelcast IMap with atomic {@code putIfAbsent} operations
 * to prevent race conditions. Claims automatically expire via TTL to prevent memory leaks.
 * <p>
 * <strong>Fail-open behavior:</strong> If Hazelcast is unavailable or cluster mode
 * is disabled, this service allows event processing to continue (better to risk
 * duplicate builds than drop events entirely).
 *
 * @author CloudBees, Inc.
 */
public final class EventClaimService {

    private static final Logger logger = LoggerFactory.getLogger(EventClaimService.class);

    /**
     * Hazelcast map name for event claims.
     */
    private static final String CLAIMS_MAP_NAME = "gerrit-trigger-event-claims";

    /**
     * Default TTL for event claims in seconds (5 minutes).
     * Claims expire automatically to prevent memory leaks.
     */
    private static final long DEFAULT_CLAIM_TTL_SECONDS = 300;

    /**
     * System property to override claim TTL.
     * Example: -Dgerrit.trigger.hazelcast.claim.ttl.seconds=600
     */
    private static final String CLAIM_TTL_PROPERTY = "gerrit.trigger.hazelcast.claim.ttl.seconds";

    /**
     * Cached instance identifier (hostname or pod name).
     */
    private static volatile String instanceId = null;

    /**
     * Private constructor to prevent instantiation.
     */
    private EventClaimService() {
        // Utility class
    }

    /**
     * Attempts to claim an event for processing at the replica level.
     * <p>
     * Uses atomic {@code putIfAbsent} operation to ensure only one replica
     * successfully claims each event. Once a replica claims an event, ALL jobs
     * on that replica can process it (returns true for all jobs on the claiming replica).
     * Jobs on other replicas will see the event as already claimed and skip processing.
     * <p>
     * This replica-level claiming allows multiple jobs on the same replica to be triggered
     * by the same event while preventing duplicate processing across replicas.
     * <p>
     * <strong>Fail-open behavior:</strong>
     * <ul>
     *   <li>If cluster mode is disabled: returns true (allow processing)</li>
     *   <li>If Hazelcast is unavailable: returns true (allow processing)</li>
     * </ul>
     *
     * @param event the Gerrit event to claim
     * @return true if this replica claimed the event (or it's already claimed by this replica),
     *         false if claimed by another replica
     */
    public static boolean tryClaimEvent(GerritTriggeredEvent event) {
        // Check if cluster mode is enabled
        if (!isClusterModeEnabled()) {
            logger.trace("Cluster mode disabled, skipping event claiming");
            return true; // Always allow in standalone mode
        }

        // Get Hazelcast instance
        HazelcastInstance hazelcast = HazelcastInstanceProvider.getInstance();
        if (hazelcast == null) {
            logger.error("Hazelcast not available, cannot claim event. Allowing event processing.");
            return true; // Fail-open: allow processing if Hazelcast unavailable
        }

        // Generate event ID
        String eventId = EventIdentifier.generateEventId(event);
        String thisInstanceId = getInstanceId();

        try {
            // Get claims map
            IMap<String, EventClaim> claimsMap = hazelcast.getMap(CLAIMS_MAP_NAME);

            // Check if event is already claimed
            EventClaim existingClaim = claimsMap.get(eventId);

            if (existingClaim != null) {
                // Event already claimed - check who claimed it
                if (existingClaim.getClaimedBy().equals(thisInstanceId)) {
                    // Claimed by THIS replica (another job already processed it)
                    // Allow this job to also process the event
                    logger.trace("Event already claimed by this replica, allowing: {} (job processing)",
                            eventId);
                    return true;
                } else {
                    // Claimed by ANOTHER replica - skip processing
                    logger.debug("Event already claimed by {}: {} (type: {})",
                            existingClaim.getClaimedBy(), eventId, event.getEventType().getTypeValue());
                    return false;
                }
            }

            // Event not yet claimed - attempt to claim it
            EventClaim claim = new EventClaim(
                    eventId,
                    thisInstanceId,
                    System.currentTimeMillis(),
                    event.getEventType().getTypeValue()
            );

            // Attempt atomic claim with TTL
            EventClaim previousClaim = claimsMap.putIfAbsent(
                    eventId,
                    claim,
                    getClaimTtlSeconds(),
                    TimeUnit.SECONDS
            );

            if (previousClaim == null) {
                // Successfully claimed by this replica
                logger.debug("Successfully claimed event: {} (type: {})", eventId, event.getEventType().getTypeValue());
                return true;
            } else {
                // Race condition: another replica claimed it between our get() and putIfAbsent()
                // Check if it was claimed by this replica or another
                if (previousClaim.getClaimedBy().equals(thisInstanceId)) {
                    // Claimed by THIS replica (race between jobs on same replica)
                    logger.trace("Event claimed by this replica during race condition: {}", eventId);
                    return true;
                } else {
                    // Claimed by ANOTHER replica
                    logger.debug("Event claimed by {} during race condition: {} (type: {})",
                            previousClaim.getClaimedBy(), eventId, event.getEventType().getTypeValue());
                    return false;
                }
            }
        } catch (Exception e) {
            // Hazelcast operation failed
            logger.error("Failed to claim event, allowing processing to continue: " + eventId, e);
            return true; // Fail-open: allow processing on error
        }
    }

    /**
     * Releases a claim (optional, for explicit cleanup).
     * <p>
     * Claims auto-expire via TTL, so this method is only needed for early cleanup
     * (e.g., when build completes quickly). In most cases, TTL expiration is sufficient.
     *
     * @param event the event to release
     */
    public static void releaseClaim(GerritTriggeredEvent event) {
        if (!isClusterModeEnabled()) {
            return;
        }

        HazelcastInstance hazelcast = HazelcastInstanceProvider.getInstance();
        if (hazelcast == null) {
            return;
        }

        try {
            String eventId = EventIdentifier.generateEventId(event);
            IMap<String, EventClaim> claimsMap = hazelcast.getMap(CLAIMS_MAP_NAME);

            EventClaim removed = claimsMap.remove(eventId);
            if (removed != null) {
                logger.debug("Released claim for event: {}", eventId);
            }
        } catch (Exception e) {
            logger.debug("Failed to release claim", e);
        }
    }

    /**
     * Gets the current instance identifier.
     * <p>
     * Uses hostname or pod name to identify this Jenkins instance.
     * Cached after first retrieval for performance.
     *
     * @return instance ID (hostname or pod name)
     */
    private static String getInstanceId() {
        if (instanceId == null) {
            synchronized (EventClaimService.class) {
                if (instanceId == null) {
                    try {
                        instanceId = System.getenv("HOSTNAME");
                        if (instanceId == null || "".equals(instanceId.trim())) {
                            instanceId = InetAddress.getLocalHost().getHostName();
                        }
                    } catch (Exception e) {
                        logger.warn("Could not determine hostname, using fallback", e);
                        instanceId = "unknown-" + System.currentTimeMillis();
                    }
                }
            }
        }
        return instanceId;
    }

    /**
     * Checks if cluster mode is enabled.
     *
     * @return true if cluster mode is enabled and Hazelcast should be used
     */
    private static boolean isClusterModeEnabled() {
        return ClusterModeProvider.isClusterModeEnabled();
    }

    /**
     * Gets the configured claim TTL in seconds.
     * <p>
     * Can be overridden via system property {@link #CLAIM_TTL_PROPERTY}.
     * Default is {@link #DEFAULT_CLAIM_TTL_SECONDS} (5 minutes).
     *
     * @return TTL in seconds
     */
    private static long getClaimTtlSeconds() {
        String ttlProperty = System.getProperty(CLAIM_TTL_PROPERTY);
        if (ttlProperty != null) {
            try {
                long ttl = Long.parseLong(ttlProperty);
                if (ttl > 0) {
                    return ttl;
                } else {
                    logger.warn("Invalid claim TTL property (must be > 0): {}, using default", ttlProperty);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid claim TTL property (not a number): {}, using default", ttlProperty);
            }
        }
        return DEFAULT_CLAIM_TTL_SECONDS;
    }

    /**
     * Gets the number of active claims in the cluster.
     * <p>
     * Useful for monitoring and debugging. Returns -1 if cluster mode is disabled
     * or Hazelcast is unavailable.
     *
     * @return claim count, or -1 if unavailable
     */
    public static int getClaimCount() {
        if (!isClusterModeEnabled()) {
            return 0;
        }

        HazelcastInstance hazelcast = HazelcastInstanceProvider.getInstance();
        if (hazelcast == null) {
            return -1;
        }

        try {
            IMap<String, EventClaim> claimsMap = hazelcast.getMap(CLAIMS_MAP_NAME);
            return claimsMap.size();
        } catch (Exception e) {
            logger.debug("Failed to get claim count", e);
            return -1;
        }
    }
}
