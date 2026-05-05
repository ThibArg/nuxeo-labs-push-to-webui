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

/**
 * Service for pushing notifications to connected Web UI clients via Server-Sent Events.
 * <p>
 * Manages SSE connections per user and provides methods to send messages to all
 * active sessions of a given user.
 *
 * @since 2025.1
 */
public interface PushNotificationService {

    /**
     * Registers an SSE connection for the given user.
     *
     * @param username the Nuxeo username
     * @param connection the SSE connection to register
     */
    void register(String username, SseConnection connection);

    /**
     * Unregisters an SSE connection for the given user.
     *
     * @param username the Nuxeo username
     * @param connection the SSE connection to remove
     */
    void unregister(String username, SseConnection connection);

    /**
     * Sends a message to all active SSE connections of the given user.
     * <p>
     * If the user has no active connections, the message is silently dropped.
     * Failed connections are automatically cleaned up.
     *
     * @param username the Nuxeo username to send to
     * @param message the message text to push
     */
    void pushToUser(String username, String message);
}
