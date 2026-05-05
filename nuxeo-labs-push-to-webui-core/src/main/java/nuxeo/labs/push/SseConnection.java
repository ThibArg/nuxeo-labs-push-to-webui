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

import jakarta.servlet.AsyncContext;

/**
 * Wraps a servlet {@link AsyncContext} as an SSE connection.
 * <p>
 * Provides methods to send SSE-formatted data and check connection status.
 *
 * @since 2025.1
 */
public class SseConnection {

    private final AsyncContext asyncContext;

    private final PrintWriter writer;

    private volatile boolean closed;

    public SseConnection(AsyncContext asyncContext) throws IOException {
        this.asyncContext = asyncContext;
        this.writer = asyncContext.getResponse().getWriter();
        this.closed = false;
    }

    /**
     * Sends an SSE data event.
     *
     * @param message the message to send
     * @throws IOException if the connection is closed or writing fails
     */
    public synchronized void sendEvent(String message) throws IOException {
        if (closed) {
            throw new IOException("Connection is closed");
        }
        writer.write("data: " + message + "\n\n");
        writer.flush();
        if (writer.checkError()) {
            closed = true;
            throw new IOException("Client disconnected");
        }
    }

    /**
     * Sends an SSE comment (keepalive).
     *
     * @throws IOException if writing fails
     */
    public synchronized void sendComment(String comment) throws IOException {
        if (closed) {
            throw new IOException("Connection is closed");
        }
        writer.write(": " + comment + "\n\n");
        writer.flush();
        if (writer.checkError()) {
            closed = true;
            throw new IOException("Client disconnected");
        }
    }

    /**
     * Closes this SSE connection.
     */
    public synchronized void close() {
        if (!closed) {
            closed = true;
            try {
                asyncContext.complete();
            } catch (IllegalStateException e) {
                // already completed
            }
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
