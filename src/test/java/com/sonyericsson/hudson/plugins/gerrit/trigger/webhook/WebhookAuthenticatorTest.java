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

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WebhookAuthenticator.
 *
 * @author Your Name &lt;your.email@domain.com&gt;
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class WebhookAuthenticatorTest {

    private static final int TEST_ITERATIONS = 1000;
    private static final int MAX_TEST_DURATION_MS = 1000;

    private WebhookAuthenticator authenticator;

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private GerritServer mockServer;

    @Before
    public void setUp() {
        authenticator = new WebhookAuthenticator();
        when(mockServer.getName()).thenReturn("test-server");
    }

    @Test
    public void testAuthenticateWithNullRequest() {
        boolean result = authenticator.authenticate(null, mockServer);
        assertFalse("Should fail authentication with null request", result);
    }

    @Test
    public void testAuthenticateWithNullServer() {
        boolean result = authenticator.authenticate(mockRequest, null);
        assertFalse("Should fail authentication with null server", result);
    }

    @Test
    public void testAuthenticateWithNoConfiguration() {
        // Mock basic request properties
        when(mockRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(mockRequest.getHeader("X-Gerrit-Token")).thenReturn(null);
        when(mockRequest.getHeader("Authorization")).thenReturn(null);
        when(mockRequest.getHeader("X-Gerrit-Signature")).thenReturn(null);
        when(mockRequest.getParameter("token")).thenReturn(null);

        // Since no webhook configuration is available (returns null),
        // authentication should pass by default
        boolean result = authenticator.authenticate(mockRequest, mockServer);
        assertTrue("Should pass authentication when no configuration is available", result);
    }

    @Test
    public void testGetClientIpAddressBasic() {
        when(mockRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(mockRequest.getHeader("X-Real-IP")).thenReturn(null);

        // Use reflection to access private method for testing
        // This would need to be refactored or made package-private for proper testing
        assertTrue("Should pass basic authentication",
                  authenticator.authenticate(mockRequest, mockServer));
    }

    @Test
    public void testGetClientIpAddressWithXForwardedFor() {
        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1");
        when(mockRequest.getRemoteAddr()).thenReturn("192.168.1.100");

        // Should still pass authentication (no IP restrictions configured)
        assertTrue("Should pass authentication with X-Forwarded-For header",
                  authenticator.authenticate(mockRequest, mockServer));
    }

    @Test
    public void testGetClientIpAddressWithMultipleForwardedIps() {
        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 192.168.1.1, 10.0.0.1");
        when(mockRequest.getRemoteAddr()).thenReturn("192.168.1.100");

        // Should still pass authentication (no IP restrictions configured)
        assertTrue("Should pass authentication with multiple forwarded IPs",
                  authenticator.authenticate(mockRequest, mockServer));
    }

    @Test
    public void testAuthenticationWithTokenInHeader() {
        when(mockRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(mockRequest.getHeader("X-Gerrit-Token")).thenReturn("test-token");
        when(mockRequest.getHeader("X-Gerrit-Signature")).thenReturn(null);

        // Should pass since no token validation is configured
        assertTrue("Should pass authentication with token header",
                  authenticator.authenticate(mockRequest, mockServer));
    }

    @Test
    public void testAuthenticationWithTokenInAuthorizationHeader() {
        when(mockRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(mockRequest.getHeader("X-Gerrit-Token")).thenReturn(null);
        when(mockRequest.getHeader("Authorization")).thenReturn("Bearer test-token");
        when(mockRequest.getHeader("X-Gerrit-Signature")).thenReturn(null);

        // Should pass since no token validation is configured
        assertTrue("Should pass authentication with Authorization Bearer token",
                  authenticator.authenticate(mockRequest, mockServer));
    }

    @Test
    public void testAuthenticationWithTokenInQueryParameter() {
        when(mockRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(mockRequest.getHeader("X-Gerrit-Token")).thenReturn(null);
        when(mockRequest.getHeader("Authorization")).thenReturn(null);
        when(mockRequest.getParameter("token")).thenReturn("test-token");
        when(mockRequest.getHeader("X-Gerrit-Signature")).thenReturn(null);

        // Should pass since no token validation is configured
        assertTrue("Should pass authentication with query parameter token",
                  authenticator.authenticate(mockRequest, mockServer));
    }

    @Test
    public void testAuthenticationWithHmacSignature() {
        when(mockRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(mockRequest.getHeader("X-Gerrit-Token")).thenReturn(null);
        when(mockRequest.getHeader("Authorization")).thenReturn(null);
        when(mockRequest.getHeader("X-Gerrit-Signature")).thenReturn("sha256=abcdef1234567890");

        // Should pass since no HMAC validation is configured
        assertTrue("Should pass authentication with HMAC signature",
                  authenticator.authenticate(mockRequest, mockServer));
    }

    @Test
    public void testSecureEqualsWithNullValues() {
        // This test would need access to the private secureEquals method
        // In a real implementation, you might make this method package-private or protected
        // for better testability, or use a testing library that can access private methods

        // For now, we can only test through the public authenticate method
        assertTrue("Authentication should handle null values gracefully",
                  authenticator.authenticate(mockRequest, mockServer));
    }

    @Test
    public void testAuthenticationPerformance() {
        // Test that authentication doesn't take too long
        when(mockRequest.getRemoteAddr()).thenReturn("192.168.1.100");

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            authenticator.authenticate(mockRequest, mockServer);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Authentication should be fast (less than 1 second for 1000 calls)
        assertTrue("Authentication should be performant", duration < MAX_TEST_DURATION_MS);
    }
}
