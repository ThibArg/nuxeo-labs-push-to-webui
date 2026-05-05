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

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

/**
 * Automation operation that sends a push notification to a user's Web UI sessions.
 * <p>
 * Can be called from Automation chains or scripts. If {@code username} is not specified,
 * the notification is sent to the current user.
 * <p>
 * Example usage in an Automation chain:
 * <pre>
 * - Push.Notification:
 *     message: "Your import is complete!"
 * </pre>
 *
 * @since 2025.1
 */
@Operation(id = PushNotificationOp.ID, category = "Notification", label = "Push Notification", description = "Sends a push notification to a user's Web UI sessions via SSE.")
public class PushNotificationOp {

    public static final String ID = "Push.Notification";

    @Context
    protected CoreSession session;

    @Param(name = "message", required = true, description = "The message to push to the user")
    protected String message;

    @Param(name = "username", required = false, description = "Target username. Defaults to the current user.")
    protected String username;

    @OperationMethod
    public void run() {
        String targetUser = StringUtils.isNotBlank(username) ? username : session.getPrincipal().getName();
        PushNotificationService service = Framework.getService(PushNotificationService.class);
        service.pushToUser(targetUser, message);
    }
}
