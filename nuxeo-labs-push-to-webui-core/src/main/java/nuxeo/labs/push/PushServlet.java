/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 *     (Code initially generated with the help of opencode / Claude Opus)
 */
package nuxeo.labs.push;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.api.Framework;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP servlet that provides a Server-Sent Events endpoint for push notifications.
 * <p>
 * Clients connect via {@code GET /nuxeo/push/subscribe} using their existing
 * authentication (cookie or token). The connection is kept open and the server
 * pushes events as they occur.
 * <p>
 * The servlet blocks the request thread for the duration of the SSE connection.
 * This is necessary because Nuxeo's servlet filter chain does not support async
 * servlets ({@code AsyncContext}). This follows the same pattern used by Nuxeo's
 * internal {@code StreamServlet}.
 * <p>
 * Authentication is handled by Nuxeo's standard NuxeoAuthenticationFilter, which
 * is wired to this URL in the deployment-fragment.xml.
 *
 * @since 2025.1
 */
public class PushServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LogManager.getLogger(PushServlet.class);

    /** Keepalive interval in seconds. Also the poll timeout for the blocking queue. */
    private static final long KEEPALIVE_INTERVAL_SECONDS = 30;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var principal = request.getUserPrincipal();
        if (principal == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }
        String username = principal.getName();

        // Set SSE headers
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        // Prevent buffering by proxies
        response.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer = response.getWriter();
        SseConnection connection = new SseConnection(writer);

        PushNotificationService service = Framework.getService(PushNotificationService.class);
        service.register(username, connection);

        // Send initial comment to confirm connection
        if (!connection.sendComment("connected")) {
            log.debug("Failed to send initial SSE comment to user: {}", username);
            service.unregister(username, connection);
            return;
        }

        log.debug("SSE connection established for user: {}", username);

        try {
            // Blocking loop: wait for messages or send keepalives
            while (!connection.isClosed()) {
                String message = connection.waitForMessage(KEEPALIVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
                if (message != null) {
                    // A message was enqueued by the service
                    if (!connection.sendEvent(message)) {
                        log.debug("Client disconnected while sending message to user: {}", username);
                        break;
                    }
                } else {
                    // Timeout — send keepalive to detect disconnected clients
                    if (!connection.sendComment("keepalive")) {
                        log.debug("Client disconnected during keepalive for user: {}", username);
                        break;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("SSE connection interrupted for user: {}", username);
        } finally {
            log.debug("SSE connection closed for user: {}", username);
            service.unregister(username, connection);
            connection.close();
        }
    }
}
