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
 */
package nuxeo.labs.push;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.api.Framework;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
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
 * The servlet uses async mode to avoid blocking a thread per connection.
 * Authentication is handled by Nuxeo's standard authentication filter, which is
 * wired to this URL in the deployment-fragment.xml.
 *
 * @since 2025.1
 */
public class PushServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LogManager.getLogger(PushServlet.class);

    /** Async timeout: 0 means no timeout (connection stays open until client disconnects). */
    private static final long ASYNC_TIMEOUT = 0;

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

        // Start async context
        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(ASYNC_TIMEOUT);

        SseConnection connection;
        try {
            connection = new SseConnection(asyncContext);
        } catch (IOException e) {
            log.error("Failed to create SSE connection for user: {}", username, e);
            return;
        }

        PushNotificationService service = Framework.getService(PushNotificationService.class);
        service.register(username, connection);

        // Send initial comment to confirm connection
        try {
            connection.sendComment("connected");
        } catch (IOException e) {
            log.debug("Failed to send initial SSE comment to user: {}", username);
            service.unregister(username, connection);
            return;
        }

        log.debug("SSE connection established for user: {}", username);

        // Register cleanup listener
        asyncContext.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                cleanup();
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                cleanup();
            }

            @Override
            public void onError(AsyncEvent event) {
                cleanup();
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                // no-op
            }

            private void cleanup() {
                log.debug("SSE connection closed for user: {}", username);
                service.unregister(username, connection);
                connection.close();
            }
        });
    }
}
