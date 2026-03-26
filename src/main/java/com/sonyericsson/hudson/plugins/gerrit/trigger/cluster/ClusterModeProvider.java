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

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for checking cluster mode status via system property.
 * <p>
 * Cluster mode is enabled by setting the system property:
 * <pre>-Dgerrit.trigger.cluster.mode.enabled=true</pre>
 * <p>
 * This property must be set before Jenkins starts and cannot be changed at runtime.
 * A restart is required to enable or disable cluster mode.
 * <p>
 * Example usage:
 * <pre>
 * # Kubernetes deployment
 * env:
 *   - name: JAVA_OPTS
 *     value: "-Dgerrit.trigger.cluster.mode.enabled=true"
 *
 * # Traditional deployment
 * export JAVA_OPTS="$JAVA_OPTS -Dgerrit.trigger.cluster.mode.enabled=true"
 * </pre>
 *
 * @author CloudBees, Inc.
 */
public final class ClusterModeProvider {

    private static final Logger logger = LoggerFactory.getLogger(ClusterModeProvider.class);

    /**
     * System property to enable cluster mode.
     */
    public static final String CLUSTER_MODE_PROPERTY = "gerrit.trigger.cluster.mode.enabled";

    /**
     * Cached cluster mode status (read once at class loading).
     */
    private static final boolean CLUSTER_MODE_ENABLED;

    /**
     * Test override for cluster mode.
     * When non-null, this value is returned by {@link #isClusterModeEnabled()} instead of
     * the system property value. This allows tests to control cluster mode behavior without
     * setting system properties.
     * <p>
     * <strong>FOR TESTING ONLY</strong> - Do not use in production code.
     */
    private static volatile Boolean testOverride = null;

    static {
        CLUSTER_MODE_ENABLED = Boolean.getBoolean(CLUSTER_MODE_PROPERTY);
        String status;
        if (CLUSTER_MODE_ENABLED) {
            status = "ENABLED";
        } else {
            status = "DISABLED";
        }
        logger.info("Gerrit Trigger cluster mode: {} (property: {}={})",
                status,
                CLUSTER_MODE_PROPERTY,
                System.getProperty(CLUSTER_MODE_PROPERTY, "not set"));
    }

    /**
     * Private constructor - utility class.
     */
    private ClusterModeProvider() {
    }

    /**
     * Checks if cluster mode is enabled.
     * <p>
     * This value is determined at startup by reading the system property
     * {@link #CLUSTER_MODE_PROPERTY} and cannot change during runtime.
     * <p>
     * In test environments, this can be overridden using {@link #setTestMode(Boolean)}.
     *
     * @return true if cluster mode is enabled via system property (or test override)
     */
    public static boolean isClusterModeEnabled() {
        // Check test override first (for testing only)
        if (testOverride != null) {
            return testOverride;
        }
        return CLUSTER_MODE_ENABLED;
    }

    /**
     * Sets a test override for cluster mode status.
     * <p>
     * <strong>FOR TESTING ONLY</strong> - This method should only be called from test code.
     * When set, {@link #isClusterModeEnabled()} will return this value instead of reading
     * the system property.
     * <p>
     * <strong>Important:</strong> Always call {@link #clearTestMode()} after the test
     * (typically in an {@code @After} method) to avoid affecting other tests.
     *
     * @param enabled the test cluster mode status (true = enabled, false = disabled)
     */
    @VisibleForTesting
    public static void setTestMode(Boolean enabled) {
        testOverride = enabled;
    }

    /**
     * Clears the test override, restoring normal system property behavior.
     * <p>
     * <strong>FOR TESTING ONLY</strong> - Should be called in {@code @After} methods
     * to clean up test state.
     */
    @VisibleForTesting
    public static void clearTestMode() {
        testOverride = null;
    }
}
