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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Implementation of {@link PushNotificationService}.
 * <p>
 * Manages SSE connections per user using a {@link ConcurrentHashMap}.
 * Messages are enqueued on each connection's blocking queue; the servlet
 * thread handles writing and keepalives.
 *
 * @since 2025.1
 */
public class PushNotificationServiceImpl extends DefaultComponent implements PushNotificationService {

    private static final Logger log = LogManager.getLogger(PushNotificationServiceImpl.class);

    /**
     * Maps username to the set of active SSE connections for that user.
     */
    private final Map<String, Set<SseConnection>> connections = new ConcurrentHashMap<>();

    @Override
    public void stop(org.nuxeo.runtime.model.ComponentContext context) throws InterruptedException {
        // Close all connections
        connections.forEach((username, conns) -> conns.forEach(SseConnection::close));
        connections.clear();
        log.info("PushNotificationService stopped");
    }

    @Override
    public void register(String username, SseConnection connection) {
        connections.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(connection);
        log.debug("Registered SSE connection for user: {}", username);
    }

    @Override
    public void unregister(String username, SseConnection connection) {
        Set<SseConnection> conns = connections.get(username);
        if (conns != null) {
            conns.remove(connection);
            if (conns.isEmpty()) {
                connections.remove(username, conns);
            }
        }
        log.debug("Unregistered SSE connection for user: {}", username);
    }

    @Override
    public void pushToUser(String username, String message) {
        Set<SseConnection> conns = connections.get(username);
        if (conns == null || conns.isEmpty()) {
            log.debug("No active connections for user: {}, message dropped", username);
            return;
        }
        Iterator<SseConnection> it = conns.iterator();
        while (it.hasNext()) {
            SseConnection conn = it.next();
            if (conn.isClosed()) {
                it.remove();
            } else {
                conn.enqueueMessage(message);
            }
        }
    }
}
