# Redact

Tombstones one of the **sender's own** earlier messages. `targets[0]` is the redacted
message's signature; an optional human reason rides in `text_message`.

→ [index](README.md) · [edit](edit.md) · [API doc §2](../architecture/MESSAGE_ACTIONS_API_1_5_0.md) · [spec](../roadmap/MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md)

## 1. Canonical wire format (fixture-anchored)

Redact with a reason (the reason is the `text_message`):

<!-- fixture: V11_redact_reason/expected_inner_plaintext.json -->
```json
{
  "text_message" : "violates policy",
  "action" : "redact",
  "targets" : [ "8otxdmZIe7HY_2ugbRZbAn_lrlxjD_QnCix1w7EpK2DnDaJKgBI98JbGBPR1l7U3jN2ue_dHNLyRUQSFiVRlBg.." ],
  "client_protocol_version" : "1.1"
}
```

<!-- fixture: V11_redact_reason/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ ]
}
```

Redact with no reason (`text_message` omitted):

<!-- fixture: V12_redact_no_reason/expected_inner_plaintext.json -->
```json
{
  "action" : "redact",
  "targets" : [ "8otxdmZIe7HY_2ugbRZbAn_lrlxjD_QnCix1w7EpK2DnDaJKgBI98JbGBPR1l7U3jN2ue_dHNLyRUQSFiVRlBg.." ],
  "client_protocol_version" : "1.1"
}
```

<!-- fixture: V12_redact_no_reason/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ ]
}
```

## 2. Reference

**Sender** — `ChatCryptoUtils.getRedactMessageBean` (`utils/ChatCryptoUtils.java:692`):

```java
getRedactMessageBean(SendContext ctx, String parentSignature, String optionalReason)
    throws ChatCryptoConstructionException, MalformedTargetException;
```

`optionalReason` may be null/empty (then `text_message` is omitted, per V12).

**Receiver** — soft action with the same `SELF_AUTHOR` authorization check as
[edit](edit.md): a non-author redact surfaces `UNAUTHORIZED_REDACT` (WARN) and still
renders. Redaction is a tombstone *request*; the client decides whether to blank its
local copy.

**Shell CLI** (dash-safe; `ChatCommands.redact`, `--reason` optional):

```
redact --parentSignature "<yourMessageSig>" --reason "<why>"
```
