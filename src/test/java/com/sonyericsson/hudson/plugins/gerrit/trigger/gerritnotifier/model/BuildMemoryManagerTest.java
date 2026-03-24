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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BuildMemoryManager coordination service.
 * Tests verify that BuildMemoryManager correctly coordinates migrations
 * between local and distributed storage modes.
 *
 * @author CloudBees, Inc.
 */
@RunWith(MockitoJUnitRunner.class)
public class BuildMemoryManagerTest {

    private MockedStatic<ToGerritRunListener> listenerStatic;
    private ToGerritRunListener mockListener;
    private BuildMemory mockMemory;

    /**
     * Setup method called before each test.
     * Creates mocks for ToGerritRunListener and BuildMemory.
     */
    @Before
    public void setUp() {
        mockListener = mock(ToGerritRunListener.class);
        mockMemory = mock(BuildMemory.class);

        listenerStatic = mockStatic(ToGerritRunListener.class);
        listenerStatic.when(ToGerritRunListener::getInstance).thenReturn(mockListener);
        when(mockListener.getMemory()).thenReturn(mockMemory);
    }

    /**
     * Cleanup method called after each test.
     * Closes mocked static instances.
     */
    @After
    public void tearDown() {
        if (listenerStatic != null) {
            listenerStatic.close();
        }
    }

    /**
     * Test successful migration to distributed storage.
     * Verifies BuildMemoryManager calls BuildMemory.migrateToDistributed().
     */
    @Test
    public void testMigrateToDistributedSuccess() {
        // Call migration
        BuildMemoryManager.migrateToDistributed();

        // Verify BuildMemory migration was called
        verify(mockMemory, times(1)).migrateToDistributed();
    }

    /**
     * Test successful migration to local storage.
     * Verifies BuildMemoryManager calls BuildMemory.migrateToLocal().
     */
    @Test
    public void testMigrateToLocalSuccess() {
        // Call migration
        BuildMemoryManager.migrateToLocal();

        // Verify BuildMemory migration was called
        verify(mockMemory, times(1)).migrateToLocal();
    }

    /**
     * Test migration when ToGerritRunListener is unavailable.
     * Verifies graceful handling (no exception, logs warning).
     */
    @Test
    public void testMigrateToDistributedWhenListenerUnavailable() {
        // Mock listener as unavailable
        listenerStatic.when(ToGerritRunListener::getInstance).thenReturn(null);

        // Call migration (should not throw exception)
        BuildMemoryManager.migrateToDistributed();

        // Verify BuildMemory migration was NOT called (since listener unavailable)
        verify(mockMemory, times(0)).migrateToDistributed();
    }

    /**
     * Test migration when BuildMemory is unavailable.
     * Verifies graceful handling (no exception, logs warning).
     */
    @Test
    public void testMigrateToDistributedWhenMemoryUnavailable() {
        // Mock memory as unavailable
        when(mockListener.getMemory()).thenReturn(null);

        // Call migration (should not throw exception)
        BuildMemoryManager.migrateToDistributed();

        // Verify no NPE (test passes if we get here)
    }

    /**
     * Test migration when BuildMemory.migrateToDistributed() throws exception.
     * Verifies exception is caught and logged (non-fatal).
     */
    @Test
    public void testMigrateToDistributedWithException() {
        // Mock BuildMemory to throw exception
        doThrow(new RuntimeException("Test exception")).when(mockMemory).migrateToDistributed();

        // Call migration (should not throw exception)
        BuildMemoryManager.migrateToDistributed();

        // Verify BuildMemory migration was attempted
        verify(mockMemory, times(1)).migrateToDistributed();
    }

    /**
     * Test migration when BuildMemory.migrateToLocal() throws exception.
     * Verifies exception is caught and logged (non-fatal).
     */
    @Test
    public void testMigrateToLocalWithException() {
        // Mock BuildMemory to throw exception
        doThrow(new RuntimeException("Test exception")).when(mockMemory).migrateToLocal();

        // Call migration (should not throw exception)
        BuildMemoryManager.migrateToLocal();

        // Verify BuildMemory migration was attempted
        verify(mockMemory, times(1)).migrateToLocal();
    }

    /**
     * Test isAvailable() when both listener and memory are available.
     */
    @Test
    public void testIsAvailableWhenAllComponentsPresent() {
        // Verify isAvailable returns true
        assertTrue("Should be available when components present", BuildMemoryManager.isAvailable());
    }

    /**
     * Test isAvailable() when ToGerritRunListener is unavailable.
     */
    @Test
    public void testIsAvailableWhenListenerUnavailable() {
        // Mock listener as unavailable
        listenerStatic.when(ToGerritRunListener::getInstance).thenReturn(null);

        // Verify isAvailable returns false
        assertFalse("Should not be available when listener unavailable", BuildMemoryManager.isAvailable());
    }

    /**
     * Test isAvailable() when BuildMemory is unavailable.
     */
    @Test
    public void testIsAvailableWhenMemoryUnavailable() {
        // Mock memory as unavailable
        when(mockListener.getMemory()).thenReturn(null);

        // Verify isAvailable returns false
        assertFalse("Should not be available when memory unavailable", BuildMemoryManager.isAvailable());
    }

    /**
     * Test multiple sequential migrations.
     * Verifies manager can handle repeated migration calls.
     */
    @Test
    public void testSequentialMigrations() {
        // Perform multiple migrations
        BuildMemoryManager.migrateToDistributed();
        BuildMemoryManager.migrateToLocal();
        BuildMemoryManager.migrateToDistributed();
        BuildMemoryManager.migrateToLocal();

        // Verify each migration was called
        verify(mockMemory, times(2)).migrateToDistributed();
        verify(mockMemory, times(2)).migrateToLocal();
    }

    /**
     * Test that BuildMemoryManager is fail-safe.
     * Verifies plugin continues to work even if migrations fail.
     */
    @Test
    public void testFailSafeBehavior() {
        // First migration fails
        doThrow(new RuntimeException("First failure")).when(mockMemory).migrateToDistributed();
        BuildMemoryManager.migrateToDistributed();

        // Second migration should still be attempted (fail-safe)
        BuildMemoryManager.migrateToDistributed();

        // Verify both attempts were made
        verify(mockMemory, times(2)).migrateToDistributed();
    }
}
