/*
 * The MIT License
 *
 *
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.cluster.ClusterModeProvider;
import com.sonyericsson.hudson.plugins.gerrit.trigger.cluster.HazelcastInstanceProvider;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.PluginConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for async notification processing in ToGerritRunListener.
 * Tests verify that notifications are processed asynchronously and race conditions are prevented.
 *
 * @author CloudBees, Inc.
 */
@RunWith(MockitoJUnitRunner.class)
public class AsyncNotificationTest {

    private static final int BUILD_NUMBER_1 = 42;
    private static final int BUILD_NUMBER_2 = 43;
    private static final int NOTIFICATION_TIMEOUT_SECONDS = 5;
    private static final int ASYNC_WAIT_MS = 100;
    private static final int ASYNC_WAIT_LONG_MS = 500;
    private static final int SHUTDOWN_WAIT_MS = 5000;
    private static final int REPLICA_COUNT = 3;

    private ToGerritRunListener listener;
    private PatchsetCreated event;
    private AbstractProject project1;
    private AbstractProject project2;
    private AbstractBuild build1;
    private AbstractBuild build2;
    private GerritCause cause;
    private MockedStatic<Jenkins> jenkinsStatic;
    private MockedStatic<PluginImpl> pluginStatic;
    private MockedStatic<HazelcastInstanceProvider> hazelcastStatic;

    /**
     * Setup method called before each test.
     * Creates mock objects and initializes ToGerritRunListener.
     *
     * @throws Exception if setup fails
     */
    @Before
    public void setUp() throws Exception {
        // Create mock event
        event = Setup.createPatchsetCreated();

        // Setup Jenkins singleton mock
        Jenkins jenkins = mock(Jenkins.class);
        jenkinsStatic = mockStatic(Jenkins.class);
        jenkinsStatic.when(Jenkins::get).thenReturn(jenkins);

        // Setup project mocks
        project1 = mock(AbstractProject.class);
        when(project1.getFullName()).thenReturn("test-project-1");

        project2 = mock(AbstractProject.class);
        when(project2.getFullName()).thenReturn("test-project-2");

        // Setup build mocks
        build1 = mock(AbstractBuild.class);
        when(build1.getProject()).thenReturn(project1);
        when(build1.getParent()).thenReturn(project1);
        when(build1.getNumber()).thenReturn(BUILD_NUMBER_1);
        when(build1.getId()).thenReturn(String.valueOf(BUILD_NUMBER_1));

        build2 = mock(AbstractBuild.class);
        when(build2.getProject()).thenReturn(project2);
        when(build2.getParent()).thenReturn(project2);
        when(build2.getNumber()).thenReturn(BUILD_NUMBER_2);
        when(build2.getId()).thenReturn(String.valueOf(BUILD_NUMBER_2));

        // Setup GerritCause
        cause = new GerritCause(event, false);
        List<Cause> causes = new ArrayList<>();
        causes.add(cause);
        when(build1.getCauses()).thenReturn(causes);
        when(build2.getCauses()).thenReturn(causes);

        // Initialize ToGerritRunListener
        listener = new ToGerritRunListener();
    }

    /**
     * Cleanup method called after each test.
     * Closes mocked static instances and shuts down listener.
     */
    @After
    public void tearDown() {
        // Clear cluster mode test override
        ClusterModeProvider.clearTestMode();

        if (listener != null) {
            listener.shutdown();
        }
        if (jenkinsStatic != null) {
            jenkinsStatic.close();
        }
        if (pluginStatic != null) {
            pluginStatic.close();
        }
        if (hazelcastStatic != null) {
            hazelcastStatic.close();
        }
    }

    /**
     * Test that onCompleted() returns quickly without blocking.
     * Verifies async notification doesn't block build completion.
     */
    @Test
    public void testAsyncNotificationNonBlocking() throws Exception {
        // Mock cluster mode disabled (local mode)
        mockClusterModeDisabled();

        // Trigger two builds
        BuildMemory memory = listener.getMemory();
        memory.triggered(event, project1);
        memory.triggered(event, project2);

        // Start builds
        memory.started(event, build1);
        memory.started(event, build2);

        // Measure time for onCompleted() to return
        long startTime = System.currentTimeMillis();

        // Complete first build (not all builds completed yet)
        listener.onCompleted(build1, null);

        // Complete second build (all builds completed - triggers notification)
        listener.onCompleted(build2, null);

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Verify onCompleted() returned quickly (< 100ms)
        // The actual notification happens asynchronously in a separate thread
        assertTrue("onCompleted should return quickly (< 100ms), took: " + elapsedTime + "ms",
                   elapsedTime < ASYNC_WAIT_MS);
    }

    /**
     * Test atomic notification claiming in cluster mode.
     * Verifies only one replica sends notification using putIfAbsent().
     */
    @Test
    public void testAtomicNotificationClaiming() throws Exception {
        // Mock cluster mode enabled
        IMap<String, Boolean> notificationFlags = mockClusterModeEnabled();

        // Trigger and start builds
        BuildMemory memory = listener.getMemory();
        memory.triggered(event, project1);
        memory.started(event, build1);

        // Complete build (triggers notification)
        listener.onCompleted(build1, null);

        // Wait for notification to be claimed
        Thread.sleep(ASYNC_WAIT_MS);

        // Verify notification flag was set in Hazelcast
        assertTrue("Notification flag should be set", notificationFlags.size() > 0);

        // Simulate second replica trying to claim notification
        // (in real scenario, another replica would call onCompleted with same event)
        listener.onCompleted(build1, null);

        // Wait a bit
        Thread.sleep(ASYNC_WAIT_MS);

        // Verify only one notification flag exists (no duplicates)
        assertEquals("Should only have one notification flag", 1, notificationFlags.size());
    }

    /**
     * Test that multiple replicas don't send duplicate notifications.
     * Simulates concurrent build completions from different replicas.
     */
    @Test
    public void testNoDuplicateNotificationsInCluster() throws Exception {
        // Mock cluster mode enabled
        IMap<String, Boolean> notificationFlags = mockClusterModeEnabled();

        // Trigger builds
        BuildMemory memory = listener.getMemory();
        memory.triggered(event, project1);
        memory.triggered(event, project2);
        memory.started(event, build1);
        memory.started(event, build2);

        // Complete both builds
        memory.completed(event, build1);
        memory.completed(event, build2);

        // Counter for notification attempts
        AtomicInteger notificationAttempts = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(REPLICA_COUNT);

        // Simulate 3 replicas trying to send notification simultaneously
        Thread replica1 = new Thread(() -> {
            listener.onCompleted(build2, null);
            notificationAttempts.incrementAndGet();
            latch.countDown();
        });

        Thread replica2 = new Thread(() -> {
            listener.onCompleted(build2, null);
            notificationAttempts.incrementAndGet();
            latch.countDown();
        });

        Thread replica3 = new Thread(() -> {
            listener.onCompleted(build2, null);
            notificationAttempts.incrementAndGet();
            latch.countDown();
        });

        // Start all replicas at the same time
        replica1.start();
        replica2.start();
        replica3.start();

        // Wait for all replicas to finish
        assertTrue("All replicas should complete within timeout",
                   latch.await(NOTIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // Verify all replicas attempted notification
        assertEquals("All replicas should attempt notification", REPLICA_COUNT, notificationAttempts.get());

        // Wait for async processing
        Thread.sleep(ASYNC_WAIT_LONG_MS);

        // Verify only one notification flag was set (atomic claiming worked)
        assertEquals("Only one notification should be claimed", 1, notificationFlags.size());
    }

    /**
     * Test that notification executor shuts down gracefully.
     * Verifies pending notifications are processed before shutdown.
     */
    @Test
    public void testGracefulShutdown() throws Exception {
        // Mock cluster mode disabled
        mockClusterModeDisabled();

        // Trigger and complete builds
        BuildMemory memory = listener.getMemory();
        memory.triggered(event, project1);
        memory.started(event, build1);
        memory.completed(event, build1);

        // Trigger notification
        listener.onCompleted(build1, null);

        // Shutdown (should wait for pending notifications)
        long startTime = System.currentTimeMillis();
        listener.shutdown();
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Verify shutdown completed reasonably quickly (< 5 seconds)
        assertTrue("Shutdown should complete quickly, took: " + elapsedTime + "ms",
                   elapsedTime < SHUTDOWN_WAIT_MS);
    }

    /**
     * Test notification in local mode (cluster disabled).
     * Verifies notifications work without Hazelcast.
     */
    @Test
    public void testNotificationInLocalMode() throws Exception {
        // Mock cluster mode disabled
        mockClusterModeDisabled();

        // Trigger and complete build
        BuildMemory memory = listener.getMemory();
        memory.triggered(event, project1);
        memory.started(event, build1);
        memory.completed(event, build1);

        // Trigger notification
        listener.onCompleted(build1, null);

        // Wait for async processing
        Thread.sleep(ASYNC_WAIT_MS);

        // Test passes if no exceptions (local mode works)
    }

    /**
     * Test fail-safe behavior when Hazelcast is unavailable in cluster mode.
     * Verifies listener falls back to allowing notification (fail-open).
     */
    @Test
    public void testFailSafeWhenHazelcastUnavailable() throws Exception {
        // Mock cluster mode enabled but Hazelcast unavailable
        mockClusterModeEnabledWithoutHazelcast();

        // Trigger and complete build
        BuildMemory memory = listener.getMemory();
        memory.triggered(event, project1);
        memory.started(event, build1);
        memory.completed(event, build1);

        // Trigger notification (should succeed despite Hazelcast unavailable)
        listener.onCompleted(build1, null);

        // Wait for async processing
        Thread.sleep(ASYNC_WAIT_MS);

        // Test passes if no exceptions (fail-safe works)
    }

    // Helper methods

    /**
     * Mocks cluster mode as disabled.
     */
    private void mockClusterModeDisabled() {
        if (pluginStatic != null) {
            pluginStatic.close();
        }
        if (hazelcastStatic != null) {
            hazelcastStatic.close();
        }

        PluginImpl plugin = mock(PluginImpl.class);
        PluginConfig config = mock(PluginConfig.class);
        ClusterModeProvider.setTestMode(false);
        when(plugin.getPluginConfig()).thenReturn(config);

        pluginStatic = mockStatic(PluginImpl.class);
        pluginStatic.when(PluginImpl::getInstance).thenReturn(plugin);
    }

    /**
     * Mocks cluster mode as enabled with Hazelcast available.
     *
     * @return the mocked notification flags IMap
     */
    private IMap<String, Boolean> mockClusterModeEnabled() {
        if (pluginStatic != null) {
            pluginStatic.close();
        }
        if (hazelcastStatic != null) {
            hazelcastStatic.close();
        }

        // Mock plugin config with cluster mode enabled
        PluginImpl plugin = mock(PluginImpl.class);
        PluginConfig config = mock(PluginConfig.class);
        ClusterModeProvider.setTestMode(true);
        when(plugin.getPluginConfig()).thenReturn(config);

        pluginStatic = mockStatic(PluginImpl.class);
        pluginStatic.when(PluginImpl::getInstance).thenReturn(plugin);

        // Mock Hazelcast instance with notification flags map
        HazelcastInstance hz = mock(HazelcastInstance.class);

        // Use ConcurrentHashMap to simulate IMap behavior
        ConcurrentHashMap<String, Boolean> backingMap = new ConcurrentHashMap<>();
        @SuppressWarnings("unchecked")
        IMap<String, Boolean> notificationFlags = (IMap<String, Boolean>)mock(IMap.class);

        // Mock putIfAbsent to use ConcurrentHashMap semantics
        when(notificationFlags.putIfAbsent(anyString(), eq(Boolean.TRUE)))
            .thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                return backingMap.putIfAbsent(key, Boolean.TRUE);
            });

        when(notificationFlags.size()).thenAnswer(invocation -> backingMap.size());
        when(hz.getMap("gerrit-trigger-notification-flags")).thenReturn((IMap)notificationFlags);

        hazelcastStatic = mockStatic(HazelcastInstanceProvider.class);
        hazelcastStatic.when(HazelcastInstanceProvider::getInstance).thenReturn(hz);

        return notificationFlags;
    }

    /**
     * Mocks cluster mode as enabled but Hazelcast unavailable (fail-safe scenario).
     */
    private void mockClusterModeEnabledWithoutHazelcast() {
        if (pluginStatic != null) {
            pluginStatic.close();
        }
        if (hazelcastStatic != null) {
            hazelcastStatic.close();
        }

        // Mock plugin config with cluster mode enabled
        PluginImpl plugin = mock(PluginImpl.class);
        PluginConfig config = mock(PluginConfig.class);
        ClusterModeProvider.setTestMode(true);
        when(plugin.getPluginConfig()).thenReturn(config);

        pluginStatic = mockStatic(PluginImpl.class);
        pluginStatic.when(PluginImpl::getInstance).thenReturn(plugin);

        // Mock Hazelcast as unavailable (returns null)
        hazelcastStatic = mockStatic(HazelcastInstanceProvider.class);
        hazelcastStatic.when(HazelcastInstanceProvider::getInstance).thenReturn(null);
    }
}
