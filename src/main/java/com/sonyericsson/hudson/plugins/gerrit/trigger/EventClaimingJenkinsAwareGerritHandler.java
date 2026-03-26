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
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.cluster.EventClaimService;
import com.sonyericsson.hudson.plugins.gerrit.trigger.cluster.EventIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension of JenkinsAwareGerritHandler that adds event claiming for cluster mode.
 * <p>
 * In cluster mode (HA/HS deployments), this handler ensures that only ONE replica
 * processes each Gerrit event by using Hazelcast-based event claiming.
 * <p>
 * When an event arrives, this handler attempts to claim it before dispatching to jobs.
 * If another replica has already claimed the event, this replica skips processing entirely.
 *
 * @author CloudBees, Inc.
 */
public class EventClaimingJenkinsAwareGerritHandler extends JenkinsAwareGerritHandler {

    private static final Logger logger =
        LoggerFactory.getLogger(EventClaimingJenkinsAwareGerritHandler.class);

    /**
     * Constructor for cluster-aware event handler.
     *
     * @param numberOfWorkerThreads the number of event threads.
     */
    public EventClaimingJenkinsAwareGerritHandler(int numberOfWorkerThreads) {
        super(numberOfWorkerThreads);
    }

    @Override
    public void notifyListeners(GerritEvent event) {
        // Try to claim the event in cluster mode BEFORE dispatching to jobs
        // This ensures only ONE replica processes the event across ALL jobs
        if (event instanceof GerritTriggeredEvent) {
            GerritTriggeredEvent triggeredEvent = (GerritTriggeredEvent)event;
            if (!EventClaimService.tryClaimEvent(triggeredEvent)) {
                // Another replica has already claimed this event - skip all job processing
                logger.debug("Event already claimed by another replica, skipping all jobs: {}",
                        EventIdentifier.generateEventId(triggeredEvent));
                return;
            }
            logger.trace("Successfully claimed event for this replica: {}",
                    EventIdentifier.generateEventId(triggeredEvent));
        }

        // Call parent to handle the rest (lifecycle events, ACL context, actual dispatch)
        super.notifyListeners(event);
    }
}
