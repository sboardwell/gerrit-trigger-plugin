/*
 *  The MIT License
 *
 *  Copyright (c) 2010, 2014 Sony Mobile Communications Inc. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.sonyericsson.hudson.plugins.gerrit.trigger.cluster.EventIdentifier;
import com.sonyericsson.hudson.plugins.gerrit.trigger.cluster.HazelcastInstanceProvider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Cluster mode implementation of notification claiming using Hazelcast.
 * Uses distributed IMap with atomic putIfAbsent to ensure only one replica
 * sends notifications for each event.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class ClusterNotificationClaimStrategy implements NotificationClaimStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ClusterNotificationClaimStrategy.class);

    /**
     * Hazelcast map name for notification claim flags.
     */
    private static final String NOTIFICATION_FLAGS_MAP = "gerrit-trigger-notification-flags";

    /**
     * Notification claim TTL in minutes.
     */
    private static final int NOTIFICATION_CLAIM_TTL_MINUTES = 10;

    @Override
    public boolean tryClaimNotificationRight(@NonNull GerritTriggeredEvent event) {
        try {
            HazelcastInstance hz = HazelcastInstanceProvider.getInstance();
            if (hz == null) {
                logger.warn("Hazelcast unavailable for notification claim, proceeding with local mode");
                return true; // Fail-safe: send notification
            }

            IMap<String, Boolean> notificationFlags = hz.getMap(NOTIFICATION_FLAGS_MAP);
            String eventId = EventIdentifier.generateEventId(event);
            String flagKey = "notified-" + eventId;

            // Atomic operation: set flag if not already set
            Boolean previousValue = notificationFlags.putIfAbsent(
                    flagKey, Boolean.TRUE, NOTIFICATION_CLAIM_TTL_MINUTES, TimeUnit.MINUTES);

            boolean claimed = (previousValue == null);
            if (claimed) {
                logger.debug("Successfully claimed notification right for event: {}", eventId);
            } else {
                logger.debug("Another replica already claimed notification for event: {}", eventId);
            }
            return claimed;
        } catch (Exception e) {
            logger.error("Error claiming notification right, proceeding with send to avoid notification loss", e);
            return true; // Fail-safe: send notification
        }
    }

    @Override
    public void releaseNotificationRight(@NonNull GerritTriggeredEvent event) {
        try {
            HazelcastInstance hz = HazelcastInstanceProvider.getInstance();
            if (hz != null) {
                IMap<String, Boolean> notificationFlags = hz.getMap(NOTIFICATION_FLAGS_MAP);
                String eventId = EventIdentifier.generateEventId(event);
                String flagKey = "notified-" + eventId;
                notificationFlags.delete(flagKey);
                logger.trace("Released notification claim for event: {}", eventId);
            }
        } catch (Exception e) {
            logger.warn("Error releasing notification claim (non-critical)", e);
        }
    }
}
