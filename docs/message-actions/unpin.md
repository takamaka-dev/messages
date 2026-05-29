# Unpin

Clears the conversation's pinned message. The simplest action: no target, no body —
just the action string. Authorization is conversation-creator-only (same caveat as
[pin](pin.md)).

→ [index](README.md) · [pin](pin.md) · [API doc §2](../architecture/MESSAGE_ACTIONS_API_1_5_0.md)

## 1. Canonical wire format (fixture-anchored)

<!-- fixture: V14_unpin/expected_inner_plaintext.json -->
```json
{
  "action" : "unpin",
  "client_protocol_version" : "1.1"
}
```

<!-- fixture: V14_unpin/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ ]
}
```

## 2. Reference

**Sender** — `ChatCryptoUtils.getUnpinMessageBean` (`utils/ChatCryptoUtils.java:725`):

```java
getUnpinMessageBean(SendContext ctx)
    throws ChatCryptoConstructionException;
```

No `targets`, no `text_message` — unpin clears whatever is currently pinned in the
conversation.

**Receiver** — soft action with the `CONVERSATION_CREATOR` check (`UNAUTHORIZED_UNPIN`
WARN for a non-creator). The same shell caveat as [pin](pin.md) applies: the shell
skips creator authorization (`conversationCreatorPk = null`; finding F4).

**Shell CLI** (`ChatCommands.unpin`, no arguments):

```
unpin
```
