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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * Tests for {@link PushNotificationService} and {@link PushToWebUIOp}.
 *
 * @since 2025.1
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("org.nuxeo.ecm.automation.core")
@Deploy("nuxeo.labs.push.nuxeo-labs-push-to-webui-core")
public class TestPushNotificationService {

    protected static final String TEST_USER = "Administrator";

    protected static final String TEST_MESSAGE = "Hello from test";

    @Inject
    protected CoreSession session;

    @Inject
    protected PushNotificationService service;

    @Inject
    protected AutomationService automationService;

    protected SseConnection connection;

    @Before
    public void setUp() {
        var writer = new PrintWriter(new StringWriter());
        connection = new SseConnection(writer);
    }

    @Test
    public void testServiceIsDeployed() {
        assertNotNull(service);
    }

    @Test
    public void testRegisterAndPush() throws InterruptedException {
        service.register(TEST_USER, connection);
        try {
            service.pushToUser(TEST_USER, TEST_MESSAGE);
            var received = connection.waitForMessage(1, TimeUnit.SECONDS);
            assertEquals(TEST_MESSAGE, received);
        } finally {
            service.unregister(TEST_USER, connection);
        }
    }

    @Test
    public void testPushToUnknownUserDoesNothing() {
        // Should not throw
        service.pushToUser("nobody", TEST_MESSAGE);
    }

    @Test
    public void testUnregister() throws InterruptedException {
        service.register(TEST_USER, connection);
        service.unregister(TEST_USER, connection);

        service.pushToUser(TEST_USER, TEST_MESSAGE);
        var received = connection.waitForMessage(100, TimeUnit.MILLISECONDS);
        assertNull(received);
    }

    @Test
    public void testPushOperation() throws OperationException, InterruptedException {
        service.register(TEST_USER, connection);
        try {
            try (var ctx = new OperationContext(session)) {
                var params = new HashMap<String, Object>();
                params.put("message", TEST_MESSAGE);

                automationService.run(ctx, PushToWebUIOp.ID, params);
            }
            var received = connection.waitForMessage(1, TimeUnit.SECONDS);
            assertEquals(TEST_MESSAGE, received);
        } finally {
            service.unregister(TEST_USER, connection);
        }
    }
}
