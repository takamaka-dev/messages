# Edit

Replaces the text (and optionally the media) of one of the **sender's own** earlier
messages. `targets[0]` is the edited message's signature; `text_message` /
`attached_media` carry the new content.

→ [index](README.md) · [redact](redact.md) · [API doc §2](../architecture/MESSAGE_ACTIONS_API_1_5_0.md) · [spec](../roadmap/MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md)

## 1. Canonical wire format (fixture-anchored)

Edit — new text, same media:

<!-- fixture: V09_edit_text/expected_inner_plaintext.json -->
```json
{
  "text_message" : "edited text",
  "action" : "edit",
  "targets" : [ "8otxdmZIe7HY_2ugbRZbAn_lrlxjD_QnCix1w7EpK2DnDaJKgBI98JbGBPR1l7U3jN2ue_dHNLyRUQSFiVRlBg.." ],
  "client_protocol_version" : "1.1"
}
```

<!-- fixture: V09_edit_text/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ ]
}
```

Edit — new text and new media:

<!-- fixture: V10_edit_text_media/expected_inner_plaintext.json -->
```json
{
  "text_message" : "edited",
  "attached_media" : [ {
    "media_type" : "image/png",
    "unencrypted_content_hash" : "cuXbpolVXiW-tfU0L4luPsT5acuZyrJRkd0oHcus4bI.",
    "preview" : "aGVsbG8=",
    "is_the_object" : true
  } ],
  "action" : "edit",
  "targets" : [ "8otxdmZIe7HY_2ugbRZbAn_lrlxjD_QnCix1w7EpK2DnDaJKgBI98JbGBPR1l7U3jN2ue_dHNLyRUQSFiVRlBg.." ],
  "client_protocol_version" : "1.1"
}
```

### Edge case — authorization

Edit signed by someone other than the original author → `UNAUTHORIZED_EDIT` (WARN);
the new content still renders, but a client should not apply a non-author edit.
Fixture `I12_edit_unauthorized`:

<!-- fixture: I12_edit_unauthorized/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ {
    "code" : "UNAUTHORIZED_EDIT",
    "severity" : "WARN"
  } ]
}
```

## 2. Reference

**Sender** — `ChatCryptoUtils.getEditMessageBean` (`utils/ChatCryptoUtils.java:674`):

```java
getEditMessageBean(SendContext ctx, String parentSignature, String newText,
        List<ChatMediaPlaceholderBean> newAttachedMedia, List<String> citedUsers)
    throws ChatCryptoConstructionException, MalformedTargetException;
```

**Receiver** — soft action with a `SELF_AUTHOR` authorization check: the validator
resolves the target's author via `ValidationContext.targetAuthorResolver` and, if the
editor ≠ original author, attaches `UNAUTHORIZED_EDIT` (WARN). If the target is not in
the receiver's cache the author cannot be resolved and the check is skipped — so cache
the parent (e.g. retrieve history) before validating to get the decoration
deterministically.

**Shell CLI** (dash-safe; `ChatCommands.edit`):

```
edit --parentSignature "<yourMessageSig>" --newText "<new text>"
```
