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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.cluster.BuildMemoryKey;
import com.sonyericsson.hudson.plugins.gerrit.trigger.cluster.HazelcastInstanceProvider;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.PluginConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;

import java.util.concurrent.ConcurrentHashMap;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BuildMemory dual-mode operation (local vs. distributed).
 * Tests verify that BuildMemory works correctly in both standalone and cluster modes.
 *
 * @author CloudBees, Inc.
 */
@RunWith(MockitoJUnitRunner.class)
public class BuildMemoryDualModeTest {

    private static final int BUILD_NUMBER = 42;
    private BuildMemory memory;
    private PatchsetCreated event;
    private AbstractProject project;
    private AbstractBuild build;
    private MockedStatic<Jenkins> jenkinsStatic;
    private MockedStatic<PluginImpl> pluginStatic;
    private MockedStatic<HazelcastInstanceProvider> hazelcastStatic;

    /**
     * Setup method called before each test.
     * Creates mock objects and initializes BuildMemory.
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

        // Setup project and build mocks
        project = mock(AbstractProject.class);
        when(project.getFullName()).thenReturn("test-project");

        build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        when(build.getParent()).thenReturn(project);
        when(build.getNumber()).thenReturn(BUILD_NUMBER);
        when(build.getId()).thenReturn(String.valueOf(BUILD_NUMBER));

        // Initialize BuildMemory
        memory = new BuildMemory();
    }

    /**
     * Cleanup method called after each test.
     * Closes mocked static instances.
     */
    @After
    public void tearDown() {
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
     * Test that BuildMemory works correctly in local mode (cluster disabled).
     * Verifies data is stored in local TreeMap.
     */
    @Test
    public void testLocalModeOperation() {
        // Mock cluster mode as disabled
        mockClusterModeDisabled();

        // Trigger build
        memory.triggered(event, project);

        // Verify event is stored
        MemoryImprint imprint = memory.getMemoryImprint(event);
        assertNotNull("Imprint should exist in local mode", imprint);
        assertTrue("Should have entries in local mode", imprint.getEntries().length > 0);

        // Start build
        memory.started(event, build);
        imprint = memory.getMemoryImprint(event);
        assertFalse("Not all builds started yet", memory.isAllBuildsStarted(event));

        // Complete build
        memory.completed(event, build);
        imprint = memory.getMemoryImprint(event);
        assertTrue("All builds should be completed", memory.isAllBuildsCompleted(event));
    }

    /**
     * Test that BuildMemory works correctly in distributed mode (cluster enabled).
     * Verifies data is stored in Hazelcast IMap.
     */
    @Test
    public void testDistributedModeOperation() {
        // Mock cluster mode as enabled with Hazelcast
        HazelcastInstance hz = mockClusterModeEnabled();
        @SuppressWarnings("unchecked")
        IMap<BuildMemoryKey, Object> distributedMap =
                (IMap<BuildMemoryKey, Object>)(IMap)hz.getMap("gerrit-trigger-build-memory");

        // Verify map starts empty
        assertTrue("Distributed map should start empty", distributedMap.isEmpty());

        // Trigger build
        memory.triggered(event, project);

        // Verify event is stored in distributed map
        assertFalse("Event should be in distributed map", distributedMap.isEmpty());
        MemoryImprint imprint = memory.getMemoryImprint(event);
        assertNotNull("Imprint should exist in distributed mode", imprint);
        assertTrue("Should have entries in distributed mode", imprint.getEntries().length > 0);

        // Start build
        memory.started(event, build);
        imprint = memory.getMemoryImprint(event);
        assertFalse("Not all builds started yet", memory.isAllBuildsStarted(event));

        // Complete build
        memory.completed(event, build);
        imprint = memory.getMemoryImprint(event);
        assertTrue("All builds should be completed", memory.isAllBuildsCompleted(event));
    }

    /**
     * Test migration from local to distributed mode.
     * Verifies data is copied from TreeMap to Hazelcast IMap.
     */
    @Test
    public void testMigrationLocalToDistributed() {
        // Start in local mode
        mockClusterModeDisabled();

        // Add data in local mode
        memory.triggered(event, project);
        memory.started(event, build);

        // Verify data exists in local mode
        MemoryImprint localImprint = memory.getMemoryImprint(event);
        assertNotNull("Data should exist in local mode", localImprint);
        assertTrue("Should have entries", localImprint.getEntries().length > 0);

        // Enable cluster mode
        HazelcastInstance hz = mockClusterModeEnabled();
        @SuppressWarnings("unchecked")
        IMap<BuildMemoryKey, Object> distributedMap =
                (IMap<BuildMemoryKey, Object>)(IMap)hz.getMap("gerrit-trigger-build-memory");

        // Perform migration
        memory.migrateToDistributed();

        // Verify data exists in distributed mode
        assertFalse("Distributed map should contain data", distributedMap.isEmpty());
        MemoryImprint distributedImprint = memory.getMemoryImprint(event);
        assertNotNull("Data should exist in distributed mode after migration", distributedImprint);
        assertTrue("Should have entries after migration", distributedImprint.getEntries().length > 0);
        assertEquals("Entry count should match", localImprint.getEntries().length,
                     distributedImprint.getEntries().length);
    }

    /**
     * Test migration from distributed to local mode.
     * Verifies data is copied from Hazelcast IMap to TreeMap.
     */
    @Test
    public void testMigrationDistributedToLocal() {
        // Start in distributed mode
        HazelcastInstance hz = mockClusterModeEnabled();
        @SuppressWarnings("unchecked")
        IMap<BuildMemoryKey, Object> distributedMap =
                (IMap<BuildMemoryKey, Object>)(IMap)hz.getMap("gerrit-trigger-build-memory");

        // Add data in distributed mode
        memory.triggered(event, project);
        memory.started(event, build);

        // Verify data exists in distributed mode
        MemoryImprint distributedImprint = memory.getMemoryImprint(event);
        assertNotNull("Data should exist in distributed mode", distributedImprint);
        assertTrue("Should have entries", distributedImprint.getEntries().length > 0);

        // Disable cluster mode
        mockClusterModeDisabled();

        // Perform migration
        memory.migrateToLocal();

        // Verify data exists in local mode
        MemoryImprint localImprint = memory.getMemoryImprint(event);
        assertNotNull("Data should exist in local mode after migration", localImprint);
        assertTrue("Should have entries after migration", localImprint.getEntries().length > 0);
        assertEquals("Entry count should match", distributedImprint.getEntries().length,
                     localImprint.getEntries().length);
    }

    /**
     * Test fail-safe behavior when Hazelcast is unavailable.
     * Verifies BuildMemory falls back to local mode.
     */
    @Test
    public void testFailSafeBehavior() {
        // Mock cluster mode enabled but Hazelcast unavailable
        mockClusterModeEnabledWithoutHazelcast();

        // Try to trigger build (should fall back to local)
        memory.triggered(event, project);

        // Verify operation succeeds in local mode
        MemoryImprint imprint = memory.getMemoryImprint(event);
        assertNotNull("Should fall back to local mode", imprint);
        assertTrue("Should have entries in fallback mode", imprint.getEntries().length > 0);

        // Start and complete build
        memory.started(event, build);
        memory.completed(event, build);

        // Verify completion works in fallback mode
        assertTrue("Should complete in fallback mode", memory.isAllBuildsCompleted(event));
    }

    /**
     * Test that data is isolated between local and distributed modes.
     * Verifies that switching modes without migration loses data (expected behavior).
     */
    @Test
    public void testModeIsolation() {
        // Start in local mode
        mockClusterModeDisabled();
        memory.triggered(event, project);

        // Verify data exists in local mode
        assertNotNull("Data should exist in local mode", memory.getMemoryImprint(event));

        // Switch to distributed mode WITHOUT migration
        mockClusterModeEnabled();

        // Verify data does NOT exist in distributed mode (no migration)
        MemoryImprint imprint = memory.getMemoryImprint(event);
        assertNull("Data should not exist in distributed mode without migration", imprint);
    }

    /**
     * Test bidirectional migration.
     * Verifies data survives round-trip: local → distributed → local.
     */
    @Test
    public void testBidirectionalMigration() {
        // Start in local mode
        mockClusterModeDisabled();
        memory.triggered(event, project);
        memory.started(event, build);

        // Migrate to distributed
        mockClusterModeEnabled();
        memory.migrateToDistributed();

        // Verify in distributed mode
        MemoryImprint distributedImprint = memory.getMemoryImprint(event);
        assertNotNull("Should exist in distributed", distributedImprint);

        // Migrate back to local
        mockClusterModeDisabled();
        memory.migrateToLocal();

        // Verify in local mode again
        MemoryImprint finalImprint = memory.getMemoryImprint(event);
        assertNotNull("Should exist in local after round-trip", finalImprint);
        assertTrue("Should still have entries", finalImprint.getEntries().length > 0);
    }

    /**
     * Test that forget() works in both modes.
     */
    @Test
    public void testForgetInBothModes() {
        // Test local mode
        mockClusterModeDisabled();
        memory.triggered(event, project);
        assertNotNull("Should exist before forget", memory.getMemoryImprint(event));
        memory.forget(event);
        assertNull("Should not exist after forget in local mode", memory.getMemoryImprint(event));

        // Test distributed mode
        HazelcastInstance hz = mockClusterModeEnabled();
        memory.triggered(event, project);
        assertNotNull("Should exist before forget", memory.getMemoryImprint(event));
        memory.forget(event);
        assertNull("Should not exist after forget in distributed mode", memory.getMemoryImprint(event));
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
        when(config.isClusterModeEnabled()).thenReturn(false);
        when(plugin.getPluginConfig()).thenReturn(config);

        pluginStatic = mockStatic(PluginImpl.class);
        pluginStatic.when(PluginImpl::getInstance).thenReturn(plugin);
    }

    /**
     * Mocks cluster mode as enabled with Hazelcast available.
     *
     * @return the mocked HazelcastInstance
     */
    private HazelcastInstance mockClusterModeEnabled() {
        if (pluginStatic != null) {
            pluginStatic.close();
        }
        if (hazelcastStatic != null) {
            hazelcastStatic.close();
        }

        // Mock plugin config with cluster mode enabled
        PluginImpl plugin = mock(PluginImpl.class);
        PluginConfig config = mock(PluginConfig.class);
        when(config.isClusterModeEnabled()).thenReturn(true);
        when(plugin.getPluginConfig()).thenReturn(config);

        pluginStatic = mockStatic(PluginImpl.class);
        pluginStatic.when(PluginImpl::getInstance).thenReturn(plugin);

        // Mock Hazelcast instance with real map backing
        HazelcastInstance hz = mock(HazelcastInstance.class);

        // Use ConcurrentHashMap to simulate IMap behavior
        ConcurrentHashMap<BuildMemoryKey, Object> backingMap = new ConcurrentHashMap<>();
        @SuppressWarnings("unchecked")
        IMap<BuildMemoryKey, Object> distributedMap = (IMap<BuildMemoryKey, Object>)mock(IMap.class);

        // Mock IMap operations to use backing map
        when(distributedMap.put(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                BuildMemoryKey key = invocation.getArgument(0);
                Object value = invocation.getArgument(1);
                return backingMap.put(key, value);
            });
        when(distributedMap.get(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                BuildMemoryKey key = invocation.getArgument(0);
                return backingMap.get(key);
            });
        when(distributedMap.remove(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                BuildMemoryKey key = invocation.getArgument(0);
                return backingMap.remove(key);
            });
        when(distributedMap.isEmpty()).thenAnswer(invocation -> backingMap.isEmpty());
        when(distributedMap.size()).thenAnswer(invocation -> backingMap.size());
        when(distributedMap.containsKey(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                BuildMemoryKey key = invocation.getArgument(0);
                return backingMap.containsKey(key);
            });

        when(hz.getMap("gerrit-trigger-build-memory")).thenReturn((IMap)distributedMap);

        hazelcastStatic = mockStatic(HazelcastInstanceProvider.class);
        hazelcastStatic.when(HazelcastInstanceProvider::getInstance).thenReturn(hz);

        return hz;
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
        when(config.isClusterModeEnabled()).thenReturn(true);
        when(plugin.getPluginConfig()).thenReturn(config);

        pluginStatic = mockStatic(PluginImpl.class);
        pluginStatic.when(PluginImpl::getInstance).thenReturn(plugin);

        // Mock Hazelcast as unavailable (returns null)
        hazelcastStatic = mockStatic(HazelcastInstanceProvider.class);
        hazelcastStatic.when(HazelcastInstanceProvider::getInstance).thenReturn(null);
    }
}
