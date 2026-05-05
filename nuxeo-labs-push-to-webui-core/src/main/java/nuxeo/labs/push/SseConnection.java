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

import java.io.PrintWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Represents an SSE connection to a client.
 * <p>
 * Uses a {@link BlockingQueue} to receive messages from the service.
 * The servlet thread blocks on {@link #waitForMessage(long, TimeUnit)} and
 * writes messages/keepalives to the client's {@link PrintWriter}.
 * <p>
 * This approach avoids {@code AsyncContext} which is not supported by
 * Nuxeo's filter chain.
 *
 * @since 2025.1
 */
public class SseConnection {

    private final PrintWriter writer;

    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

    private volatile boolean closed;

    public SseConnection(PrintWriter writer) {
        this.writer = writer;
        this.closed = false;
    }

    /**
     * Enqueues a message to be sent to the client.
     * Called by {@link PushNotificationServiceImpl#pushToUser(String, String)}.
     *
     * @param message the message to send
     */
    public void enqueueMessage(String message) {
        if (!closed) {
            messageQueue.offer(message);
        }
    }

    /**
     * Waits for a message from the queue, with a timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return the message, or {@code null} if the timeout expired
     * @throws InterruptedException if the thread is interrupted
     */
    public String waitForMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageQueue.poll(timeout, unit);
    }

    /**
     * Writes an SSE data event directly to the client.
     *
     * @param message the message to send
     * @return {@code true} if the write succeeded, {@code false} if the client disconnected
     */
    public synchronized boolean sendEvent(String message) {
        if (closed) {
            return false;
        }
        writer.write("data: " + message + "\n\n");
        writer.flush();
        if (writer.checkError()) {
            closed = true;
            return false;
        }
        return true;
    }

    /**
     * Writes an SSE comment (e.g., keepalive) directly to the client.
     *
     * @param comment the comment text
     * @return {@code true} if the write succeeded, {@code false} if the client disconnected
     */
    public synchronized boolean sendComment(String comment) {
        if (closed) {
            return false;
        }
        writer.write(": " + comment + "\n\n");
        writer.flush();
        if (writer.checkError()) {
            closed = true;
            return false;
        }
        return true;
    }

    /**
     * Marks this connection as closed.
     */
    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}
