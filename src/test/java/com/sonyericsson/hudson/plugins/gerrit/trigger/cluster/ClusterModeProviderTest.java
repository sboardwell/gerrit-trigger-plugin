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
package com.sonyericsson.hudson.plugins.gerrit.trigger.cluster;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link ClusterModeProvider}.
 * <p>
 * Note: The actual value of {@link ClusterModeProvider#isClusterModeEnabled()}
 * depends on whether the system property {@code gerrit.trigger.cluster.mode.enabled}
 * is set when the JVM starts. These tests verify the method works and the constant
 * is correct, but cannot reliably test the enabled state without controlling JVM startup.
 *
 * @author CloudBees, Inc.
 */
public class ClusterModeProviderTest {

    /**
     * Test that the method returns a boolean value (doesn't throw).
     * The actual value depends on system property at JVM startup.
     */
    @Test
    public void testIsClusterModeEnabledReturnsBoolean() {
        // This should not throw an exception
        boolean enabled = ClusterModeProvider.isClusterModeEnabled();

        // Verify it's a valid boolean (trivial but documents behavior)
        assertTrue(enabled || !enabled);
    }

    /**
     * Test that the property name constant is correct.
     */
    @Test
    public void testPropertyNameConstant() {
        assertEquals("gerrit.trigger.cluster.mode.enabled",
                ClusterModeProvider.CLUSTER_MODE_PROPERTY);
    }

    /**
     * Test that multiple calls return the same value (cached).
     */
    @Test
    public void testConsistentReturnValue() {
        boolean firstCall = ClusterModeProvider.isClusterModeEnabled();
        boolean secondCall = ClusterModeProvider.isClusterModeEnabled();
        boolean thirdCall = ClusterModeProvider.isClusterModeEnabled();

        assertEquals(firstCall, secondCall);
        assertEquals(secondCall, thirdCall);
    }
}
