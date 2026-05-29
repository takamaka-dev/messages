# Message Actions API — Messages 1.5.0

**Module:** `io.takamaka.messages:messages` `1.5.0-SNAPSHOT`
**Java:** 17
**Status:** Phase 1 implemented and green (`mvn clean install` → 205 tests pass).
**Wire protocol:** `client_protocol_version = "1.1"`.

This document is the consumer-facing reference for the message-action layer
added in Messages 1.5.0 (reply / reaction / edit / redact / pin / unpin /
forward / share_history). It lists the new method signatures, where each
class lives, and the **JSON actually produced** (extracted verbatim from the
committed cross-platform fixtures) — with the tricky recursive-forward and
share_history shapes called out explicitly.

Downstream consumers (`rsclient`, `rschat`, `shell`, `rsclient-flutter`,
`tkmChat`) build against this surface; they MUST NOT reimplement the
construction or validation logic.

---

## 0. Regression note — old validation tests still compile

Confirmed on 2026-05-28. The pre-existing Messages test suite (109 tests:
`MessagesTest`, `ChatUtilsTest`, `ChatCryptoUtilsSaltTest`,
`ChatMediaPlaceholderBeanTest`, `MentionParserTest`, `MentionRendererTest`,
`AddressValidationTest`) compiles and passes unchanged.

The only test edits required were two call sites that used the now-removed
2-arg `BasicMessageEncryptedContentBean(text, media)` constructor (the bean
grew to 8 fields when the action fields landed). They were switched to the
Lombok builder:

```java
// before (no longer compiles)
new BasicMessageEncryptedContentBean("text", new ArrayList<>())
// after
BasicMessageEncryptedContentBean.builder()
        .textMessage("text").attachedMedia(new ArrayList<>()).build()
```

Total suite after Phase 1: **205 tests, 0 failures**.

---

## 1. New / changed classes and file locations

All paths are relative to `Messages/src/main/java/io/takamaka/messages/`.

### Beans (changed)

| Class | Path | Change |
|---|---|---|
| `BasicMessageEncryptedContentBean` | `chat/message/BasicMessageEncryptedContentBean.java` | 8 fields; `@Builder` + `@JsonInclude(NON_NULL)` + `@JsonIgnoreProperties(ignoreUnknown=true)` |

### Constants / registry (new)

| Class | Path | Purpose |
|---|---|---|
| `MessageAction` | `chat/message/MessageAction.java` | Canonical lowercase action strings + `KNOWN` set + `normalize()` / `isKnown()` |
| `MessageActionMeta` | `chat/message/MessageActionMeta.java` | Per-action `ActionSpec` (cardinality / target format / auth pattern) registry |
| `MessageProtocolVersion` | `chat/message/MessageProtocolVersion.java` | `"1.0"` / `"1.1"`, `CURRENT`, `parse()`, `isAbsent()` |

### Validator + result types (new)

| Class | Path | Purpose |
|---|---|---|
| `MessageActionValidator` | `chat/message/MessageActionValidator.java` | Receiver-side §4.2 pipeline; throws hard, returns soft |
| `ValidationResult` | `chat/message/ValidationResult.java` | `(boolean overallValid, List<Decoration> decorations)` record |
| `Decoration` | `chat/message/Decoration.java` | `(code, humanReadableMessage, severity, affectedField)` record |
| `DecorationSeverity` | `chat/message/DecorationSeverity.java` | `INFO` / `WARN` / `ERROR` string constants |
| `ValidationDecorationCodes` | `chat/message/ValidationDecorationCodes.java` | Closed set of soft-violation codes (`ALL`) |
| `ValidationContext` | `chat/message/ValidationContext.java` | Injected lookups: creator PK, target-author resolver, embedded decryptor |

### Construction helpers + support (new / changed)

| Class | Path | Purpose |
|---|---|---|
| `ChatCryptoUtils` | `utils/ChatCryptoUtils.java` | +10 `get*MessageBean` helpers, +7 private predicates, `buildAndSign` |
| `SendContext` | `utils/SendContext.java` | The 5 recurring send parameters (record) |
| `InlineContentLimits` | `chat/attachment/InlineContentLimits.java` | (pre-existing) inline size/dimension/MIME limits |

### Exceptions (new) — `io.takamaka.messages.exception/`

| Class | Code (`getCode()`) | Tier |
|---|---|---|
| `HardProtocolViolationException` | (base) | hard |
| `MalformedProtocolVersionException` (H1) | `INVALID_PROTOCOL_VERSION_MALFORMED` | hard |
| `IncompatibleMajorVersionException` (H2) | `INVALID_PROTOCOL_VERSION_INCOMPATIBLE_MAJOR` | hard |
| `MissingVersionWithFeaturesException` (H3) | `INVALID_PROTOCOL_VERSION_MISSING_WITH_FEATURES` | hard |
| `LegacyVersionWithFeaturesException` (H4) | `INVALID_PROTOCOL_VERSION_LEGACY_WITH_FEATURES` | hard |
| `UnknownActionException` (H5) | `INVALID_ACTION` | hard |
| `InnerSignatureFailureException` (H6) | `INNER_SIGNATURE_FAILURE` | hard |
| `ChatCryptoConstructionException` | (base; `code` per call) | sender-side |
| `MalformedTargetException` | `MALFORMED_*` / `MISSING_*` | sender-side |
| `ForwardDepthExceededException` | `FORWARD_DEPTH_EXCEEDED` (+`getActualDepth()`/`getMaxDepth()`) | sender-side |
| `InvalidEmbeddedEnvelopeException` | `MISSING_ORIGINAL_ENVELOPE` / `EMBEDDED_INNER_*` / `NESTED_SHARE_HISTORY` | sender-side |
| `InlineContentViolationException` | `REACTION_MIME_NOT_ALLOWED` / `INLINE_*` | sender-side |

---

## 2. Sender API — `ChatCryptoUtils` construction helpers

Every helper takes a `SendContext` first, auto-stamps
`client_protocol_version = "1.1"`, then encrypts + signs via the existing
`getBasicMessageBean` path and returns a fully-formed `BasicMessageRequestBean`
(the signed envelope ready to ship).

```java
// io.takamaka.messages.utils.SendContext
public record SendContext(
    InstanceWalletKeystoreInterface signingWallet,
    int keyIndex,
    String conversationHashName,
    String conversationEncryptionKey,             // base64url AES-256 symmetric key
    Function<String,byte[]> keyDerivationFn)      // nullable = default
// convenience: new SendContext(wallet, index, convHash, convKey)
```

```java
// io.takamaka.messages.utils.ChatCryptoUtils — all return BasicMessageRequestBean

getPlainMessageBean(SendContext ctx, String textMessage,
        List<ChatMediaPlaceholderBean> attachedMedia, List<String> citedUsers)
    throws ChatCryptoConstructionException;

getReplyMessageBean(SendContext ctx, String parentSignature, String replyText,
        List<ChatMediaPlaceholderBean> attachedMedia, List<String> citedUsers)
    throws ChatCryptoConstructionException, MalformedTargetException;

getReactionMessageBean(SendContext ctx, String parentSignature,
        ChatMediaPlaceholderBean reactionPayload, List<String> citedUsers)
    throws ChatCryptoConstructionException, MalformedTargetException, InlineContentViolationException;

getReactionRemoveMessageBean(SendContext ctx, String parentSignature)
    throws ChatCryptoConstructionException, MalformedTargetException;

getEditMessageBean(SendContext ctx, String parentSignature, String newText,
        List<ChatMediaPlaceholderBean> newAttachedMedia, List<String> citedUsers)
    throws ChatCryptoConstructionException, MalformedTargetException;

getRedactMessageBean(SendContext ctx, String parentSignature, String optionalReason)
    throws ChatCryptoConstructionException, MalformedTargetException;

getPinMessageBean(SendContext ctx, String targetMessageSignature, String optionalReason)
    throws ChatCryptoConstructionException, MalformedTargetException;

getUnpinMessageBean(SendContext ctx)
    throws ChatCryptoConstructionException;

getForwardMessageBean(SendContext ctx, BasicMessageEncryptedContentBean beanToForward,
        String forwarderText, String claimedOriginPk)   // claimedOriginPk nullable = anonymous
    throws ChatCryptoConstructionException, ForwardDepthExceededException;

getShareHistoryMessageBean(SendContext ctx, BasicMessageRequestBean originalEnvelope,
        String relayerNote, boolean reShared)
    throws ChatCryptoConstructionException, InvalidEmbeddedEnvelopeException;
```

**Target formats enforced at construction:**

| Target | Regex | Helpers |
|---|---|---|
| Signature | `^[A-Za-z0-9_-]{86}\.\.$` | reply, reaction, reaction_remove, edit, redact, pin |
| Public key | `^[A-Za-z0-9_-]{43}\.$` | forward (claimed origin, optional) |

**`share_history` construction guards:** the embedded `originalEnvelope` must
(a) carry a valid inner Ed25519 signature, (b) belong to the same conversation
as `ctx.conversationHashName`, and (c) not itself be a `share_history`
(nesting rejected). Violations throw `InvalidEmbeddedEnvelopeException` with
the matching code.

---

## 3. Receiver API — `MessageActionValidator`

```java
// io.takamaka.messages.chat.message.MessageActionValidator
public static ValidationResult validate(
        BasicMessageEncryptedContentBean bean,       // already-decrypted inner content
        BasicMessageRequestBean outerEnvelope,       // signed envelope (sender PK, conversation)
        ValidationContext ctx)
    throws HardProtocolViolationException;
```

```java
// io.takamaka.messages.chat.message.ValidationContext
public record ValidationContext(
    String conversationCreatorPk,                                // pin/unpin auth; nullable
    Function<String,String> targetAuthorResolver,                // signature -> author PK, null = not cached
    Function<BasicMessageRequestBean,BasicMessageEncryptedContentBean> embeddedDecryptor) // nullable
// ValidationContext.empty() for plain messages / format-only checks
```

**Outcomes:**

- **Hard (drop the message):** version gate (H1–H4), unknown action (H5),
  share_history embedded inner-signature failure (H6) → a typed
  `HardProtocolViolationException` is thrown; `getCode()` returns the canonical
  H-code.
- **Soft (render the body, attach a decoration):** cardinality / target-format
  / broken-reference / authorization / forward-depth / share_history
  structural / inline-content issues → `ValidationResult` with
  `Decoration` entries. `overallValid` is `false` iff any decoration is
  `ERROR` (cross-conversation share_history, inline hash mismatch); everything
  else is `WARN` or `INFO`.

The full code list lives in `ValidationDecorationCodes.ALL`. The 37 committed
fixtures (`src/test/resources/cross-platform-vectors/`) exercise every hard
case and one representative soft case each; `CrossPlatformVectorTest` is the
cross-platform contract.

---

## 4. JSON produced — inner plaintext (`BasicMessageEncryptedContentBean`)

This is the bean that is canonical-JSON-serialized and AES-256-encrypted into
`encrypted_content`. `@JsonInclude(NON_NULL)` omits unset fields. Field names
(wire keys), in alphabetical (canonical) order:

| Wire key | Java field | Type | Set by |
|---|---|---|---|
| `action` | `action` | string | all actions (absent = plain) |
| `attached_media` | `attachedMedia` | array of `ChatMediaPlaceholderBean` | plain, reply, reaction, edit |
| `client_protocol_version` | `clientProtocolVersion` | string `"1.1"` | every v1.1 helper |
| `fw_content` | `fwContent` | **recursive** `BasicMessageEncryptedContentBean` | forward |
| `original_message` | `originalMessage` | `BasicMessageRequestBean` (full signed envelope) | share_history |
| `re_shared` | `reShared` | boolean (omitted when false/absent) | share_history re-share |
| `targets` | `targets` | array of string | reply/reaction/…/forward |
| `text_message` | `textMessage` | string | plain, reply, edit, redact reason, forwarder note |

### 4.1 Plain (v1.1) and reply

```jsonc
// plain v1.1
{ "text_message": "hello v1.1", "client_protocol_version": "1.1" }

// reply (target = parent message Ed25519 signature, 86 chars + "..")
{
  "text_message": "a reply",
  "action": "reply",
  "targets": [ "8otxdmZIe7HY_2ugbRZbAn_lrlxjD_QnCix1w7EpK2DnDaJKgBI98JbGBPR1l7U3jN2ue_dHNLyRUQSFiVRlBg.." ],
  "client_protocol_version": "1.1"
}
```

### 4.2 Forward — attributed (depth 1)

`targets[0]` is the claimed-origin **public key** (43 chars + `.`); empty
`targets` means an anonymous forward. The forwarded body lives in `fw_content`
(a nested copy of the same bean type — the source signature is NOT preserved).

```jsonc
{
  "text_message": "fyi",
  "action": "forward",
  "targets": [ "9xIsD_XELYretqJPxcXdDD1qIFGe5v-2mOktmIjRaTo." ],
  "fw_content": { "text_message": "forwarded body" },
  "client_protocol_version": "1.1"
}
```

### 4.3 Forward — recursive `fw_content` (depth 3)

A forward of a forward nests `fw_content`. Hard depth cap = **10** (enforced
at construction via `ForwardDepthExceededException`, and at validation via the
`FORWARD_DEPTH_EXCEEDED` decoration which truncates the deepest branch).

```jsonc
{
  "text_message": "fyi",
  "action": "forward",
  "targets": [ ],
  "fw_content": {
    "text_message": "n1",
    "fw_content": {
      "text_message": "n0",
      "fw_content": { "text_message": "leaf" }
    }
  },
  "client_protocol_version": "1.1"
}
```

### 4.4 Share-history — embedded signed `original_message`

Unlike forward (an unverifiable claim), share_history embeds the **original,
still-signed** envelope (`original_message`) so a late receiver can
cryptographically verify it. The embedded `encrypted_content` is the original
AES blob verbatim, decryptable with the same conversation key. `re_shared` is
omitted on a first share and set `true` on a relay-of-a-relay.

```jsonc
{
  "text_message": "sharing history",
  "action": "share_history",
  "original_message": {
    "from": "9xIsD_XELYretqJPxcXdDD1qIFGe5v-2mOktmIjRaTo.",
    "signature": "bPSUbr5g9Plwt_JSFKxPMlTICUisvyDQzY3jloCo7VlQbPMhC-qOFnltCx752ylH2jXRtkKePbYWawcHtWGrCw..",
    "message_type": "TOPIC_MESSAGE",
    "signature_type": "Ed25519BC",
    "basic_message_signed_content_bean": {
      "conversation_hash_name": "a1b2c3d4…abcd",
      "cited_users": null,
      "encrypted_content": {
        "pa": "PBKDF2WithHmacSHA512", "it": 20000,
        "tr": "AES/CBC/PKCS5Padding", "ka": "AES",
        "tv": "v0_1_a", "kl": 256, "ec": "UTF-8",
        "em": [ "…iv…", "…ciphertext…" ]
      }
    }
  },
  "client_protocol_version": "1.1"
}
```

---

## 5. JSON produced — the signed wire envelope (`BasicMessageRequestBean`)

What actually goes over RSocket. The inner plaintext above is encrypted into
`basic_message_signed_content_bean.encrypted_content`; the Ed25519 `signature`
covers the canonical JSON of `basic_message_signed_content_bean`. Example for
the reply fixture:

```jsonc
{
  "from": "9xIsD_XELYretqJPxcXdDD1qIFGe5v-2mOktmIjRaTo.",
  "signature": "odwdwFJWIIQWKWzUx-d2edRSKzLpmOeP2L2UAQxD93bmpg6Wjy_IwvMGkuXXK3P6GBBVFSAyoUdRELNPuJJPBA..",
  "message_type": "TOPIC_MESSAGE",
  "signature_type": "Ed25519BC",
  "basic_message_signed_content_bean": {
    "conversation_hash_name": "a1b2c3d4…abcd",
    "cited_users": null,
    "encrypted_content": {
      "pa": "PBKDF2WithHmacSHA512", "it": 20000, "tr": "AES/CBC/PKCS5Padding",
      "ka": "AES", "tv": "v0_1_a", "kl": 256, "ec": "UTF-8",
      "em": [ "…iv…", "…ciphertext…" ]
    }
  }
}
```

**Server visibility:** the action layer (action, targets, fw_content,
original_message, re_shared, version) lives entirely inside
`encrypted_content`. The server is action-blind — it stores the envelope keyed
by `signature`, and the parent-message link in a reply is the parent's
`signature` (which is also rschat's `messages.message_signature` primary key),
disclosed only to conversation members who hold the key.

---

## 6. Cross-platform notes (for the Dart port — Phase 2)

- **No seeded RNG**, ever (production or test). Parity is verified by
  validation-outcome agreement on the 37 frozen fixtures, NOT by byte-identical
  regeneration — AES-GCM IV / PBKDF2 salt are correctly random per call.
- **Action strings** are lowercase wire values; normalize inbound with
  `trim()` + locale-independent `toLowerCase()` before registry lookup.
- **Encoding asymmetry:** `ChatMediaPlaceholderBean.preview` is **standard
  Base64**; all hash fields (signatures, `unencrypted_content_hash`,
  conversation hashes) are **Base64URL with `.` padding**.
- **Inline-content hash contract (Phase 1 decision):** the inline
  `unencrypted_content_hash` is `SHA3-256(preview-Base64-string)` rendered as
  Base64URL — computed identically by the generator and the validator. The
  Dart port must match this exact input (hash of the Base64 string, not of the
  decoded bytes).
- **Target regexes** are POSIX-portable and reused verbatim: signature
  `^[A-Za-z0-9_-]{86}\.\.$`, public key `^[A-Za-z0-9_-]{43}\.$`.

---

**Source of truth:** the committed fixtures under
`Messages/src/test/resources/cross-platform-vectors/fixtures/` and the Phase 1
plan at `Messages/docs/roadmap/IMPL_PLAN_PHASE_1_MESSAGES_1_5_0.md`.
**Generated:** 2026-05-28 against `messages 1.5.0-SNAPSHOT`.
```
