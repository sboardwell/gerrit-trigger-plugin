/*
 *  The MIT License
 *
 *  Copyright (c) 2025, CloudBees, Inc.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.webhook;

import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WebhookEventProcessor.
 *
 * @author Your Name &lt;your.email@domain.com&gt;
 */
@RunWith(MockitoJUnitRunner.class)
public class WebhookEventProcessorTest {

    private static final int GERRIT_SSH_PORT = 29418;

    private WebhookEventProcessor processor;

    @Mock
    private GerritServer mockServer;

    @Mock
    private IGerritHudsonTriggerConfig mockConfig;

    @Before
    public void setUp() {
        processor = new WebhookEventProcessor();
        when(mockServer.getName()).thenReturn("test-server");
        when(mockServer.getConfig()).thenReturn(mockConfig);
        when(mockConfig.getGerritHostName()).thenReturn("gerrit.example.com");
        when(mockConfig.getGerritSshPort()).thenReturn(GERRIT_SSH_PORT);
        when(mockConfig.getGerritFrontEndUrl()).thenReturn("https://gerrit.example.com/");
        when(mockServer.getGerritVersion()).thenReturn("3.4.1");
    }

    @Test
    public void testProcessValidPatchsetCreatedEvent() {
        String payload = "{\n"
                + "  \"type\": \"patchset-created\",\n"
                + "  \"eventCreatedOn\": 1609459200,\n"
                + "  \"change\": {\n"
                + "    \"project\": \"test-project\",\n"
                + "    \"branch\": \"master\",\n"
                + "    \"id\": \"I1234567890abcdef\",\n"
                + "    \"number\": \"12345\",\n"
                + "    \"subject\": \"Test change\",\n"
                + "    \"owner\": {\n"
                + "      \"name\": \"Test User\",\n"
                + "      \"email\": \"test@example.com\"\n"
                + "    }\n"
                + "  },\n"
                + "  \"patchSet\": {\n"
                + "    \"number\": \"1\",\n"
                + "    \"revision\": \"abcdef1234567890\",\n"
                + "    \"ref\": \"refs/changes/45/12345/1\"\n"
                + "  }\n"
                + "}";

        GerritEvent result = processor.processWebhookPayload(payload, mockServer);

        assertNotNull("Should process valid patchset-created event", result);
        assertTrue("Should return PatchsetCreated instance", result instanceof PatchsetCreated);

        PatchsetCreated event = (PatchsetCreated)result;
        assertNotNull("Change should not be null", event.getChange());
        assertEquals("Project should match", "test-project", event.getChange().getProject());
        assertEquals("Branch should match", "master", event.getChange().getBranch());
        assertEquals("Subject should match", "Test change", event.getChange().getSubject());

        assertNotNull("PatchSet should not be null", event.getPatchSet());
        assertEquals("PatchSet number should match", "1", event.getPatchSet().getNumber());
        assertEquals("PatchSet revision should match", "abcdef1234567890", event.getPatchSet().getRevision());
    }

    @Test
    public void testProcessValidCommentAddedEvent() {
        String payload = "{\n"
                + "  \"type\": \"comment-added\",\n"
                + "  \"eventCreatedOn\": 1609459200,\n"
                + "  \"change\": {\n"
                + "    \"project\": \"test-project\",\n"
                + "    \"branch\": \"master\",\n"
                + "    \"id\": \"I1234567890abcdef\",\n"
                + "    \"number\": \"12345\",\n"
                + "    \"subject\": \"Test change\"\n"
                + "  },\n"
                + "  \"patchSet\": {\n"
                + "    \"number\": \"1\",\n"
                + "    \"revision\": \"abcdef1234567890\"\n"
                + "  },\n"
                + "  \"author\": {\n"
                + "    \"name\": \"Reviewer\",\n"
                + "    \"email\": \"reviewer@example.com\"\n"
                + "  },\n"
                + "  \"comment\": \"Looks good to me!\"\n"
                + "}";

        GerritEvent result = processor.processWebhookPayload(payload, mockServer);

        assertNotNull("Should process valid comment-added event", result);
        assertTrue("Should return CommentAdded instance", result instanceof CommentAdded);

        CommentAdded event = (CommentAdded)result;
        assertEquals("Comment should match", "Looks good to me!", event.getComment());
        assertNotNull("Author should not be null", event.getAccount());
        assertEquals("Author name should match", "Reviewer", event.getAccount().getName());
    }

    @Test
    public void testProcessValidChangeMergedEvent() {
        String payload = "{\n"
                + "  \"type\": \"change-merged\",\n"
                + "  \"change\": {\n"
                + "    \"project\": \"test-project\",\n"
                + "    \"branch\": \"master\",\n"
                + "    \"id\": \"I1234567890abcdef\"\n"
                + "  },\n"
                + "  \"patchSet\": {\n"
                + "    \"number\": \"1\",\n"
                + "    \"revision\": \"abcdef1234567890\"\n"
                + "  },\n"
                + "  \"submitter\": {\n"
                + "    \"name\": \"Submitter\",\n"
                + "    \"email\": \"submitter@example.com\"\n"
                + "  }\n"
                + "}";

        GerritEvent result = processor.processWebhookPayload(payload, mockServer);

        assertNotNull("Should process valid change-merged event", result);
        assertTrue("Should return ChangeMerged instance", result instanceof ChangeMerged);

        ChangeMerged event = (ChangeMerged)result;
        assertNotNull("Submitter should not be null", event.getAccount());
        assertEquals("Submitter name should match", "Submitter", event.getAccount().getName());
    }

    @Test
    public void testProcessEmptyPayload() {
        GerritEvent result = processor.processWebhookPayload("", mockServer);
        assertNull("Should return null for empty payload", result);
    }

    @Test
    public void testProcessNullPayload() {
        GerritEvent result = processor.processWebhookPayload(null, mockServer);
        assertNull("Should return null for null payload", result);
    }

    @Test
    public void testProcessInvalidJson() {
        String payload = "{ invalid json }";

        GerritEvent result = processor.processWebhookPayload(payload, mockServer);
        assertNull("Should return null for invalid JSON", result);
    }

    @Test
    public void testProcessEventWithoutType() {
        String payload = "{\n"
                + "  \"change\": {\n"
                + "    \"project\": \"test-project\"\n"
                + "  }\n"
                + "}";

        GerritEvent result = processor.processWebhookPayload(payload, mockServer);
        assertNull("Should return null for event without type", result);
    }

    @Test
    public void testProcessUnsupportedEventType() {
        String payload = "{\n"
                + "  \"type\": \"unsupported-event-type\",\n"
                + "  \"change\": {\n"
                + "    \"project\": \"test-project\"\n"
                + "  }\n"
                + "}";

        GerritEvent result = processor.processWebhookPayload(payload, mockServer);
        assertNull("Should return null for unsupported event type", result);
    }

    @Test
    public void testProcessEventWithMinimalData() {
        String payload = "{\n"
                + "  \"type\": \"patchset-created\"\n"
                + "}";

        GerritEvent result = processor.processWebhookPayload(payload, mockServer);

        assertNotNull("Should process event with minimal data", result);
        assertTrue("Should return PatchsetCreated instance", result instanceof PatchsetCreated);

        PatchsetCreated event = (PatchsetCreated)result;
        // Should not crash with missing data
        assertNull("Change should be null for minimal data", event.getChange());
        assertNull("PatchSet should be null for minimal data", event.getPatchSet());
    }
}
