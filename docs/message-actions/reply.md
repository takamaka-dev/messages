# Reply

A threaded reply to one parent message. `targets[0]` is the parent's Ed25519
**message signature**. Optional inline media may ride along (`attached_media`).

→ [index](README.md) · [API doc §2/§4.1](../architecture/MESSAGE_ACTIONS_API_1_5_0.md) · [spec](../roadmap/MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md)

## 1. Canonical wire format (fixture-anchored)

Inner decrypted plaintext — reply, text only:

<!-- fixture: V03_reply_text_only/expected_inner_plaintext.json -->
```json
{
  "text_message" : "a reply",
  "action" : "reply",
  "targets" : [ "8otxdmZIe7HY_2ugbRZbAn_lrlxjD_QnCix1w7EpK2DnDaJKgBI98JbGBPR1l7U3jN2ue_dHNLyRUQSFiVRlBg.." ],
  "client_protocol_version" : "1.1"
}
```

Validator outcome:

<!-- fixture: V03_reply_text_only/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ ]
}
```

Reply carrying an inline image (`attached_media[].is_the_object = true`; `preview`
is **standard Base64**, `unencrypted_content_hash` is Base64URL):

<!-- fixture: V04_reply_inline_image/expected_inner_plaintext.json -->
```json
{
  "text_message" : "look",
  "attached_media" : [ {
    "media_type" : "image/png",
    "unencrypted_content_hash" : "uA_0jkC-X8fMX-N2HSBQaro9Zbq912WCdyk1zF9zXsA.",
    "preview" : "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    "is_the_object" : true
  } ],
  "action" : "reply",
  "targets" : [ "8otxdmZIe7HY_2ugbRZbAn_lrlxjD_QnCix1w7EpK2DnDaJKgBI98JbGBPR1l7U3jN2ue_dHNLyRUQSFiVRlBg.." ],
  "client_protocol_version" : "1.1"
}
```

### Edge cases (soft — body still renders)

Two targets (cardinality > 1) → `INVALID_REPLY_MALFORMED_TARGETS` (WARN), fixture `I08_reply_cardinality`:

<!-- fixture: I08_reply_cardinality/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ {
    "code" : "INVALID_REPLY_MALFORMED_TARGETS",
    "severity" : "WARN"
  } ]
}
```

Target not matching the signature regex → `INVALID_REPLY_BAD_SIGNATURE_FORMAT` (WARN), fixture `I09_reply_bad_signature_format`:

<!-- fixture: I09_reply_bad_signature_format/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ {
    "code" : "INVALID_REPLY_BAD_SIGNATURE_FORMAT",
    "severity" : "WARN"
  } ]
}
```

Parent signature not in the receiver's cache → `BROKEN_REFERENCE_REPLY` (INFO), fixture `I11_reply_broken_reference`:

<!-- fixture: I11_reply_broken_reference/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ {
    "code" : "BROKEN_REFERENCE_REPLY",
    "severity" : "INFO"
  } ]
}
```

## 2. Reference

**Sender** — `ChatCryptoUtils.getReplyMessageBean` (`utils/ChatCryptoUtils.java:618`):

```java
getReplyMessageBean(SendContext ctx, String parentSignature, String replyText,
        List<ChatMediaPlaceholderBean> attachedMedia, List<String> citedUsers)
    throws ChatCryptoConstructionException, MalformedTargetException;
```

`parentSignature` must match `^[A-Za-z0-9_-]{86}\.\.$` or construction throws
`MalformedTargetException`.

**Receiver** — `MessageActionValidator` (`chat/message/MessageActionValidator.java`).
Reply is a **soft** action: exactly one well-formed target is required; cardinality,
format, and broken-reference issues are advisory (`WARN`/`INFO`) and the body still
renders. `overallValid` stays `true`.

**Fields:** `targets` = `[parentSignature]`; `text_message` = reply body;
`attached_media` optional. The parent link is the parent's `signature` (also rschat's
`messages.message_signature`), visible only to conversation members.

**Shell CLI** (dash-safe; `ChatCommands.reply`):

```
reply --parentSignature "<parentSig>" --text "<reply text>"
```

The shell `reply` command is text-only; the library helper additionally supports
inline `attachedMedia`/`citedUsers`.
