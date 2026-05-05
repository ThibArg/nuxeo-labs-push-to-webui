# nuxeo-labs-push-to-webui

## TL;DR

Server-side push notifications to Nuxeo Web UI (LTS 2025) via Server-Sent Events (SSE). Instead of polling, server-side code pushes messages directly to the user's browser, displayed as toast notifications.

> [!NOTE]
> This is Work In Progress - Not ready for use

## Description

A Nuxeo LTS 2025 plugin that enables **server-to-browser push notifications** in [Nuxeo Web UI](https://doc.nuxeo.com/nxdoc/web-ui/) using Server-Sent Events.

The typical use case: a user triggers an action in the browser that starts an asynchronous server-side process (via an event listener, a scheduled job, etc.). When the process completes, the server pushes a notification to the user's browser — no polling required.

The plugin provides:

- A **Java service** (`PushNotificationService`) that manages SSE connections per user
- An **HTTP servlet** that serves as the SSE endpoint (`/nuxeo/push/subscribe`)
- An **Automation operation** (`Event.PushToWebUI`) for use in Automation chains and scripts
- A **Web UI element** (`<nuxeo-labs-push-listener>`) that receives SSE events and displays them as toast notifications

## Architecture

```
Browser (Web UI)                    Nuxeo Server
+----------------------+           +------------------------------+
| <nuxeo-labs-push-    |  SSE conn | PushServlet (HttpServlet)    |
|  listener>           |<----------| GET /nuxeo/push/subscribe    |
|  (EventSource)       |           | text/event-stream            |
|                      |           |                              |
| on message -> toast  |           | PushNotificationService      |
|                      |           |  .pushToUser(user, message)  |
+----------------------+           |                              |
                                   | Event.PushToWebUI (Op)       |
                                   |  calls service from chains   |
                                   +------------------------------+
```

## How It Works

### Connection Flow

1. Web UI loads — `<nuxeo-labs-push-listener>` is instantiated (via DOCUMENT_ACTIONS slot)
2. The element opens an `EventSource` to `/nuxeo/push/subscribe` using existing auth cookies
3. `PushServlet` authenticates via `request.getUserPrincipal()` and starts an async context
4. `PushNotificationServiceImpl` registers the connection under the username
5. Keepalive comments (`: keepalive`) are sent every 30 seconds to prevent proxy timeouts
6. On disconnect, the connection is automatically cleaned up

### Push Flow

1. Server-side code calls `pushToUser("jdoe", "Done!")` (see [Usage](#usage) below)
2. The service finds all SSE connections for the user
3. It sends `data: Done!\n\n` to each connection
4. The browser's `EventSource.onmessage` fires
5. The element dispatches a `notify` CustomEvent — Web UI shows a toast

### Targeting

- Notifications are **per user**: all tabs/sessions of a user receive the message
- The `username` parameter on `Event.PushToWebUI` defaults to the current user
- In async event listeners, `coreSession.getPrincipal().getName()` gives the originating user

## Usage

### From Java (e.g., an async event listener)

```java
import nuxeo.labs.push.PushNotificationService;
import org.nuxeo.runtime.api.Framework;

// In your event listener or any server-side code:
String username = session.getPrincipal().getName();
PushNotificationService pushService = Framework.getService(PushNotificationService.class);
pushService.pushToUser(username, "Your import is complete!");
```

### From an Automation Chain

```yaml
- Event.PushToWebUI:
    message: "Your export is ready!"
```

### From an Automation Chain (targeting a specific user)

```yaml
- Event.PushToWebUI:
    message: "A document was shared with you"
    username: "jdoe"
```

## Use with nuxeo-labs-baf-notification

This plugin works well in combination with [nuxeo-labs-baf-notification](https://github.com/nuxeo-sandbox/nuxeo-labs-baf-notification), which fires a `bulkActionDone` Nuxeo event whenever a Bulk Action Framework (BAF) command completes or aborts.

By installing both plugins, you can react to bulk action completion and push a notification to the user who started the action — no polling required.

> [!NOTE]
> The `bulkActionDone` event fires for **all** bulk actions. Filter by the `action` property if you only want to notify for specific actions.

### Using Nuxeo Studio (no custom Java code)

This is the easiest approach — no Java code required:

1. **Register the event in Studio**: Add `bulkActionDone` to the Studio Registry under "Core Events" so Studio recognizes it as a valid event name.
2. **Create an Event Handler**: In Studio Modeler, create an Event Handler that listens for the `bulkActionDone` event.
3. **Link a JavaScript Automation chain**: JavaScript Automation makes it easy to extract the event context properties and call the `Event.PushToWebUI` operation.

**Example JavaScript Automation chain:**

```javascript
function run(input, params) {
  var eventCtx = ctx.Event.context;
  var username = eventCtx.getProperty("username");
  var action = eventCtx.getProperty("action");
  var state = eventCtx.getProperty("state");
  var processed = eventCtx.getProperty("processed");
  var errorCount = eventCtx.getProperty("errorCount");

  var message;
  if (state === "COMPLETED") {
    if (errorCount > 0) {
      message = "Bulk action '" + action + "' completed: " + processed + " documents processed, " + errorCount + " errors";
    } else {
      message = "Bulk action '" + action + "' completed: " + processed + " documents processed";
    }
  } else {
    message = "Bulk action '" + action + "' was aborted";
  }

  var pushOp = Context.RunOperation(null, {
    "id": "Event.PushToWebUI",
    "parameters": {
      "message": message,
      "username": username
    }
  });

  return input;
}
```

### Using a Java Event Listener

Alternatively, you can write a Java event listener in a custom plugin:

**1. Create the listener:**

```java
package com.example;

import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.runtime.api.Framework;

import nuxeo.labs.push.PushNotificationService;

public class BulkActionPushListener implements EventListener {

    @Override
    public void handleEvent(Event event) {
        var ctx = event.getContext();
        var username = (String) ctx.getProperty("username");
        var action = (String) ctx.getProperty("action");
        var state = (String) ctx.getProperty("state");
        var processed = (long) ctx.getProperty("processed");
        var errorCount = (long) ctx.getProperty("errorCount");

        String message;
        if ("COMPLETED".equals(state)) {
            if (errorCount > 0) {
                message = String.format("Bulk action '%s' completed: %d documents processed, %d errors",
                        action, processed, errorCount);
            } else {
                message = String.format("Bulk action '%s' completed: %d documents processed",
                        action, processed);
            }
        } else {
            message = String.format("Bulk action '%s' was aborted", action);
        }

        Framework.getService(PushNotificationService.class).pushToUser(username, message);
    }
}
```

**2. Register the listener:**

```xml
<?xml version="1.0"?>
<component name="com.example.bulk-action-push-listener">
  <extension target="org.nuxeo.ecm.core.event.EventServiceComponent" point="listener">
    <listener name="bulkActionPushListener"
        class="com.example.BulkActionPushListener">
      <event>bulkActionDone</event>
    </listener>
  </extension>
</component>
```

In both cases, the user who started the bulk action will see a toast notification in their browser as soon as the action completes.

## Server-Side API

### PushNotificationService

| Method | Description |
|---|---|
| `register(username, connection)` | Registers an SSE connection for a user |
| `unregister(username, connection)` | Removes a connection |
| `pushToUser(username, message)` | Sends a message to all active connections of a user |

### Automation Operation: `Event.PushToWebUI`

| | |
|---|---|
| **ID** | `Event.PushToWebUI` |
| **Category** | Notification |
| **Input** | None |
| **Parameters** | `message` (String, required) — the message to push |
| | `username` (String, optional) — target user, defaults to current user |
| **Output** | void |

## How to Build and Deploy

### Build and Deploy Locally

```bash
git clone https://github.com/nuxeo-sandbox/nuxeo-labs-push-to-webui
cd nuxeo-labs-push-to-webui
mvn clean install
```

To skip unit testing, add `-DskipTests`.

The Marketplace package is generated at:

```
nuxeo-labs-push-to-webui-package/target/nuxeo-labs-push-to-webui-package-*.zip
```

Install it via `nuxeoctl`:

```bash
nuxeoctl mp-install nuxeo-labs-push-to-webui-package-2025.1.0-SNAPSHOT.zip
```

### Deploy from Nuxeo Marketplace

This plugin will be available as a package on the [Nuxeo Marketplace](https://connect.nuxeo.com/nuxeo/site/marketplace), you can just:

```bash
nuxeoctl mp-install nuxeo-labs-push-to-webui

```


## Support

**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be useful for the Nuxeo Platform in general, they will be integrated directly into the platform, not maintained here.

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

## About Nuxeo

Nuxeo Platform is an open source highly scalable, cloud-native, enterprise content management product with rich multimedia support, written in Java. Data can be stored in both SQL & NoSQL databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

More information is available at [Hyland/Nuxeo](https://www.hyland.com/en/solutions/products/nuxeo-platform).
