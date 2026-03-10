/*
 *  The MIT License
 *
 *  Copyright 2013 rinrinne All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.config;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.junit.After;
import org.junit.Test;

import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

/**
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public class PluginConfigTest {

    //CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mocks tests.

    /**
     * Resets the GerritEventType enum.
     */
    @After
    public void afterTest() {
     // TODO if an event type is added with a default other than true
        // in the future then this needs to be updated to check each
        // events default value.
        for (GerritEventType type : GerritEventType.values()) {
            type.setInteresting(true);
        }
    }

    /**
     * Test creation of a config object from form data.
     * filterIn using partial event list.
     */
    @Test
    public void testSetValues() {
        String events = "change-abandoned change-merged change-restored";
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\","
                + "\"clusterModeEnabled\":true,"
                + "\"filterIn\":\"" + events + "\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        PluginConfig config = new PluginConfig(form);
        assertEquals(6, config.getNumberOfReceivingWorkerThreads());
        assertEquals(4, config.getNumberOfSendingWorkerThreads());
        assertTrue(config.isClusterModeEnabled());
        assertEquals(Arrays.asList(events.split(" ")), config.getFilterIn());
        for (GerritEventType type : GerritEventType.values()) {
            if (events.contains(type.getTypeValue())) {
                assertTrue(type.isInteresting());
            } else {
                assertFalse(type.isInteresting());
            }
        }
    }

    //CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mocks tests.

    /**
     * Test creation of a config object from an existing one.
     * filterIn using empty event list.
     */
    @Test
    public void testCopyConfig() {
        String events = "";
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\","
                + "\"clusterModeEnabled\":true,"
                + "\"filterIn\":\"" + events + "\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        PluginConfig initialConfig = new PluginConfig(form);
        PluginConfig config = new PluginConfig(initialConfig);
        assertEquals(6, config.getNumberOfReceivingWorkerThreads());
        assertEquals(4, config.getNumberOfSendingWorkerThreads());
        assertTrue(config.isClusterModeEnabled());
        assertEquals(Arrays.asList(events.split(" ")), config.getFilterIn());
        for (GerritEventType type : GerritEventType.values()) {
            assertFalse(type.isInteresting());
        }
    }

    /**
     * Test empty filterIn form data which should result in default settings
     * for event filter.
     */
    @Test
    public void testDefaultEventFilter() {
        List<String> defaultEventFilter = PluginConfig.getDefaultEventFilter();
        String events = "null";
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\","
                + "\"filterIn\":\"" + events + "\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        new PluginConfig(form);
        for (GerritEventType type : GerritEventType.values()) {
            if (defaultEventFilter.contains(type.getTypeValue())) {
                assertTrue(type.isInteresting());
            } else {
                assertFalse(type.isInteresting());
            }
        }
    }

    /**
     * Test cluster mode enabled from form data.
     */
    @Test
    public void testClusterModeEnabled() {
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\","
                + "\"clusterModeEnabled\":true}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        PluginConfig config = new PluginConfig(form);
        assertTrue(config.isClusterModeEnabled());
    }

    /**
     * Test cluster mode disabled from form data.
     */
    @Test
    public void testClusterModeDisabled() {
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\","
                + "\"clusterModeEnabled\":false}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        PluginConfig config = new PluginConfig(form);
        assertFalse(config.isClusterModeEnabled());
    }

    /**
     * Test cluster mode defaults to false when not specified in form data.
     */
    @Test
    public void testClusterModeDefaultValue() {
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        PluginConfig config = new PluginConfig(form);
        assertFalse(config.isClusterModeEnabled());
    }

    /**
     * Test cluster mode is properly copied in copy constructor.
     */
    @Test
    public void testClusterModeCopyConstructor() {
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\","
                + "\"clusterModeEnabled\":true}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        PluginConfig initialConfig = new PluginConfig(form);
        PluginConfig copiedConfig = new PluginConfig(initialConfig);
        assertTrue(copiedConfig.isClusterModeEnabled());
    }

    /**
     * Test cluster mode false value is properly copied in copy constructor.
     */
    @Test
    public void testClusterModeCopyConstructorWithDisabled() {
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\","
                + "\"clusterModeEnabled\":false}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        PluginConfig initialConfig = new PluginConfig(form);
        PluginConfig copiedConfig = new PluginConfig(initialConfig);
        assertFalse(copiedConfig.isClusterModeEnabled());
    }

    /**
     * Test cluster mode setter and getter methods directly.
     */
    @Test
    public void testClusterModeSetterGetter() {
        PluginConfig config = new PluginConfig();

        // Default should be false
        assertFalse(config.isClusterModeEnabled());

        // Set to true
        config.setClusterModeEnabled(true);
        assertTrue(config.isClusterModeEnabled());

        // Set back to false
        config.setClusterModeEnabled(false);
        assertFalse(config.isClusterModeEnabled());
    }

    /**
     * Test that default constructor creates config with cluster mode disabled.
     */
    @Test
    public void testDefaultConstructorClusterMode() {
        PluginConfig config = new PluginConfig();
        assertFalse(config.isClusterModeEnabled());

        // Verify other defaults are also set correctly
        assertEquals(PluginConfig.DEFAULT_NR_OF_RECEIVING_WORKER_THREADS,
                     config.getNumberOfReceivingWorkerThreads());
        assertEquals(PluginConfig.DEFAULT_NR_OF_SENDING_WORKER_THREADS,
                     config.getNumberOfSendingWorkerThreads());
    }
}
