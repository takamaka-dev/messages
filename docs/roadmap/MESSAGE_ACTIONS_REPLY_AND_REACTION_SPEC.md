# Message Actions — Reply & Reaction Spec

**Status:** Draft v2 — open questions resolved (2026-05-27), inline appendix and action extensions added
**Date:** 2026-05-27
**Target Messages version:** `1.5.0-SNAPSHOT`
**Author of original proposal:** Giovanni Antino (`tech@takamaka.io`)
**Editor of this formalization:** Claude

This document formalizes the addition of `action` + `targets` to `BasicMessageEncryptedContentBean` and specifies the two initial actions, **reply** and **reaction**. It also flags design issues that should be resolved before implementation.

---

## 1. Background

The Takamaka chat protocol's encrypted message body — `BasicMessageEncryptedContentBean` — historically carried only `text_message` and `attached_media`. Replies and reactions were not part of the wire protocol. As established in two prior findings logged in `/home/h2tcoin.com/giovanni.antino/NetBeansProjects/SHELL_BOOTSTRAP.md` (2026-05-27):

- The Java side has stranded reply fields in `shell/.../Message.java` (UI-only, not wired).
- The Flutter side has a fully built reply UX in `tkmChat` whose `replyToSignature` is dropped at `message_service.dart:255-258` because the bean has no field for it.

The user has now modified `BasicMessageEncryptedContentBean.java` to add two new fields:

```java
@JsonProperty("action")  private String action;
@JsonProperty("targets") private List<String> targets;
```

The change is in the working tree, not yet committed. This document specifies the semantics, validation, and rollout that should accompany it.

**Placement rationale.** The fields sit inside the AES-256-GCM-encrypted body. The server cannot read either of them. This is the correct placement on principle — repeating the `cited_users` mistake (plaintext metadata exposing the social graph) would have been wrong. The server cannot tell a reply from a normal message, nor see what is being reacted to.

---

## 2. Wire format

### 2.1 The bean (current state after edit)

```java
public class BasicMessageEncryptedContentBean {
    @JsonProperty("text_message")    private String textMessage;
    @JsonProperty("attached_media")  private List<ChatMediaPlaceholderBean> attachedMedia;
    @JsonProperty("action")          private String action;        // NEW
    @JsonProperty("targets")         private List<String> targets; // NEW
}
```

### 2.2 Recommended annotations (not yet applied)

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BasicMessageEncryptedContentBean { ... }
```

- `@JsonInclude(NON_NULL)` — omits `action` / `targets` from JSON when null, so existing simple messages serialize identically and stay compact.
- `@JsonIgnoreProperties(ignoreUnknown = true)` — defensive: makes the bean robust if a future client adds a third action attribute. (Already covered globally by `ChatUtils.java:185` and `SimpleRequestHelper.java:169`, but adding it at the class level removes that dependency.)

### 2.3 Dart mirror (required, not yet applied)

```dart
@JsonSerializable(explicitToJson: true, includeIfNull: false)
class BasicMessageEncryptedContentBean {
  @JsonKey(name: 'text_message')    final String textMessage;
  @JsonKey(name: 'attached_media')  final List<ChatMediaPlaceholderBean>? attachedMedia;
  @JsonKey(name: 'action')          final String? action;     // NEW
  @JsonKey(name: 'targets')         final List<String>? targets; // NEW
  ...
}
```
Must be regenerated with `flutter pub run build_runner build --delete-conflicting-outputs`.

### 2.4 JSON examples

**Plain message (unchanged):**
```json
{ "text_message": "hi" }
```

**Reply:**
```json
{
  "text_message": "I disagree.",
  "action": "reply",
  "targets": ["v7DVzfG5U4Au5CIbGPNDZyV3qtKbEW08OWp1aeZxQb5pEUrHlnJ3FrOLWrAiwMJFRcaC3BN7ucq6MhhH34xrAg.."]
}
```

**Reaction (emoji):**
```json
{
  "text_message": "",
  "action": "reaction",
  "targets": ["v7DVzfG5U4Au5CIbGPNDZyV3qtKbEW08OWp1aeZxQb5pEUrHlnJ3FrOLWrAiwMJFRcaC3BN7ucq6MhhH34xrAg.."],
  "attached_media": [{
    "media_type": "image/png",
    "is_the_object": true,
    "emoji": "👍",
    ...
  }]
}
```

---

## 3. Action registry

`action` is a **closed-vocabulary string**. Clients MUST NOT invent values. Unknown values trigger graceful-degradation behaviour (§6.3).

| Value | Cardinality of `targets` | Required content | Notes |
|---|---|---|---|
| `null` / absent | n/a | n/a | Plain message. Indistinguishable from pre-1.5 behaviour. |
| `"reply"` | exactly 1 | `text_message` non-empty (typical); `attached_media` optional | Quotes a single parent message. |
| `"reaction"` | exactly 1 (see §3.1) | one of: inline image / `sticker_id` / `emoji` | `text_message` SHOULD be empty. |

### 3.1 Reaction targets cardinality — design decision

The original proposal says "accepts a LIST of message signatures as a target". I recommend pinning **cardinality = 1** for reactions in v1.5, because:

- One reaction per message is the universal UX convention (Signal, Telegram, Matrix, iMessage all do this).
- Batched multi-target reactions create asymmetric semantics ("did Alice react to all three, or just collectively?"), and the local UI has to fan them out anyway.
- Keeping `targets` as `List<String>` already preserves forward compatibility — if multi-target reactions are needed later, we relax the cardinality rule without a wire change.

**Recommended rule for v1.5:** `targets.size() == 1` for both `reply` and `reaction`. Clients receiving messages with `targets.size() > 1` and a known action should treat them as malformed and render the broken-reference fallback.

If you disagree and want batch reactions from day one, change this rule and adjust §6.2 validation; the wire format already supports it.

---

## 4. Validation — receiver side

Validation runs **after** decryption and signature verification of the outer envelope. The signed envelope guarantees authenticity of the entire content (including `action` and `targets`).

### 4.1 Signature format regex

Ed25519 signatures encoded by `TkmSignUtils` use Base64URL with `.` as the padding character (per `CLAUDE.md` — *"Base64URL encoding (URL-safe, `.` as padding)"*). A 64-byte Ed25519 signature serializes to exactly 86 base64url chars + 2 padding dots = 88 total.

**Strict regex (recommended):**
```regex
^[A-Za-z0-9_-]{86}\.\.$
```

**Permissive regex (if QTESLA support ever arrives):**
```regex
^[A-Za-z0-9_-]+\.{0,2}$
```

Today the rschat schema column `messages.message_signature VARCHAR(88)` only accommodates Ed25519-length signatures, so the strict form is sufficient. If post-quantum messages are stored later, the column must widen first.

**Test vector** (from the original proposal):
- Signature: `v7DVzfG5U4Au5CIbGPNDZyV3qtKbEW08OWp1aeZxQb5pEUrHlnJ3FrOLWrAiwMJFRcaC3BN7ucq6MhhH34xrAg..`
- 86 base64url chars + `..` padding = 88 total. Matches the strict regex.

### 4.2 Validation pipeline

For every received message with non-null `action`:

1. **Action recognized?** If `action` is not in `{"reply", "reaction"}`, fall back to plain-message rendering and ignore `targets`. **Do not discard the message.**
2. **`targets` present and `size == 1`?** If absent, empty, or oversized, render "INVALID `<action>` reference — malformed targets" decoration and continue with normal body.
3. **Each target matches signature regex?** If not, "INVALID `<action>` reference — bad signature format" decoration.
4. **Each target belongs to the conversation the message was sent in?** Lookup against the client's local message cache for this `conversation_hash_name`. If the parent is unknown:
   - **Reply:** decorate "INVALID reply reference to `<target>`" or, if user prefers, "reply to (message not in cache)" — softer wording for known offline scenarios. Continue rendering the message.
   - **Reaction:** decorate "reaction targeting unknown message `<target>`". Optionally skip rendering entirely depending on client UX.
5. **Action-specific checks** — see §4.3 and §4.4.

The parent message is identified by its **outer envelope Ed25519 signature** — i.e. the same value used as the rschat `messages.message_signature` primary key, and as the `signature` field of `SignedMessageBean`.

### 4.3 Reply-specific checks

- `text_message` SHOULD be non-empty (the reply text). Empty replies are valid but unusual.
- `attached_media` MAY be present.
- Self-replies allowed. Cross-conversation replies disallowed by step 4.

### 4.4 Reaction-specific checks

Exactly one of the following must hold on the reaction message:

| Variant | Conditions |
|---|---|
| **Emoji** | `attached_media[0].emoji` set, `attached_media[0].sticker_id` null, `is_the_object == true` not required (emoji is the payload). `text_message` empty. |
| **Sticker** | `attached_media[0].sticker_id` set (+ optional `pack_hash`), `emoji` null. `text_message` empty. |
| **Inline image** | exactly one element in `attached_media`, `is_the_object == true`, `media_type` starts with `image/`, `preview` populated with the full base64 content, `original_size <= INLINE_MEDIA_MAX_BYTES`, image decodable to ≤ 256×256 pixels. `encrypted_file_hash` and `sed` MUST be null. `text_message` empty. |

`INLINE_MEDIA_MAX_BYTES` — referenced by the existing inline-content policy ("≤256×256, ≤50 KB"). **Open issue:** I could not locate a Java constant enforcing this 50 KB cap. Before this spec ships, either:
- introduce `ChatMediaPlaceholderBean.INLINE_MEDIA_MAX_BYTES = 51200L`, or
- add it to `rschat` validation config so the constraint is enforceable client-side and noted server-side (server cannot enforce since the payload is encrypted — but documenting it keeps clients consistent).

Allowed reaction MIME subtypes (when using inline image): `image/png`, `image/jpeg`, `image/webp`, `image/gif`. Other types fall back to broken-reference decoration.

---

## 5. Rendering guidelines (informative, not normative)

### 5.1 Shell (TKSH)

**Valid reply:**
```
> valid reply to <conversation_hash_short>, <signer_short>:
>   <parent text preview, truncated to 80 chars>
<reply text>
```

**Invalid reply (target unknown or signature malformed):**
```
> INVALID reply reference to <target_signature_short>
<reply text>
```

**Reaction:** as a single line preceding any message-listing output:
```
<sender_short> reacted to <target_signature_short> with <emoji or sticker label or "[image]">
```

### 5.2 tkmChat (Flutter)

Use the existing reply preview widget (`message_bubble.dart:242 _ReplyPreview`). Replace the placeholder `replyToSignature.substring(0,8)` with a real lookup against the local message cache. For reactions, render under the parent bubble as a small stacked overlay (existing UI conventions).

### 5.3 General

- Clients MAY render reactions inline on the parent message (overlay) **or** as separate timeline entries — protocol does not mandate either.
- Reactions are **additive**. When the same sender reacts twice to the same parent, the most recent reaction supersedes earlier ones (client-side dedup by `(sender_pk, target_signature)`).
- No protocol-level "remove reaction" in v1.5. A future `reaction_remove` action can be added (closed list reopened).

---

## 6. Critical analysis

### 6.1 Issues that need a decision before merge

1. **`text_message` for reactions** — current bean has `textMessage` as a plain `String`, no nullability annotation. For reactions it is conceptually empty. We need to formally specify: empty string `""` SHOULD be used (not `null`), to keep canonical JSON predictable. Alternatively make it `@Nullable` and use `null` with `@JsonInclude(NON_NULL)`.
2. **Inline size cap constant** — §4.4 references `INLINE_MEDIA_MAX_BYTES` but no such constant exists in the Java codebase today. Define it (recommended: 51 200 bytes, i.e. 50 KiB).
3. **Reaction-target cardinality** (§3.1) — confirm whether to pin to 1 for v1.5 or allow multi-target from the start.
4. **Reaction MIME whitelist** — confirm allowed subtypes; `image/gif` (animated) and `image/webp` (animated) increase complexity for image-decoders. Stripping to `png/jpeg/webp/gif` is the safest pragmatic set.
5. **Notification routing** — replies to me and reactions to me should ideally notify me. The server can't see `targets`. The only existing notification mechanism is `cited_users` (plaintext, server-visible). Two options:
   - **Convention:** when replying to or reacting to someone else's message, the sender SHOULD add the parent author's pk to `cited_users`. This reuses the existing FCM pipeline at the cost of leaking "X replied to Y" metadata to the server — i.e. exposing the reply graph. This is the same trade-off documented in §8.6 of the E2E protocol doc.
   - **Pure-encrypted:** rely on the recipient's client to scan incoming messages for self-targeted actions and raise local notifications. No metadata leak, but only works for online users / clients that received the message.

   Recommend documenting both and letting the user pick per-client (mirrors the dual-mention privacy mode already specced).
6. **Author non-repudiation of the parent reference** — because `action`/`targets` are inside the encrypted body but the outer envelope is Ed25519-signed by the sender, the sender cannot deny having pointed at a specific parent. This is consistent with the rest of the protocol but worth recording: replying to a controversial message is a signed act.

### 6.2 Validation strictness

Reject vs. degrade — when in doubt, **degrade and render the body**. Discarding the entire message because the reply pointer is malformed loses the content the user wanted to deliver. Protocol-level "invalid action" should never delete the user-typed `text_message`.

Exception: a "reaction" message whose payload (emoji/sticker/inline) is also malformed *and* whose `text_message` is empty has nothing to render — that one is safe to discard with a log line.

### 6.3 Forward compatibility

- Older clients (Java pre-1.5, rsclient-flutter pre-update) ignore unknown JSON fields by default (Java side via `FAIL_ON_UNKNOWN_PROPERTIES=false` at `ChatUtils.java:185` and `SimpleRequestHelper.java:169`; Dart side via `json_serializable` default behaviour). They will see only `text_message`/`attached_media` and render the message as plain. Acceptable.
- New clients receiving an `action` they don't recognize MUST display the body unchanged (graceful degradation). This lets us add `reaction_remove`, `edit`, `redact`, etc. without breaking older listeners.

### 6.4 Security review

- **Confidentiality:** `action` and `targets` are inside the AES-GCM body — server-blind. ✅
- **Integrity:** outer Ed25519 envelope covers the entire signed-content bean which contains the encrypted blob; tampering with action/targets requires decrypting, modifying, re-encrypting (recipient detects via GCM tag), then re-signing (impossible without sender key). ✅
- **Non-repudiation:** sender cannot deny pointing at a specific parent. Trade-off, not a bug. ✅
- **Replay:** reusing a captured action message would require the same conversation key. A participant could "replay" their own reaction at any time; clients should treat duplicate (signature, target) pairs as no-ops. Worth a note.
- **DoS:** `targets` is bounded by §3.1 cardinality rule. No payload bloat risk.
- **Reaction storm:** a malicious participant could spam reactions. That is a rate-limit concern (`rschat` already has `RateLimitService`), not a protocol concern.
- **Cross-conversation leakage:** §4.2 step 4 enforces same-conversation parents — prevents a malicious sender from "pointing at" a message they shouldn't reference. The check is client-side and best-effort.

### 6.5 Cycle handling

`A.replyTo(B); B.replyTo(A)` is permissible at the protocol level. Display-time render-loop protection is a client concern (visited-set + max depth, e.g. 8). Spec leaves it client-defined.

### 6.6 Storage implications

- rschat: zero schema change. The new fields are inside the encrypted blob.
- tkmChat local Drift DB: already has `replyToSignature` column (`tables.dart:97`). Needs a parallel column for reactions (e.g. `reaction_target_signature`) and a reaction kind discriminator. Or a separate `reactions` table keyed by `(target_signature, sender_pk)` for dedup.
- shell: in-memory display only; no persistent storage.

---

## 7. Implementation work plan

### 7.1 Bean & protocol library

- [x] Add `action` + `targets` to `BasicMessageEncryptedContentBean.java` *(done by user, uncommitted)*
- [ ] Add `@JsonInclude(NON_NULL)` and `@JsonIgnoreProperties(ignoreUnknown=true)` to the bean
- [ ] Mirror in `rsclient-flutter/lib/src/beans/message/basic_message_encrypted_content_bean.dart`
- [ ] Regenerate `*.g.dart` via `build_runner`
- [ ] Add `INLINE_MEDIA_MAX_BYTES = 51200L` constant (location TBD: `ChatMediaPlaceholderBean` or a new `MessageActionConstants` class)
- [ ] Add `MessageAction` enum or constants class with `REPLY`, `REACTION`
- [ ] Add a `MessageActionValidator` utility (regex, cardinality, action recognition)

### 7.2 Senders

- [ ] `Messages/.../ChatCryptoUtils.getBasicMessageBean(...)` — extend signature to accept optional `action` + `targets`
- [ ] `tkmChat/lib/services/message_service.dart:255-258` — pass `replyToSignature` into the bean (currently dropped)
- [ ] shell — add command(s): `reply <message_signature> <text>` and `react <message_signature> <emoji|sticker|imagefile>`

### 7.3 Receivers

- [ ] `tkmChat` — when persisting an incoming message, copy `bean.action` / `bean.targets[0]` into local DB columns
- [ ] `tkmChat` — extend `_ReplyPreview` to resolve target signature against local cache
- [ ] shell — extend message print routine to emit the decoration lines from §5.1

### 7.4 Versions to bump

| Module | From | To | Rationale |
|---|---|---|---|
| `Messages` (`io.takamaka.messages:messages`) | `1.4.0-SNAPSHOT` | `1.5.0-SNAPSHOT` | New backward-compatible field |
| `rsclient` (`io.takamaka.rsocket:rsclient`) | `1.4-SNAPSHOT` | `1.5-SNAPSHOT` | Mirror Messages |
| `rschat` (`io.takamaka:rschat`) | `0.5.0-SNAPSHOT` | `0.5.1-SNAPSHOT` | Dep bump only; no logic change |
| `shell` (`io.takamaka.manage:shell`) | `0.5.1-SNAPSHOT` | `0.6.0-SNAPSHOT` | New user-visible commands |
| `rsclient-flutter` | `0.1.0` | `0.2.0` | New protocol field (minor) |
| `tkmChat` | `0.1.0+1` | `0.2.0+1` | Reply now functional cross-client |

Dependency declarations in `pom.xml`/`pubspec.yaml` must be updated consistently to point at the new versions.

### 7.5 Documentation

- [ ] Bump E2E protocol doc to v1.6, add §3.4 "Message Actions" with the same content as §2–§5 of this doc
- [ ] Update `rschat/CLAUDE.md` and `shell/CLAUDE.md` to mention `MessageAction` constants
- [ ] Append a closing entry to `SHELL_BOOTSTRAP.md` §10 once implementation merges, noting that the earlier "reply is stranded" finding is resolved as of Messages 1.5.0
- [ ] Update `tkmChat/CLAUDE.md` to remove the implicit assumption that reply doesn't round-trip

### 7.6 Tests

- [ ] Java: unit tests for `MessageActionValidator` (regex match/non-match, cardinality, action recognition, MIME whitelist)
- [ ] Java: integration test for reply round-trip (canonical JSON stability, signature verifies, decryption recovers fields)
- [ ] Cross-platform vector: same plaintext + key → identical canonical-JSON between Java and Dart
- [ ] Backward-compat: Messages 1.5 sender → Messages 1.4 receiver (older client must render the text and ignore the new fields)
- [ ] Forward-compat: Messages 1.5 receiver on a hypothetical action `"foo"` → graceful-degrade, render body only
- [ ] Reaction with each variant (emoji / sticker / inline image) → renders correctly
- [ ] Broken target (unknown signature) → decoration emitted, body still rendered

---

## 8. Decisions (resolved 2026-05-27)

| # | Question | Decision |
|---|---|---|
| 1 | Reaction `targets` cardinality | **= 1**. Multi-target reactions disallowed at the protocol level. |
| 2 | `text_message` for reactions | **MUST be `""` (empty string)**. `null` permitted on input but normalized to `""` on output. |
| 3 | Where to declare inline constants | Move existing constants from `shell/utils/ThumbnailService.java` to a new `Messages/.../chat/attachment/InlineContentLimits.java`. See §11. |
| 4 | Notify-on-reply mechanism | **Out of protocol.** Whether to add the parent author to `cited_users` for FCM routing is a per-client implementation choice, made explicitly at implementation time. No protocol mandate. |
| 5 | Animated WebP / GIF in reactions | **Allowed**, provided they satisfy inline limits (≤256×256, ≤50 KB). See §11.4. |
| 6 | Forbid reply-to-reaction and reaction-to-reaction at the protocol level | **No restriction.** Client renders a decoration line and the message body when encountering edge cases. |
| 7 | Maximum reply-chain depth before client compresses display | **Client UX choice.** Recommend 8 as a default; not protocol-binding. |

All "Non-negotiable refinements" from the original critical-analysis output are accepted:
- `@JsonInclude(NON_NULL)` and `@JsonIgnoreProperties(ignoreUnknown=true)` annotations applied to `BasicMessageEncryptedContentBean`.
- Strict Ed25519 signature regex `^[A-Za-z0-9_-]{86}\.\.$` for `targets` validation.
- Graceful-degradation rendering (never discard `text_message` for action-level malformations).
- Reactions are additive; same-sender re-react supersedes (client-side dedup by `(sender_pk, target_signature)`).
- Versions: `Messages 1.5.0`, `rsclient 1.5`, `rschat 0.5.1`, `shell 0.6.0`, `rsclient-flutter 0.2.0`, `tkmChat 0.2.0+1`.

---

## 9. Out of scope for v1.5

- Reaction removal / change (future: `reaction_remove` action)
- Message editing (future: `edit` action targeting self-authored parent)
- Message redaction / "deleted" tombstones (future: `redact` action)
- Forwards / quotes-without-reply
- Threaded conversation views (a rendering concern, not protocol)
- Multi-target reactions
- Reaction counters / aggregation (purely client-side display)
- Read receipts (would require server cooperation or out-of-band channel)

---

## 10. Acceptance criteria

The spec is considered ready to implement when:

1. The seven open questions in §8 have decisions.
2. `INLINE_MEDIA_MAX_BYTES` is defined and its enforcement location chosen.
3. Reaction-cardinality rule for v1.5 is fixed (1 vs. N).
4. Test plan §7.6 is approved.
5. All version bumps in §7.4 are agreed.

---

**Document Status:** Draft v2 — decisions resolved, ready for implementation kickoff.
**Path:** `/home/h2tcoin.com/giovanni.antino/NetBeansProjects/Messages/docs/roadmap/MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md`

---

## 11. Inline Content — Deep Specification

This section formalizes inline content (`isTheObject=true` path) into a normative protocol contract. The constraints already exist in code but are currently distributed and shell-scoped; they must be promoted to the protocol layer.

### 11.1 Current state (research findings)

| Concern | Location | Value |
|---|---|---|
| Max thumbnail dimension | `shell/.../utils/ThumbnailService.java:42` | `MAX_THUMBNAIL_SIZE = 256` |
| Max inline file size | `shell/.../utils/ThumbnailService.java:45` | `MAX_INLINE_SIZE = 50 * 1024` (51 200 bytes) |
| Inline criterion | `ThumbnailService.java:121-123` | `width ≤ 256 AND height ≤ 256 AND fileSize ≤ 50 KB` |
| Recognized image MIMEs (shell) | `ThumbnailService.java:47-50` | `jpeg, jpg, png, gif, bmp, webp, tiff` |
| Preview encoding | `ThumbnailService.java:129, 156` | **Standard Base64** (`Base64.getEncoder()`) — not URL-safe |
| Hash field encoding | `ChatMediaPlaceholderBean.java:81-84` doc | Base64URL with `.` padding |
| Dart parity | `rsclient-flutter/.../chat_media_placeholder_bean.dart` | 1:1 schema; same field semantics; no constants enforced yet |

**Issue:** the protocol-level invariants (`MAX_THUMBNAIL_SIZE`, `MAX_INLINE_SIZE`) live in the shell module. Flutter does not enforce them. Both clients must agree, so the constants need to move to a shared protocol location.

### 11.2 Field contract (normative)

When `isTheObject == true`:

| Field | Constraint |
|---|---|
| `preview` | MUST be non-null and non-empty. Carries the **full** content, not a thumbnail. Encoded as **standard Base64** (RFC 4648 §4, with `+`/`/` and `=` padding). |
| `encryptedFileHash` | MUST be `null`. (No server upload.) |
| `sed` | MUST be `null`. (No stream encryption descriptor — the content is encrypted only by the outer message-level AES-GCM, not by the separate file-stream pipeline.) |
| `unencryptedContentHash` | MUST be `SHA3-256(base64Decode(preview))`, encoded as **Base64URL with `.` padding**. |
| `originalSize` | MUST equal `base64Decode(preview).length` in bytes. |
| `size` | MUST equal `originalSize` (no encryption overhead since the only encryption is the outer message body). |
| `mediaType` | MUST be a recognized MIME from the whitelist in §11.4. |
| `fileName` | OPTIONAL. Display hint. |
| `packHash`, `stickerId`, `emoji` | Phase 2 reserved; MUST all be `null` for inline-image use. |

When `isTheObject == false` (regular attachment, unchanged):
- `encryptedFileHash` MUST be populated.
- `sed` MUST be populated.
- `preview` is optional (256×256 WebP thumbnail).
- `size` = encrypted/Base64 size; `originalSize` = plaintext file size.

### 11.3 Constants — protocol promotion

Create `Messages/src/main/java/io/takamaka/messages/chat/attachment/InlineContentLimits.java`:

```java
package io.takamaka.messages.chat.attachment;

/**
 * Protocol-level limits for inline content (isTheObject=true).
 * <p>These constants are normative for both Java and Dart clients.
 * Any inline placeholder violating them MUST be rejected by the receiver.
 */
public final class InlineContentLimits {
    /** Maximum pixel dimension (width and height) for an inline image. */
    public static final int MAX_THUMBNAIL_DIMENSION_PX = 256;

    /** Maximum byte length of the decoded inline payload (i.e. base64Decode(preview).length). */
    public static final int MAX_INLINE_BYTES = 50 * 1024; // 51_200

    /** Allowed top-level MIME family for inline content carrying image data. */
    public static final String INLINE_IMAGE_MIME_FAMILY = "image/";

    private InlineContentLimits() {}
}
```

Update `ThumbnailService` (shell) to import and reference these constants instead of declaring its own. Mirror in Dart:

```dart
// rsclient-flutter/lib/src/beans/attachment/inline_content_limits.dart
class InlineContentLimits {
  static const int maxThumbnailDimensionPx = 256;
  static const int maxInlineBytes = 50 * 1024;
  static const String inlineImageMimeFamily = 'image/';
  InlineContentLimits._();
}
```

### 11.4 Inline MIME whitelist (normative)

Two whitelists coexist:

**General inline image whitelist** (current `ThumbnailService.IMAGE_MIME_TYPES`, retained for non-reaction inline attachments):
```
image/png, image/jpeg, image/jpg, image/gif, image/webp, image/bmp, image/tiff
```

**Reaction-payload whitelist** (narrower — per the decision in §3.1 / §4.4):
```
image/png, image/jpeg, image/webp, image/gif
```

`image/webp` and `image/gif` include animated variants. Animated payloads are allowed iff they satisfy the byte and dimension limits above. `image/bmp` and `image/tiff` are deliberately excluded from reactions — they bloat for trivial content and don't match common emoji-pack tooling.

### 11.5 Encoding asymmetry (footgun documentation)

The `preview` field uses **standard Base64** (with `+`, `/`, `=`). Every other base64-encoded protocol field uses **Base64URL with `.` padding**. This asymmetry is historical (`javax.imageio` → `Base64.getEncoder()` was the path of least resistance for shell) and must be preserved for compatibility with existing inline content that Flutter and shell already produce.

**Consequence:** clients MUST NOT base64url-decode `preview`. The validator MUST use the standard base64 decoder. Cross-platform test vectors should pin this.

### 11.6 Integrity verification — receiver pipeline

For every inline placeholder with `isTheObject == true`:

1. `decoded = base64StandardDecode(preview)` — fail with `INLINE_DECODE_ERROR` decoration if decode fails.
2. Reject if `decoded.length > MAX_INLINE_BYTES`.
3. Compute `recomputed = base64UrlDotPadEncode(sha3_256(decoded))`.
4. Reject if `recomputed != unencryptedContentHash` → `INLINE_HASH_MISMATCH` decoration.
5. Reject if `mediaType` not in the contextually-appropriate whitelist (§11.4).
6. Image-specific: decode header for dimensions; reject if `width > 256 || height > 256` → `INLINE_DIMENSION_VIOLATION` decoration.
7. Reject if `originalSize != decoded.length` or `size != decoded.length` → `INLINE_SIZE_MISMATCH`.
8. Reject if `encryptedFileHash != null` or `sed != null` → `INLINE_FIELD_VIOLATION` (mixing inline and download paths).

Rejection at the inline-content level does NOT discard the parent message. The message's `text_message` (if any) renders normally, with a single decoration line summarizing the inline failure.

### 11.7 Test vectors required

- 1×1 transparent PNG (~70 bytes) → `originalSize == size == 70`, hash matches.
- 256×256 PNG at maximum size (~49 KB) → accepted.
- 257×256 PNG → rejected, dimension violation.
- 256×256 PNG at 51 KB → rejected, byte violation.
- Animated WebP, 200×200, 45 KB → accepted (reaction-whitelist test).
- TIFF inline → rejected for reactions, accepted for general inline.
- `preview` with URL-safe base64 (`-_`) → rejected (encoding mismatch).

---

## 12. Action Extension Proposal — additional actions worth designing now

The `(action, targets)` shape generalizes cleanly. The following actions plug into the same registry and the same `BasicMessageEncryptedContentBean` without further wire changes. Designing them here in one breath prevents re-opening the spec piecemeal.

### 12.1 Action registry — extended

| Action | `targets` cardinality | `text_message` | `attached_media` | Author check | Notes |
|---|---|---|---|---|---|
| `reply` | 1 | non-empty (typical) | optional | none | §3-§5 |
| `reaction` | 1 | `""` | exactly 1 (emoji/sticker/inline-image) | none | §3-§5, §11 |
| `reaction_remove` | 1 | `""` | absent / null | sender must match prior reaction's sender | §12.2 |
| `edit` | 1 | new text | new media (optional) | sender must equal parent author | §12.3 |
| `redact` | 1 | optional reason ("" allowed) | absent | sender must equal parent author | §12.4 |
| `pin` | 1 | optional note | absent | conversation participant (admin-only optional) | §12.5 |
| `unpin` | 1 | `""` | absent | same as pin | §12.5 |

The following are **deferred** with rationale in §12.6: `forward`, `read_receipt`, `typing`, `vote`/`poll_response`, generic `acknowledge`.

### 12.2 `reaction_remove`

**Purpose.** Remove a previously-emitted reaction from this sender to a specific parent message.

**Semantics.**
- `targets[0]` = **the parent message's signature** (not the prior reaction's signature). This is consistent with the additive-reactions rule (one reaction per sender per parent) — removing requires identifying the parent, not the prior reaction.
- `text_message = ""`.
- `attached_media = null`.

**Receiver validation.**
- Standard target validation (§4.1, §4.2 steps 1-4).
- Self-removal only: the `from` field of the envelope must equal the sender of the prior reaction this is removing. Clients maintain a local index of `(parent_sig, sender_pk) → reaction_message_sig`; if no entry exists, render `INVALID reaction_remove (no prior reaction)` decoration but do not discard.

**Client behaviour.**
- Drops the corresponding entry from the local reaction index.
- UI: the prior reaction sticker/emoji disappears from the parent's overlay.

**Why design now.** Reactions without removal are clumsy. Defining `reaction_remove` immediately avoids a v1.6 "we should have allowed removal" rework. Cost: zero protocol surface (same shape).

### 12.3 `edit`

**Purpose.** Replace own previous message text/media.

**Semantics.**
- `targets[0]` = signature of the message being edited.
- `text_message` = new text.
- `attached_media` = new media list (replaces parent's; not a delta).

**Receiver validation.**
- Standard target validation.
- **Author check:** `envelope.from == lookup(targets[0]).from`. If different, render `INVALID edit (not author)` and ignore the edit, render the new message as a plain message.
- Edits MUST NOT be themselves edited or redacted in v1.5+1 (keep it tractable): if a client receives `edit` whose `targets[0]` resolves to another `edit` message, it normalizes by walking the chain to the original parent. Cycle protection: max-depth 4.

**Client behaviour.**
- UI shows latest-edit content; clients MAY expose an "edit history" view by walking the edit chain.
- Audit: edits remain visible in conversation history (signed records); there is no protocol-level forgetting.

**Caveat.** Because the protocol cannot truly mutate a stored message (the server only relays signed records), `edit` is a display directive — clients must cooperate. A non-cooperating client could keep showing the original. Document this honestly.

### 12.4 `redact`

**Purpose.** Soft-delete own previous message.

**Semantics.**
- `targets[0]` = signature of the message to redact.
- `text_message` = optional reason (empty string permitted).
- `attached_media = null`.

**Receiver validation.**
- Standard target validation.
- **Author check:** `envelope.from == lookup(targets[0]).from`. Cross-author redacts ignored.
- Conversation admins MAY have a separate `redact_admin` action later (out of scope; would expand the author-check matrix).

**Client behaviour.**
- UI replaces parent's text and media with a tombstone (e.g. "[message deleted by sender]"). Original content MAY still be stored locally (some clients want undo); MUST NOT be displayed by default.
- Redacted messages still consume conversation history; they cannot be truly removed from the rschat database without server cooperation (and even then the encrypted blob would remain hashed-referenced from other messages potentially).

**Caveat.** Same cooperation requirement as `edit`. The relay server retains the redacted message; only cooperating clients honor the directive.

### 12.5 `pin` / `unpin`

**Purpose.** Designate one or more messages as conversation-pinned (sticky / featured).

**Semantics.**
- `pin`: `targets[0]` = message signature to pin. `text_message` = optional admin note. `attached_media` = null.
- `unpin`: `targets[0]` = previously-pinned message signature. `text_message = ""`. `attached_media = null`.

**Receiver validation.**
- Standard target validation.
- **Authorization choice (decide before implementation):**
  - **Option A — open:** any conversation participant may pin/unpin. Last action wins.
  - **Option B — admin only:** only senders whose role in `users_to_conversations` is `administrator`. The role is not in the encrypted message — clients consult their local copy of conversation membership. A non-admin pinning would be flagged with `INVALID pin (not admin)` decoration.
- Recommend **Option B** for v1.5+1: aligns with the existing role model and prevents pin spam.

**Client behaviour.**
- Maintains an ordered list of pinned messages per conversation by replaying `pin`/`unpin` actions in conversation order. The state is **derived**, not stored on the server.
- UI: pinned strip at the top of the conversation; tap → jump-to-message.

**Why design now.** Pin/unpin is the most common conversation feature beyond reply/react in modern chats. The action shape fits perfectly. Cost: one optional admin check at the validator layer.

### 12.6 Deferred actions — with reasoning

| Action | Why defer |
|---|---|
| `forward` | Cross-conversation correlation problem. The forwarded signature won't resolve in the recipient conversation; we'd need a `forwarded_from_conversation_hash` field that leaks a cross-conversation link to anyone who shares both keys. Worth a dedicated proposal. |
| `read_receipt` | Persists in conversation history (server stores every signed message). Bloats timeline, costs bandwidth and storage for a UX nicety. Needs an ephemeral / out-of-band transport that doesn't exist. |
| `typing` | Same as above. Inherently ephemeral; persistent signed messages are the wrong substrate. Would need a new RSocket endpoint with per-conversation ephemeral fan-out. |
| `vote` / `poll_response` | Implies a `poll` action that defines the poll first, then `vote` actions targeting it. Two-stage protocol with quorum, deduplication, and result-counting semantics. Dedicated spec. |
| `acknowledge` (generic) | Subsumed by `read_receipt`'s issues. No clear additional use case. |
| `bookmark`, `flag` | Purely local UX. No wire format needed; clients implement locally. |

### 12.7 Forward-compatibility guarantee for unknown actions

A receiver encountering an unrecognized `action` value MUST:

1. NOT discard the message.
2. Render the `text_message` (if non-empty) and any valid `attached_media`.
3. Emit a single decoration line: `unsupported action "<value>" (target: <truncated_signature>)`.
4. Not attempt to interpret `targets`.

This guarantee is what makes piecemeal action additions safe across version skew. It MUST be tested with a synthetic action `"__test_unknown_action__"` in the test suite.

### 12.8 Cross-action interactions

| Scenario | Behaviour |
|---|---|
| Edit of a reply | Standard edit. The reply pointer is part of the edited message body. To preserve the reply linkage, the edited message should keep the same `action="reply"` + same `targets`. |
| Reply to an edited message | The reply's `targets[0]` is the **original** signature, not any edit. Clients resolve through the edit chain when displaying. |
| Reply to a redacted message | Reply is delivered; receiver renders "valid reply to [deleted message]". |
| React to a redacted message | Reaction is delivered; receiver renders the reaction with `[deleted message]` as the target preview. |
| Edit of a reaction | **Forbidden by spec.** Reactions are immutable; use `reaction_remove` + new `reaction`. |
| Redact of a reaction | Allowed (it's just deleting your own message). Equivalent in effect to `reaction_remove`. Clients SHOULD treat both as the same delete operation. |
| Pin/unpin of a redacted message | Pinning is permitted but UI MAY hide pinned tombstones. Unpinning a redacted pin is straightforward. |
| Multiple pins | Conversation may have multiple pinned messages; pin order is the order of `pin` actions. Optional client cap (e.g. 5) for display. |

### 12.9 Implementation plan addendum (extends §7)

If the Tier-1 extensions (`reaction_remove`, `edit`, `redact`) ship together with reply/reaction:

**Versions become:**
- `Messages` → `1.5.0-SNAPSHOT` (unchanged; same wire format)
- `rsclient` → `1.5-SNAPSHOT`
- `rschat` → `0.5.1-SNAPSHOT`
- `shell` → `0.6.0-SNAPSHOT` (new commands: `reply`, `react`, `unreact`, `edit`, `redact`)
- `rsclient-flutter` → `0.2.0`
- `tkmChat` → `0.2.0+1` (new UI affordances; local DB schema migration for reaction index and edit chains)

**Additional test vectors:**
- Edit chain depth 3, all signed by same author → resolves to most-recent text.
- Edit by non-author → rejected, parent unchanged.
- Redact by non-author → rejected.
- `reaction_remove` without prior reaction → decoration, no state change.
- Unknown action `"__test_unknown_action__"` → text body rendered, decoration emitted.

**Pin (Tier 2)** can ship in the same release if admin-check logic is reused from existing membership code (`users_to_conversations`). Otherwise punt to v1.6.

---

## 13. Decision request for §12 (before extension implementation)

1. Tier-1 (`reaction_remove`, `edit`, `redact`) — ship together with reply/reaction in 1.5.0, or in 1.5.1?
2. Pin/unpin authorization — Option A (open) or Option B (admin only)?
3. Cross-action interaction matrix (§12.8) — approved as written?
4. Edit-chain max depth — 4 acceptable, or different?
5. Forward, polls, read receipts — confirm deferral, or sponsor one for its own dedicated spec?
