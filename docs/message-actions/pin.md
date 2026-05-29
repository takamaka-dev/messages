# Pin

Marks one message as the conversation's pinned message. `targets[0]` is the pinned
message's signature; an optional note rides in `text_message`. Authorization is
**conversation-creator-only** (see the caveat below).

→ [index](README.md) · [unpin](unpin.md) · [API doc §2](../architecture/MESSAGE_ACTIONS_API_1_5_0.md) · [spec](../roadmap/MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md)

## 1. Canonical wire format (fixture-anchored)

Pin with a note:

<!-- fixture: V13_pin_reason/expected_inner_plaintext.json -->
```json
{
  "text_message" : "pinned note",
  "action" : "pin",
  "targets" : [ "8otxdmZIe7HY_2ugbRZbAn_lrlxjD_QnCix1w7EpK2DnDaJKgBI98JbGBPR1l7U3jN2ue_dHNLyRUQSFiVRlBg.." ],
  "client_protocol_version" : "1.1"
}
```

<!-- fixture: V13_pin_reason/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ ]
}
```

### Edge case — authorization

Pin signed by a non-creator → `UNAUTHORIZED_PIN` (WARN). Fixture `I13_pin_unauthorized`:

<!-- fixture: I13_pin_unauthorized/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ {
    "code" : "UNAUTHORIZED_PIN",
    "severity" : "WARN"
  } ]
}
```

## 2. Reference

**Sender** — `ChatCryptoUtils.getPinMessageBean` (`utils/ChatCryptoUtils.java:708`):

```java
getPinMessageBean(SendContext ctx, String targetMessageSignature, String optionalReason)
    throws ChatCryptoConstructionException, MalformedTargetException;
```

**Receiver** — soft action with a `CONVERSATION_CREATOR` authorization check. The
validator compares the signer against `ValidationContext.conversationCreatorPk`; a
non-creator pin gets `UNAUTHORIZED_PIN` (WARN) and still renders.

> **Caveat (shell):** the shell passes `conversationCreatorPk = null` at the validate
> seam (the creator PK is not resolved client-side there), so pin/unpin authorization
> is **skipped** in the shell — a non-creator pin renders without the decoration. This
> is degraded, not incorrect (no false negatives in the cryptography). See
> `MESSAGE_ACTION_TESTPLAN.md` finding F4. Resolving it needs the creator PK at the
> seam.

**Shell CLI** (dash-safe; `ChatCommands.pin`, note the option is `--targetSignature`):

```
pin --targetSignature "<messageSig>" --reason "<note>"
```
