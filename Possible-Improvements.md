# Possible Improvements

Ideas and strategies for future versions of `nuxeo-labs-push-to-webui`.

## Configurable Keepalive Interval

The keepalive interval is currently hardcoded to 30 seconds in `PushServlet.java`. Some deployment environments (e.g., aggressive reverse proxies, cloud load balancers) may require a shorter interval, while others could benefit from a longer one to reduce overhead.

This could be made configurable via a `nuxeo.conf` property using Nuxeo's `ConfigurationService`:

```
org.nuxeo.labs.push.keepaliveIntervalSeconds=30
```

The servlet would read this at connection time via `Framework.getService(ConfigurationService.class).getString(...)`.

## Maximum Connections Per User

Currently there is no limit on the number of SSE connections per user. If a user has many tabs open, each one opens a separate SSE connection and holds a servlet thread. A configurable maximum (e.g., `org.nuxeo.labs.push.maxConnectionsPerUser=5`) could cap this and reject or close excess connections.

## Structured Message Payload

Currently, messages are plain text strings displayed as toast notifications. A future version could support structured JSON payloads natively in the plugin, allowing the front-end to handle different message types differently — for example:

- Display a toast with a link to a document
- Refresh the current view
- Trigger a navigation
- Show different severity levels (info, warning, error)

> **Note:** This can already be achieved today by sending JSON strings as the message and overriding the `nuxeo-labs-push-listener.html` element in Studio Designer. See the "Advanced: Custom Message Handling" section in the README for details and examples.

A future version could build this into the plugin natively, with a standard JSON schema and built-in handling for common actions, so users don't need to override the element.

## Message History / Missed Messages

If the SSE connection drops and reconnects, any messages sent during the gap are lost. A future version could buffer recent messages per user (e.g., the last N messages or messages from the last M minutes) and replay them on reconnect using the `Last-Event-ID` header that `EventSource` sends automatically.
