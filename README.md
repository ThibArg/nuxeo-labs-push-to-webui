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
- An **Automation operation** (`Push.Notification`) for use in Automation chains and scripts
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
                                   | Push.Notification (Op)       |
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
- The `username` parameter on `Push.Notification` defaults to the current user
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
- Push.Notification:
    message: "Your export is ready!"
```

### From an Automation Chain (targeting a specific user)

```yaml
- Push.Notification:
    message: "A document was shared with you"
    username: "jdoe"
```

## Server-Side API

### PushNotificationService

| Method | Description |
|---|---|
| `register(username, connection)` | Registers an SSE connection for a user |
| `unregister(username, connection)` | Removes a connection |
| `pushToUser(username, message)` | Sends a message to all active connections of a user |

### Automation Operation: `Push.Notification`

| | |
|---|---|
| **ID** | `Push.Notification` |
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
nuxeoctl mp-install 

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
