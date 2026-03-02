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

import hudson.Extension;
import hudson.security.csrf.CrumbExclusion;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exempts Gerrit webhook requests from CSRF protection.
 * External Gerrit servers cannot provide Jenkins crumb tokens,
 * so we exempt the webhook endpoint from CSRF checking.
 * Security is enforced via WebhookAuthenticator (tokens, HMAC, IP filtering).
 */
@Extension
public class WebhookCrumbExclusion extends CrumbExclusion {

    private static final Logger LOGGER = Logger.getLogger(WebhookCrumbExclusion.class.getName());
    private static final String EXCLUSION_PATH = "/" + WebhookEventReceiver.URL_NAME;

    /**
     * Processes the request to determine if it should be excluded from CSRF protection.
     * If the request path matches the webhook endpoint, it is excluded.
     *
     * @param req   the HTTP request
     * @param resp  the HTTP response
     * @param chain the filter chain
     * @return true if the request is excluded, false otherwise
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        String requestURI = req.getRequestURI();
        String pathInfo = req.getPathInfo();
        String contextPath = req.getContextPath();
        String servletPath = req.getServletPath();

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "WebhookCrumbExclusion.process() - URI: {0}, pathInfo: {1}, "
                            + "contextPath: {2}, servletPath: {3}, EXCLUSION_PATH: {4}",
                    new Object[]{requestURI, pathInfo, contextPath, servletPath, EXCLUSION_PATH});
        }
        // Check if this request is for our webhook endpoint
        // Use contains() to be more forgiving than equals()
        boolean isWebhookPath = (pathInfo != null && pathInfo.contains(EXCLUSION_PATH))
                             || (requestURI != null && requestURI.contains(EXCLUSION_PATH));

        if (isWebhookPath) {
            LOGGER.log(Level.FINE, "Webhook path matched! Calling chain.doFilter() to continue processing");
            chain.doFilter(req, resp);
            LOGGER.log(Level.FINE, "chain.doFilter() completed, returning true");
            return true;
        }

        LOGGER.log(Level.FINE, "Not a webhook path, returning false");
        return false;
    }
}
