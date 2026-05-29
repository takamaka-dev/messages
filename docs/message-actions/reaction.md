# Reaction

An inline reaction (emoji / sticker / small image) attached to a parent message.
The reaction payload rides as a single inline `attached_media` entry
(`is_the_object = true`); `targets[0]` is the parent's message signature.

→ [index](README.md) · [API doc §2](../architecture/MESSAGE_ACTIONS_API_1_5_0.md) · [spec](../roadmap/MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md)

## 1. Canonical wire format (fixture-anchored)

Emoji reaction (`preview` is **standard Base64** of the payload bytes):

<!-- fixture: V05_reaction_emoji/expected_inner_plaintext.json -->
```json
{
  "attached_media" : [ {
    "media_type" : "image/png",
    "unencrypted_content_hash" : "cuXbpolVXiW-tfU0L4luPsT5acuZyrJRkd0oHcus4bI.",
    "preview" : "aGVsbG8=",
    "is_the_object" : true
  } ],
  "action" : "reaction",
  "targets" : [ "8otxdmZIe7HY_2ugbRZbAn_lrlxjD_QnCix1w7EpK2DnDaJKgBI98JbGBPR1l7U3jN2ue_dHNLyRUQSFiVRlBg.." ],
  "client_protocol_version" : "1.1"
}
```

<!-- fixture: V05_reaction_emoji/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ ]
}
```

Sticker (`image/webp`):

<!-- fixture: V06_reaction_sticker/expected_inner_plaintext.json -->
```json
{
  "attached_media" : [ {
    "media_type" : "image/webp",
    "unencrypted_content_hash" : "ruAMiDKCb8sqvN6BoLc4_B2tawM3SNv-yEMdEbspikI.",
    "preview" : "c3RpY2tlcg==",
    "is_the_object" : true
  } ],
  "action" : "reaction",
  "targets" : [ "8otxdmZIe7HY_2ugbRZbAn_lrlxjD_QnCix1w7EpK2DnDaJKgBI98JbGBPR1l7U3jN2ue_dHNLyRUQSFiVRlBg.." ],
  "client_protocol_version" : "1.1"
}
```

Inline image (real PNG bytes in `preview`):

<!-- fixture: V07_reaction_inline_image/expected_inner_plaintext.json -->
```json
{
  "attached_media" : [ {
    "media_type" : "image/png",
    "unencrypted_content_hash" : "uA_0jkC-X8fMX-N2HSBQaro9Zbq912WCdyk1zF9zXsA.",
    "preview" : "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    "is_the_object" : true
  } ],
  "action" : "reaction",
  "targets" : [ "8otxdmZIe7HY_2ugbRZbAn_lrlxjD_QnCix1w7EpK2DnDaJKgBI98JbGBPR1l7U3jN2ue_dHNLyRUQSFiVRlBg.." ],
  "client_protocol_version" : "1.1"
}
```

### Edge cases

Disallowed MIME (e.g. `image/tiff`) → `INLINE_MIME_VIOLATION` (WARN), fixture `I17_reaction_mime_violation`:

<!-- fixture: I17_reaction_mime_violation/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ {
    "code" : "INLINE_MIME_VIOLATION",
    "severity" : "WARN"
  } ]
}
```

`unencrypted_content_hash` not matching the preview → `INLINE_HASH_MISMATCH` (**ERROR**, `overallValid=false`), fixture `I18_inline_hash_mismatch`:

<!-- fixture: I18_inline_hash_mismatch/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : false,
  "decorations" : [ {
    "code" : "INLINE_HASH_MISMATCH",
    "severity" : "ERROR"
  } ]
}
```

## 2. Reference

**Sender** — `ChatCryptoUtils.getReactionMessageBean` (`utils/ChatCryptoUtils.java:636`):

```java
getReactionMessageBean(SendContext ctx, String parentSignature,
        ChatMediaPlaceholderBean reactionPayload, List<String> citedUsers)
    throws ChatCryptoConstructionException, MalformedTargetException, InlineContentViolationException;
```

Construction enforces the inline-content limits (`chat/attachment/InlineContentLimits.java`):
allowed image MIME family, decoded size `≤ MAX_INLINE_BYTES = 51200` (50 KiB), and
the `unencrypted_content_hash` contract. Violations throw
`InlineContentViolationException` at compose.

**Inline-hash contract:** `unencrypted_content_hash = Base64URL( SHA3-256( preview-Base64-string ) )`
— hashed over the **Base64 string**, not the decoded bytes (must match in the Dart port).

**Receiver** — soft action. MIME family is a `WARN` decoration; the
hash-mismatch is the only `ERROR` (sets `overallValid=false`) — the body still renders.

**Encoding:** `preview` is **standard Base64**; `unencrypted_content_hash` is Base64URL.

**Shell CLI** (dash-safe; `ChatCommands.react`):

```
react --parentSignature "<parentSig>" --mime image/png --previewBase64 "<standard-base64>"
```

Remove a reaction with [reaction-remove](reaction-remove.md).
