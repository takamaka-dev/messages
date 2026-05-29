# Reaction remove

Retracts the sender's own earlier reaction to a parent message. Carries no body and
no media — just the action and `targets[0]` = the parent message signature.

→ [index](README.md) · [reaction](reaction.md) · [API doc §2](../architecture/MESSAGE_ACTIONS_API_1_5_0.md)

## 1. Canonical wire format (fixture-anchored)

<!-- fixture: V08_reaction_remove/expected_inner_plaintext.json -->
```json
{
  "action" : "reaction_remove",
  "targets" : [ "8otxdmZIe7HY_2ugbRZbAn_lrlxjD_QnCix1w7EpK2DnDaJKgBI98JbGBPR1l7U3jN2ue_dHNLyRUQSFiVRlBg.." ],
  "client_protocol_version" : "1.1"
}
```

<!-- fixture: V08_reaction_remove/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ ]
}
```

## 2. Reference

**Sender** — `ChatCryptoUtils.getReactionRemoveMessageBean` (`utils/ChatCryptoUtils.java:659`):

```java
getReactionRemoveMessageBean(SendContext ctx, String parentSignature)
    throws ChatCryptoConstructionException, MalformedTargetException;
```

`parentSignature` must match `^[A-Za-z0-9_-]{86}\.\.$`.

**Receiver** — soft action. Authorization (remover should be the original reaction
author) is advisory: a non-author removal surfaces `UNAUTHORIZED_REACTION_REMOVE`
(WARN) and still renders. The client decides whether to apply the removal to its view.

**Fields:** `targets = [parentSignature]`; no `text_message`, no `attached_media`.

**Shell CLI** (dash-safe; `ChatCommands.reactRemove`):

```
react-remove --parentSignature "<parentSig>"
```
