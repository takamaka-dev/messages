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

| Value | Cardinality of `targets` | Required content | Authorization | Notes |
|---|---|---|---|---|
| `null` / absent | n/a | n/a | none | Plain message. Indistinguishable from pre-1.5 behaviour. |
| `"reply"` | exactly 1 | `text_message` non-empty (typical); `attached_media` optional | none | Quotes a single parent message. |
| `"reaction"` | exactly 1 (see §3.1) | one of: inline image / `sticker_id` / `emoji` | none | `text_message` SHOULD be `""` (see §3.2). |
| `"pin"` | exactly 1 (message signature) | `text_message` optional pin reason, SHOULD be ≤ 200 chars; `attached_media` MUST be null | creator-only | See §5.5 for aggregation, §12.5 for full design. |
| `"unpin"` | **0** (`targets` MUST be null / empty / absent) | `text_message` SHOULD be `""`; `attached_media` MUST be null | creator-only | Clears the pin slot for the conversation. |
| `"forward"` | **0 or 1** (claimed-origin public key, or absent for anonymous forward) | `text_message` carries the forwarded text; `attached_media` MAY carry re-encrypted attachments | none | See §12.10. `targets[0]` is a **public key**, not a signature — different regex. Origin claim is **unverifiable** by design. |
| `"share_history"` | **0** | `text_message` optional relayer note; `original_message` MUST be populated with the encrypted original envelope | none | See §12.11. In-conversation only. The embedded original signature is preserved and **cryptographically verifiable** — a different trust model from `forward`. |

### 3.2 `text_message` for reactions (normative, RFC 2119)

For action `"reaction"`, the `text_message` field SHOULD be the empty string `""`.

- **Senders** SHOULD set `text_message = ""` on outgoing reactions. Senders MAY emit `null` for compactness when `@JsonInclude(NON_NULL)` is in effect; this is equivalent to `""` for protocol purposes.
- **Receivers** MUST accept reactions whose `text_message` is `""`, `null`, or absent — all three are equivalent.
- **Receivers** that encounter a reaction with a non-empty `text_message` MUST NOT discard the message. They SHOULD render the reaction payload (emoji / sticker / inline image) normally and MAY additionally render the accompanying text, with an implementation-defined visual cue indicating that text on a reaction is non-canonical (e.g. tooltip "reaction carried unexpected text"). Logging or telemetry at the warning level is permitted.
- This rule is **SHOULD**, not **MUST**, because the wire format cannot enforce text emptiness without either rejecting valid signed messages or breaking forward compatibility with future reaction variants that might carry an optional caption (e.g. "thumbs up + comment"). Promoting this to MUST is deferred.

### 3.3 Notification routing — protocol support, client policy

**The protocol SUPPORTS targeted notifications but does NOT FORCE them.** Whether a reply or reaction triggers a visible push notification to the targeted user is a **client policy decision**, not a protocol constraint. The protocol layer is, and remains, agnostic.

#### The mechanism

The only mechanism by which the server can route a targeted FCM push to a specific user is the **plaintext `cited_users` field** in `BasicMessageSignedContentBean` (see the parent E2E protocol doc §8.6). This field already exists, predates this spec, and is read by `rschat`'s notification pipeline at `MessageUtils.java:155` to fan out per-recipient pushes via `NotificationUtils.submitUserNotifications(...)` and `FcmService`.

- `cited_users` lives in the **signed-but-unencrypted** envelope. The server reads it.
- Encrypted-body fields (`action`, `targets`, `text_message`, attachments) are server-blind. The server cannot tell that message M is a reply to message N, nor who the reaction targets, because that information is inside the AES-GCM ciphertext.

**Consequence:** if a client wants the server to deliver an "Alice replied to you" or "Bob reacted to your message" push, that client MUST add the parent author's public key to `cited_users` at send time. There is no other way. Conversely, a client that omits `cited_users` produces a message for which the server can at best emit a generic "new message in conversation" notification.

#### Protocol stance — normative

- The protocol **SUPPORTS** notification routing via `cited_users`. Servers and clients are required to honour `cited_users` exactly as before this spec — no semantic change.
- The protocol **does NOT REQUIRE** that replies or reactions populate `cited_users`. Sending a reply with `cited_users = []` is fully valid. Sending a reaction with `cited_users = []` is fully valid.
- The protocol **does NOT FORBID** populating `cited_users` for a reply/reaction either. Sending a reply with `cited_users = [parent_author_pk]` is fully valid.
- Receivers MUST NOT change rendering or trust based on whether `cited_users` was populated. The `cited_users` field affects server-side push fan-out only; client-side display logic is governed by `action` + `targets` inside the encrypted body, which is the authoritative source.

#### Client policy — where the decision lives

The decision **whether** to populate `cited_users` on outgoing replies/reactions belongs to the **client implementation** and is expected to be controlled by user-visible privacy settings. Two layers, consistent with the dual-mention privacy model already proposed in `MENTION_PRIVACY_COMPLIANCE_ANALYSIS.md`:

1. **Per-conversation override.** Settings attached to the active conversation (e.g. `ConversationSettings.notificationMode`) determine the default for messages in that conversation. Privacy-sensitive conversations (e.g. "anonymous tip line") can be configured to never populate `cited_users` regardless of message type.
2. **Global default.** A user-level preference (e.g. `UserPrivacyPreferences.allowPlaintextMentions`, already proposed in the mention-privacy analysis) sets the fallback for conversations that do not override.

Recommended defaults across clients:

| Setting | Privacy-first default | Convenience default |
|---|---|---|
| Reply populates `cited_users` with parent author | off | on |
| Reaction populates `cited_users` with parent author | off | on |
| Generic `@mention` populates `cited_users` | off (mention text remains inside encrypted body) | on |

Each client SHOULD ship with one of these as the out-of-box default and SHOULD expose a single visible toggle to switch. Per-conversation overrides SHOULD be available but MAY be hidden behind an advanced settings menu.

#### Trade-offs surfaced to the user

When `cited_users` is populated, the server can build a partial social-interaction graph: who replies to whom, who reacts to whom, mention frequency, time-of-day patterns. Message content remains encrypted; only the fact of citation leaks. When `cited_users` is empty, the server sees only generic conversation traffic — at the cost of: targeted offline pushes do not work for that message, so the recipient sees the reply/reaction only when they next open the app.

This trade-off MUST be disclosed in client-facing privacy documentation. The same trade-off applies to all `cited_users` usage, not just reply/reaction — this section formalizes the existing protocol stance for the new actions.

#### Cross-platform parity (informative)

- **shell:** introduce a `chat-privacy notifications <on|off|per-conversation>` command and a `conversation set notifications <on|off|inherit>` per-conversation override. Default: privacy-first (off).
- **tkmChat:** the existing privacy preferences UI should grow a "show me in others' mention notifications" toggle and per-conversation analog. Default: privacy-first.
- **rsclient / rsclient-flutter:** no API changes. The `cited_users` list is already a per-call parameter on the message-send entry points.
- **rschat:** no changes. Existing notification fan-out continues to honour whatever `cited_users` arrives.

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

1. **Action recognized?** If `action` is not in the registered set (§3 table), fall back to plain-message rendering and ignore `targets`. **Do not discard the message.**
2. **Per-action cardinality.** Each action declares its `targets` cardinality in the registry. Receivers MUST validate the incoming `targets` against the declared cardinality. Current values:
   - `reply`, `reaction`, `reaction_remove`, `edit`, `redact`, `pin` → **exactly 1**
   - `unpin`, `share_history` → **0** (null, empty list, or absent — all equivalent)
   - `forward` → **0 or 1** (1 = claimed origin, 0 = anonymous forward)

   Mismatch → decoration `INVALID <action> reference — malformed targets`, body still rendered.
3. **Per-action target format.** Each action declares the format of its `targets[0]`:
   - **Signature-typed targets** (`reply`, `reaction`, `reaction_remove`, `edit`, `redact`, `pin`): match the Ed25519 envelope-signature regex `^[A-Za-z0-9_-]{86}\.\.$`.
   - **Public-key-typed targets** (`forward`, when cardinality 1): match the Ed25519 public-key regex `^[A-Za-z0-9_-]{43}\.$` (32 bytes encoded as 43 base64url chars + 1 padding dot).
   - **No targets** (`unpin`, `forward` with cardinality 0): no format check.

   Mismatch → decoration `INVALID <action> reference — bad <signature|public-key> format`, body still rendered.
4. **Each signature-typed target belongs to the conversation the message was sent in?** (Skipped for cardinality 0 and for public-key-typed targets.) Lookup against the client's local message cache for this `conversation_hash_name`. If the parent is unknown:
   - **Reply:** decorate `INVALID reply reference to <target>` or, if user prefers, "reply to (message not in cache)" — softer wording for known offline scenarios. Continue rendering the message.
   - **Reaction:** decorate `reaction targeting unknown message <target>`. Optionally skip rendering entirely depending on client UX.
   - **Pin:** decorate `[pinned message not in cache]` in the pinned-message slot. The pin slot still updates (target signature is recorded); resolution is best-effort whenever the parent eventually arrives.
   - **Forward:** N/A — the target is a public key, not a signature, and not a resolvable message reference. Step 4 is skipped. Clients SHOULD try to resolve the PK against their local contacts / registered-users cache to render a display name; resolution failure is normal and not a validation failure.
5. **Authorization.** Some actions require an authorization check (see §12.7 for the three patterns). For `pin` and `unpin`: `envelope.from` MUST equal the conversation creator's PK (= `from` of the decrypted `CreateConversationRequestBean`). For `edit`, `redact`, `reaction_remove`: see action-specific rules. Failure → `INVALID <action> (not authorized)` decoration, body still rendered.
6. **Action-specific checks** — see §4.3 and §4.4.

The parent message is identified by its **outer envelope Ed25519 signature** — i.e. the same value used as the rschat `messages.message_signature` primary key, and as the `signature` field of `SignedMessageBean`.

### 4.3 Reply-specific checks

- `text_message` SHOULD be non-empty (the reply text). Empty replies are valid but unusual.
- `attached_media` MAY be present.
- Self-replies allowed. Cross-conversation replies disallowed by step 4.

### 4.4 Reaction-specific checks

Exactly one of the following must hold on the reaction message:

| Variant | Conditions |
|---|---|
| **Emoji** | `attached_media[0].emoji` set, `attached_media[0].sticker_id` null, `is_the_object == true` not required (emoji is the payload). `text_message` SHOULD be `""` (§3.2). |
| **Sticker** | `attached_media[0].sticker_id` set (+ optional `pack_hash`), `emoji` null. `text_message` SHOULD be `""` (§3.2). |
| **Inline image** | exactly one element in `attached_media`, `is_the_object == true`, `media_type` starts with `image/` and is in the reaction whitelist, `preview` populated with the full standard-Base64 content, `original_size <= InlineContentLimits.MAX_INLINE_BYTES`, image decodable to ≤ `InlineContentLimits.MAX_THUMBNAIL_DIMENSION_PX` pixels in both dimensions. `encrypted_file_hash` and `sed` MUST be null. `text_message` SHOULD be `""` (§3.2). |

Inline cap constants live in `io.takamaka.messages.chat.attachment.InlineContentLimits` (defined under this branch). Values: `MAX_INLINE_BYTES = 51200` (50 KiB), `MAX_THUMBNAIL_DIMENSION_PX = 256`. See §11.3 for full constant definitions and the migration of `shell/.../ThumbnailService` to reference them.

Reactions using inline-image payload MUST carry a `mediaType` from the closed set defined in `InlineContentLimits.REACTION_ALLOWED_IMAGE_MIMES`:

```
image/png, image/jpeg, image/webp, image/gif
```

Any other MIME — including `image/bmp`, `image/tiff`, the looser `image/jpg` synonym, or non-image families — MUST be rejected at the action-validation layer (decoration code `INLINE_MIME_VIOLATION`, parent `text_message` still rendered). Animated `image/gif` and `image/webp` are permitted iff they also satisfy `MAX_INLINE_BYTES` and `MAX_THUMBNAIL_DIMENSION_PX`.

The whitelist is closed. Adding a MIME requires a wire-protocol revision and coordinated client updates.

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
- Reactions are **additive**. When the same sender reacts twice to the same parent, the most recent reaction supersedes earlier ones (client-side dedup by `(sender_pk, target_signature)`). Authoritative aggregation rule in §5.4.
- No protocol-level "remove reaction" in v1.5. A future `reaction_remove` action can be added (closed list reopened).

### 5.4 Reaction aggregation (normative)

> §5.1–§5.3 above are **informative** rendering guidelines. **§5.4 is normative** — all clients MUST implement the same logical aggregation, otherwise they will show divergent reaction state for the same conversation history.

Each client MUST maintain, per conversation, a logical table:

```
reactions: (parent_signature, sender_pk) → reaction_payload
```

where `(parent_signature, sender_pk)` is the primary key.

**On receipt of a valid `action="reaction"`** targeting `parent_signature` from `sender_pk`:

1. If no row exists for the key, **insert** with the reaction's payload and its `reception_timestamp`.
2. If a row exists, **overwrite iff** the incoming reaction's `reception_timestamp` is strictly greater than the stored row's `reception_timestamp`. On exact timestamp equality, overwrite iff the incoming envelope signature is lexicographically greater than the stored row's envelope signature.

**On receipt of a valid `action="reaction_remove"`** (§12.2) from `sender_pk` targeting `parent_signature`: **delete** the corresponding row (no-op if absent). If a `reaction` and a `reaction_remove` arrive out-of-order, treat them as two events at the same key and let the one with the larger `(reception_timestamp, envelope_signature)` tuple win — i.e. the most recent action for `(parent_signature, sender_pk)` is authoritative, whether it is a `reaction` (row present) or a `reaction_remove` (row absent).

**The chronological clock** is the **server-assigned `RetrieveMessagesResponseBean.receptionTimestamp`** (rschat writes this as `messages.message_timestamp` when it accepts the message; it is propagated to clients verbatim in every retrieval response). Sender-claimed timestamps inside the encrypted body, if present for other purposes, **MUST NOT** be used for reaction ordering.

**Why server timestamp** — design rationale, summarized:

- It is the only candidate that is deterministic across clients (sender-claimed times can be forged by a malicious sender; per-client receipt order is non-deterministic by definition).
- Using it grants the server no new capability — the server already controls message delivery order and timing, an accepted out-of-scope threat under the zero-trust relay model (E2E protocol doc §8.5).
- The narrow residual threat — server choosing which of one sender's duplicate reactions wins — is documented in §6.4 of this spec.

The above defines the **logical state** of reactions on a parent. **How** that state is rendered (layout, counts, ordering within a reaction strip, animation, reactor-identity disclosure, overflow handling, grouping by emoji) is entirely client-defined and out of scope.

#### 5.4.1 Anchoring and re-delivery (normative)

Reactions and replies key on **target signature**, computed at original signing time and independent of any re-delivery mechanism. Re-delivery operations (`forward`, `share_history`) do **NOT** re-anchor existing reactions or replies onto the re-delivery envelope.

Two rules follow:

**Anchoring rule.** A reaction or reply targeting signature S aggregates against the message with signature S, regardless of how that message reached the receiver.

- For `forward`: source-conversation reactions and replies on the original message never reach the destination conversation; the destination receiver has no visibility into them. Post-forward reactions and replies in the destination target the **forwarder's** envelope signature (the forward is a new message in the destination conversation). The source's reaction history is structurally inaccessible to destination receivers and is not propagated through `fw_content`.
- For `share_history`: the embedded `original_message` keeps its original signature; reactions and replies targeting that signature continue to anchor to the embedded original, not to the share wrapper. The share wrapper has its own signature (the relayer's envelope); reactions and replies targeting the wrapper anchor to the wrapper. The two sets are independent and do not combine.

**Rendering rule.** Clients MUST NOT bind pre-existing reactions or replies — those whose `reception_timestamp` is earlier than the share's `reception_timestamp` — as decorations on the share's wrapper rendering. They were emitted in the original's conversational context, not in the late-receiver's context, and binding them to the share's rendering would misrepresent the reactor's intent. The reactions still aggregate against the original target's signature in the conversation's logical reaction table; clients MAY render the share without showing them inline, or MAY render the original at its native timeline position separately with its reactions attached there, or MAY offer a "view all reactions on this message" affordance that is independent of any specific rendering position.

**Rationale.** A reactor at time T reacted to M1 in M1's native conversational context. A late receiver D obtaining M1 via a `share_history` at time T' > T sees M1 in a different context: their own view at T', which contains none of what was happening when the reactor reacted. Forcing T's reactions onto D's T' rendering would misrepresent the reactor as having reacted in D's context. The reactions remain real and remain anchored to M1's signature; they just don't decorate the re-delivery wrapper.

Temporal causality between a reaction and its target is automatic: a reactor cannot know the target's signature before the target is signed, so reaction `reception_timestamp` is always ≥ target `reception_timestamp` under honest protocol use. No separate causality validation rule is needed.

### 5.5 Pin aggregation (normative)

Each client MUST maintain, per conversation, a logical single-slot table:

```
pinned: conversation_hash → pinned_message_signature
```

where `conversation_hash` is the primary key. Each conversation has **at most one pinned message at any time** — there is no list of pinned messages, no ranking, no priority.

**On receipt of a valid `action="pin"`** (authorization passed — see §4.2 step 5 and below) targeting `pinned_message_signature` in conversation `conversation_hash`:

1. If no row exists for the key, **insert** with the pin's `targets[0]` and its `reception_timestamp`.
2. If a row exists, **overwrite iff** the incoming pin's `reception_timestamp` is strictly greater than the stored row's `reception_timestamp`. On exact timestamp equality, overwrite iff the incoming envelope signature is lexicographically greater than the stored row's envelope signature.

**On receipt of a valid `action="unpin"`** (authorization passed) for conversation `conversation_hash`: **delete** the corresponding row (no-op if absent). If a `pin` and an `unpin` arrive out-of-order, treat them as competing events at the same key and let the one with the larger `(reception_timestamp, envelope_signature)` tuple win — exactly mirroring the `reaction` / `reaction_remove` rule in §5.4.

**Authorization is creator-only** for both `pin` and `unpin`. A client MUST verify, before applying the state change, that `envelope.from` equals the `from` field of the decrypted `CreateConversationRequestBean` for this conversation. Non-creator pins / unpins are rejected with decoration `INVALID <pin|unpin> (not conversation creator)`; the slot does not update. The check is purely local — both values live in already-decrypted client state.

**Clock and tiebreaker** are identical to §5.4: server-assigned `RetrieveMessagesResponseBean.receptionTimestamp`, signature lex tiebreaker on equality. Server-timestamp ordering of duplicate pins is subject to the same narrow accepted threat documented in §6.4.

**Resolution of the pinned target.** Display-time, the client looks up `pinned_message_signature` in its local message cache. If present → render normally in the pinned slot. If absent (the pinned message has not yet been received, or was redacted, or was never sent to this client) → render the slot with a broken-reference decoration (e.g. `[pinned message not in cache]`). The slot's logical state is still authoritative; rendering is best-effort.

**Empty state.** When no row exists for the conversation, no pinned-message slot is rendered. Clients SHOULD NOT show an empty placeholder.

**How** the pinned slot is rendered (top-of-conversation strip, sidebar, hover affordance, jump-to-message action, animation) is entirely client-defined and out of scope.

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
- **Server-timestamp ordering of duplicate reactions (narrow accepted threat):** The reaction aggregation rule (§5.4) uses the server-assigned `reception_timestamp` as the chronological clock. A hostile server CAN reorder duplicate reactions from the same sender on the same parent message — i.e. choose which of that sender's own reactions wins in the per-conversation aggregate. It CANNOT forge reactions, change the reactor's identity, alter the reaction payload, or affect ordering across different senders (each `(parent, sender)` key is independent). The affected sender retains an authoritative local outbox and can detect divergence between what they sent and what other participants see. This is a narrow case of the general "server controls message delivery order" accepted threat already documented in `rschat-docs/.../E2E_ENCRYPTION_PROTOCOL_DOCUMENTATION.md` §8.5 / `rschat/docs/architecture/E2E_ENCRYPTION_PROTOCOL_DOCUMENTATION.md` §8.5. **TODO (during implementation):** propagate this bullet verbatim into that parent threat-model section so its enumeration of "out-of-scope availability attacks" explicitly names duplicate-reaction reordering.
- **Server-timestamp ordering of competing pin / unpin (narrow accepted threat):** Same shape as the reaction case. A hostile server CAN reorder competing `pin` and `unpin` events authored by the conversation creator, selecting which of the creator's own pin decisions wins. It CANNOT forge pins (creator-only authorization is checked client-side against the signed envelope), change which message is pinned by a forged pin, or pin from a non-creator identity. The creator can detect divergence between their own action log and what other participants see. Subsumed by the same "server controls message delivery order" out-of-scope threat.
- **Creator-only authorization (current model):** `pin` / `unpin` are authorized by PK equality between the action's `envelope.from` and the conversation creator (= `from` of the decrypted `CreateConversationRequestBean`). The check is purely client-side, deterministic, and uses data every cooperating client already holds. Limitations honestly stated: there is no protocol mechanism to add, remove, or transfer admins. The creator is the sole authority for pin operations for the conversation's lifetime. If a richer admin model is ever needed, it requires a separate protocol revision adding promote/demote actions plus a derived admin-set per conversation — out of scope here.
- **Unverifiable origin claim on `forward` (accepted by design):** The `forward` action carries the **claimed-origin public key** in `targets[0]` but transmits neither the original signature nor the original encrypted blob. The recipient cryptographically verifies the **forwarder's** signature on the envelope; the claimed origin is third-hand testimony and **CANNOT** be verified. A malicious or careless forwarder may attribute fabricated content to any public key. This is structurally equivalent to a screenshot and is the only model compatible with cross-conversation forwarding under zero-knowledge E2E (the source conversation's symmetric key MUST NOT travel with the forwarded content). Client UI **MUST** visually distinguish forwarded content from native (signed-by-claimed-author) content — e.g., a distinct envelope, badge, or "forwarded" decoration — so users can apply appropriate skepticism. Treating forwarded text as natively-attributable would mislead the user about what the cryptographic guarantees actually cover.

### 6.5 Cycle handling

`A.replyTo(B); B.replyTo(A)` is permissible at the protocol level. Display-time render-loop protection is a client concern (visited-set + max depth, e.g. 8). Spec leaves it client-defined.

### 6.6 Storage implications

- rschat: zero schema change. The new fields are inside the encrypted blob.
- tkmChat local Drift DB: already has `replyToSignature` column (`tables.dart:97`). Needs a parallel column for reactions (e.g. `reaction_target_signature`) and a reaction kind discriminator. Or a separate `reactions` table keyed by `(target_signature, sender_pk)` for dedup. Pin state needs a single-row-per-conversation `pinned_messages` table or a `pinned_signature` column on `conversations`.
- shell: in-memory display only; no persistent storage.
- **Dead-data finding (separate cleanup, not blocking):** `rschat.users_to_conversations.conversation_role` is currently unread by every code path (server never authorizes against it, never sends it in any response bean). Clients derive their own role view from `creation_request.from` (Flutter: `conversation_sync_service.dart:361`). Either remove the column and its lookup table `conversation_roles` in a future schema migration, or wire it to a real authorization model — both options are out of scope for this branch. Flagged so the column does not silently drift further out of sync with client-derived state.

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
| 2 | `text_message` for reactions | **SHOULD be `""` (empty string)** (RFC 2119). Senders SHOULD emit `""`; `null` or absent is equivalent on receive. Non-empty text on a reaction is renderable but non-canonical — receivers MUST NOT discard. Full normative text in §3.2. |
| 3 | Where to declare inline constants | Move existing constants from `shell/utils/ThumbnailService.java` to a new `Messages/.../chat/attachment/InlineContentLimits.java`. See §11. |
| 4 | Notify-on-reply / notify-on-reaction mechanism | **Protocol SUPPORTS, does NOT FORCE.** Routing relies on the existing plaintext `cited_users` field in `BasicMessageSignedContentBean`. Whether a client populates it for replies/reactions is a per-client policy with a per-conversation override and a global default. Full normative paragraph in §3.3. |
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

### 11.3 Constants — protocol-level (defined under this branch)

The numeric inline limits and the reaction MIME whitelist are now formally declared in
`Messages/src/main/java/io/takamaka/messages/chat/attachment/InlineContentLimits.java`
(this branch). Authoritative values:

| Constant | Value | Meaning |
|---|---|---|
| `InlineContentLimits.MAX_INLINE_BYTES` | `51200` (= 50 × 1024) | Maximum byte length of `base64StandardDecode(preview)` |
| `InlineContentLimits.MAX_THUMBNAIL_DIMENSION_PX` | `256` | Maximum width AND height (pixels) for inline images |
| `InlineContentLimits.INLINE_IMAGE_MIME_FAMILY` | `"image/"` | Required `mediaType` prefix for inline images |
| `InlineContentLimits.REACTION_ALLOWED_IMAGE_MIMES` | `{image/png, image/jpeg, image/webp, image/gif}` | Closed MIME whitelist for reaction inline-image payloads |

The Javadoc on `InlineContentLimits` records the provenance (values were previously declared in `shell/.../utils/ThumbnailService.java` as `MAX_INLINE_SIZE` and `MAX_THUMBNAIL_SIZE`), the server-blindness caveat (the server cannot enforce these — they exist to keep clients consistent), and the cross-platform parity requirement.

**Migration tasks (this branch establishes only the Java side):**

1. **shell:** update `io.takamaka.manage.shell.utils.ThumbnailService` to reference `InlineContentLimits.MAX_INLINE_BYTES` / `MAX_THUMBNAIL_DIMENSION_PX` instead of its own `MAX_INLINE_SIZE` / `MAX_THUMBNAIL_SIZE`. Delete the local copies.
2. **rsclient-flutter:** create `lib/src/beans/attachment/inline_content_limits.dart` mirroring the Java values:
   ```dart
   class InlineContentLimits {
     static const int maxInlineBytes = 50 * 1024;
     static const int maxThumbnailDimensionPx = 256;
     static const String inlineImageMimeFamily = 'image/';
     static const Set<String> reactionAllowedImageMimes = {
       'image/png', 'image/jpeg', 'image/webp', 'image/gif',
     };
     static bool isReactionImageMimeAllowed(String? mediaType) =>
         mediaType != null &&
         reactionAllowedImageMimes.contains(mediaType.toLowerCase());
     InlineContentLimits._();
   }
   ```
3. **rschat-docs / E2E protocol doc:** bump to v1.6 and reference these constants in the file-attachment section.

### 11.4 Inline MIME whitelists (normative)

Two whitelists coexist, distinguished by the action context:

**Reaction-payload whitelist** — closed, normative (`InlineContentLimits.REACTION_ALLOWED_IMAGE_MIMES`):
```
image/png, image/jpeg, image/webp, image/gif
```
Used iff `action == "reaction"` and the reaction carries an inline-image payload. Animated `image/webp` and `image/gif` are permitted iff they satisfy `MAX_INLINE_BYTES` and `MAX_THUMBNAIL_DIMENSION_PX`. Any other MIME — including `image/bmp`, `image/tiff`, `image/jpg` (the non-canonical synonym for `image/jpeg`), or non-image families — MUST be rejected with decoration code `INLINE_MIME_VIOLATION`.

**General inline-image whitelist** — broader, for non-reaction inline content (e.g. sticker packs, regular small-image attachments inlined for round-trip efficiency). Currently `ThumbnailService.IMAGE_MIME_TYPES`:
```
image/png, image/jpeg, image/jpg, image/gif, image/webp, image/bmp, image/tiff
```
This is **not** the reaction whitelist. Reactions are deliberately narrower so that emoji/sticker payloads cannot smuggle in archival or platform-specific image formats.

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

| Action | `targets` cardinality | `text_message` | `attached_media` | Authorization | Notes |
|---|---|---|---|---|---|
| `reply` | 1 | non-empty (typical) | optional | none | §3-§5 |
| `reaction` | 1 | `""` (SHOULD) | exactly 1 (emoji/sticker/inline-image) | none | §3-§5, §11 |
| `reaction_remove` | 1 | `""` | absent / null | self-author (envelope.from == prior reaction's from) | §12.2 |
| `edit` | 1 | new text | new media (optional) | self-author (envelope.from == parent's from) | §12.3 |
| `redact` | 1 | optional reason ("" allowed) | absent | self-author (envelope.from == parent's from) | §12.4 |
| `pin` | 1 | optional reason, SHOULD ≤ 200 chars | MUST be null | creator-only (envelope.from == creation_request.from) | §5.5, §12.5 |
| `unpin` | **0** (null / empty / absent) | SHOULD be `""` | MUST be null | creator-only (same check) | §5.5, §12.5 |
| `forward` | **0 or 1** (claimed-origin PK; 0 = anonymous) | re-encoded forwarded text | re-encrypted attachments under target conversation key | none | §12.10. Targets type = **public key**, not signature. Origin claim is unverifiable. |
| `share_history` | **0** | optional relayer note | optional (rare) | none | §12.11. Carries `original_message` = the encrypted original envelope, signature preserved. **Verifiable** original-sender attribution. In-conversation only. |

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

### 12.5 `pin` / `unpin` — single-slot model

**Purpose.** Designate **one** message as the conversation's pinned message. Each conversation has at most one pinned message at any time. Pinning a new message overwrites the previous pin; `unpin` clears the slot entirely.

**Semantics.**

`pin`:
- `targets[0]` = signature of the message to pin (Ed25519 envelope signature, matching `^[A-Za-z0-9_-]{86}\.\.$`).
- `text_message` = optional pin reason. SHOULD be ≤ 200 chars. Default `""`.
- `attached_media` MUST be null.

`unpin`:
- `targets` MUST be null, empty list, or absent — all equivalent. There is no target to reference because the slot identity is the conversation itself (carried in the outer envelope's `conversation_hash_name`).
- `text_message` SHOULD be `""`.
- `attached_media` MUST be null.

**Receiver validation.**

1. Standard validation per §4.2, with the per-action cardinality applied (`pin` → exactly 1, `unpin` → 0).
2. **Authorization (creator-only).** Before applying any state change, verify `envelope.from == creation_request.from` for this conversation, where `creation_request.from` is the `from` field of the conversation's decrypted `CreateConversationRequestBean`. The value lives in already-decrypted client state — no server call, no role table consultation.

   Non-creator emissions are rejected with decoration `INVALID <pin|unpin> (not conversation creator)`. The pin slot is **not** updated. The parent envelope's `text_message` (if any) still renders normally — failed authorization at the action layer never discards user content.

3. For `pin` only: target signature regex match + same-conversation-membership lookup (best-effort, see §4.2 step 4 / §5.5).

**Aggregation.** See §5.5 for the normative state-machine. Summary: a single-slot table `pinned: conversation_hash → pinned_message_signature` updated by `(reception_timestamp, envelope_signature)`-ordered events; `pin` overwrites, `unpin` clears.

**Client behaviour.**

- **Pin slot state** is derived from the conversation's action history per §5.5 — not server-side state.
- **Display:** if the slot holds a signature and the parent message is in the local cache → render the pinned message in a client-defined visual affordance (top strip, sidebar, hover, etc.). If the parent is not in the local cache → render the slot with a broken-reference decoration; do not clear the slot.
- **Empty state:** no pinned-message affordance is rendered. SHOULD NOT show empty placeholders.
- **Layout / placement / interactions** (jump-to-message, animation, hover preview, dismiss UI) are entirely client-defined and out of scope.

**Authorization model is intentionally narrow.** "Creator-only" was chosen because the current protocol has no mechanism to promote, demote, or transfer admin status — see the dead-data finding for `users_to_conversations.conversation_role` in §6.6. The creator of a conversation is its sole pin authority for the conversation's lifetime. If a richer admin model is added in a future protocol revision (`promote_admin` / `demote_admin` actions with consensus / inheritance rules), the pin authorization MAY widen to "creator or admin" without a wire change — only the validator's authorization check would update.

**Why design now.** Pin is the third-most-common conversation feature after reply and react. The single-slot model is the simplest possible design that delivers the user-visible feature, fits the existing `(action, targets)` shape, and reuses the §5.4 aggregation machinery wholesale. Cost: zero new wire fields, zero new endpoints, one PK equality check.

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

### 12.7a Authorization patterns — name the three

Every new action picks exactly one of three authorization patterns. The spec records them here so future actions don't reinvent the check.

| Pattern | Check | Currently used by | Notes |
|---|---|---|---|
| **No check** | any conversation participant may emit | `reply`, `reaction`, `forward`, `share_history` | Validator does only structural validation; no authorization step. |
| **Self-author check** | `envelope.from == lookup(target_signature).from` | `edit`, `redact`, `reaction_remove` | Requires the target message to be in the local cache for the check to succeed. If the target is unknown, action is held in a deferred-validation queue or rejected with a "cannot verify authorship" decoration (client choice). |
| **Conversation-creator check** | `envelope.from == creation_request.from` | `pin`, `unpin` | The `creation_request` is the decrypted `CreateConversationRequestBean` for this conversation, present in client state as soon as the conversation is set up. Check is deterministic, local, and requires no server query. |

**Why these three and no others.** The protocol currently has no concept of an "admin set" or any other persistent role state. The only identity-level facts a client can verify locally are: (a) who sent THIS message (envelope.from), (b) who sent the TARGET message (envelope.from of the lookup), (c) who CREATED the conversation (from of the decrypted creation request). Every authorization decision must compose from these three. Until a future protocol revision introduces explicit role-management actions, no fourth pattern is possible.

**Server-side enforcement: none of the above.** All authorization checks are client-side. A non-cooperating client could ignore an authorization failure and render the action anyway (e.g. show a non-creator's pin in its own UI). Same caveat as `edit` and `redact`: directives that depend on cooperating implementations.

### 12.8 Cross-action interactions

| Scenario | Behaviour |
|---|---|
| Edit of a reply | Standard edit. The reply pointer is part of the edited message body. To preserve the reply linkage, the edited message should keep the same `action="reply"` + same `targets`. |
| Reply to an edited message | The reply's `targets[0]` is the **original** signature, not any edit. Clients resolve through the edit chain when displaying. |
| Reply to a redacted message | Reply is delivered; receiver renders "valid reply to [deleted message]". |
| React to a redacted message | Reaction is delivered; receiver renders the reaction with `[deleted message]` as the target preview. |
| Edit of a reaction | **Forbidden by spec.** Reactions are immutable; use `reaction_remove` + new `reaction`. |
| Redact of a reaction | Allowed (it's just deleting your own message). Equivalent in effect to `reaction_remove`. Clients SHOULD treat both as the same delete operation. |
| Pin of a redacted message | Pin is permitted protocol-wise. Slot updates to that signature. Display: client MAY render the pinned slot as `[pinned message deleted]` or MAY auto-clear from view (slot state unchanged). |
| Pin of an edited message | `targets[0]` is the original signature, never an edit. Display resolution walks the edit chain to render the latest content. |
| Pin pointing at a message not in local cache | Slot updates to that signature; display renders `[pinned message not in cache]`. Slot resolves when the parent arrives. |
| Multiple sequential pins | Single-slot model: latest by `(reception_timestamp, signature)` wins. No list of pinned messages exists. |
| Pin followed by edit of pinned message | The edit changes the displayed content of the pinned message (clients resolve via the edit chain). The pin slot itself is untouched. |
| `unpin` arriving before any `pin` | No-op. Slot was already empty; remains empty. |
| Concurrent `pin` and `unpin` from creator | Latest by `(reception_timestamp, signature)` tuple wins, exactly as §5.5 specifies. |
| Pin attempt by non-creator | Rejected with decoration `INVALID pin (not conversation creator)`. Slot unchanged. Parent body still renders. |
| React to a forwarded message | Standard reaction targeting the **forwarder's envelope signature** (the forward is a new message in the destination conversation). Source-conversation reactions on the original NEVER reach the destination; they aren't propagated through `fw_content`. Reaction anchoring per §5.4.1. |
| Reply to a forwarded message | Standard reply targeting the **forwarder's envelope signature**. Source-conversation replies on the original do not propagate; the destination receiver cannot resolve source signatures. Reply anchoring per §5.4.1. |
| Reactions on a `share_history`-delivered original | Pre-share reactions remain anchored to the embedded original's signature (per §5.4 aggregation), NOT to the share wrapper. Clients MUST NOT render pre-share reactions as decorations on the share's rendering — the reactor reacted in the original's context, not the late-receiver's context. Post-share reactions target whichever signature the reactor chose (typically the original's signature, since that's the persistent identifier the share carries). See §5.4.1 for the full anchoring + rendering rule. |
| Replies on a `share_history`-delivered original | Same anchoring rule as reactions. Pre-share replies remain anchored to the embedded original; clients MUST NOT decorate the share with them. Post-share replies typically target the original's signature; clients can render them as reply-to-original threads. |
| Reacting/replying to the share wrapper itself | Standard. The wrapper has its own signature; reactions/replies target that signature and anchor to it. Independent of any reactions on the embedded original. |
| Edit of a forwarded message | Forwarder may edit their own forward (self-author check on the forward envelope). Does not affect the source. |
| Redact of a forwarded message | Forwarder may redact their own forward. Does not affect the source. |
| Pin of a forwarded message | Creator may pin a forward. Renders in the pinned slot with the forward decoration intact. |
| Forward of a redacted message | The forwarder's local cache determines whether the tombstone or the pre-redaction content is forwarded. Both are protocol-valid. Forwarding pre-redaction content against the original author's intent is a client/user ethics concern, not a protocol-enforceable invariant. |
| Forward of a forward | Allowed. **Recursive via `fw_content`.** The re-forward's `fw_content` is the previous forward's full `BasicMessageEncryptedContentBean` (including its own `fw_content`, recursively, up to the hard depth cap of 10). Each hop carries a claimed-origin PK in `targets[0]` (or empty for anonymous); the chain of claimed origins and decrypted contents accumulates. Clients render the chain with informational decorations, not alarmist UI (§12.10.4). |
| Forward to self (e.g., a personal notes conversation) | Allowed. Trivially handled. |
| Forward with empty `targets` | Allowed (cardinality 0 path). Renders as anonymous forward without origin attribution. |
| Forward with `targets[0]` equal to the forwarder's own PK | Allowed. UI renders normally; forwarder is attributing the content to themselves. |
| Forwarded message decrypts but `targets[0]` PK is unknown to recipient | Not a validation failure. Client renders the forward badge with a truncated PK label or "(unknown user)". |
| Forwarding a message with `attached_media` containing regular (server-uploaded) attachments | Forwarder MUST re-encrypt and re-upload under the destination conversation's key (§12.10.1). Reusing the original `encrypted_file_hash` is forbidden. |
| Forwarding a message with `attached_media` containing inline (`isTheObject=true`) content | Forwarder re-encodes the placeholder for the destination message; the bytes travel inside the new encrypted body. `unencrypted_content_hash` MUST be recomputed from the new placeholder's preview. |
| `share_history` of a previously-redacted message | The relayer's local cache may hold a pre-redact copy. Replaying it carries the original signed envelope; the recipient sees verified original content. This is the same honest-implementation issue as forward-of-redacted: the protocol cannot enforce cooperating-clients-only behaviour. |
| `share_history` of an edited message | The relayer shares whatever they have locally — either the original or an edit. The receiver verifies the embedded inner signature and renders the embedded content. Edit chain resolution from §12.3 still applies if the receiver later receives subsequent edits. |
| `share_history` whose embedded `conversation_hash_name` differs from the outer | Rejected. Cross-conversation `share_history` is disallowed: the embedded content was encrypted under a different key, the recipient cannot decrypt it, and the use case is meaningless. Decoration `INVALID share_history (cross-conversation)`. Outer wrapper still renders. |
| `share_history` whose embedded inner signature does not verify | Decoration `INVALID share_history (inner signature)`. Outer wrapper still renders. |
| Nested `share_history` (inner decrypted bean itself has `action="share_history"`) | **Rejected.** The recovery use case does not need forensic chain tracking. Decoration `INVALID share_history (nested share_history)`. Outer wrapper still renders. |
| `share_history` of a `forward` | Allowed. The embedded envelope IS the forward; the relayer is helping a late receiver recover a forward they missed. Recipient verifies the forwarder's signature (inner) and then renders the forward's `fw_content` chain as normal. |
| `forward` of a `share_history` | Allowed. The forwarder is taking a relayed message and carrying its content to another conversation. The `share_history` semantics dissolve into a normal `forward` — the destination recipient sees a forward, not a history-share (they were never in the source conversation). |
| Reply to a `share_history` message | Standard reply; targets the relayer's wrapper signature in this conversation. To reply to the original sender directly, the recipient should target the inner envelope's signature (which now lives in their cache via the relay) — but this is the same as replying to any verified message they have a signature for. |
| React to a `share_history` message | Standard reaction; targets the relayer's wrapper signature. |

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

**Pin / unpin** (single-slot model, §5.5 / §12.5) ships in 1.5.0 alongside reply / reaction. Creator-only authorization adds zero new wire surface — the check is a PK equality against already-decrypted `creation_request.from`. No `users_to_conversations.conversation_role` consultation; that column is dead data (see §6.6).

**Forward** (§12.10) ships in 1.5.0 alongside the rest. No new wire fields. The forward action graduates from §12.6 (deferred) once the design accepts that the original signature and original encrypted blob are not transmitted — only a re-encoded body under the destination conversation's key, plus an **unverifiable claimed-origin public key** in `targets[0]`.

**Share-history** (§12.11) ships in 1.5.0 as a structurally distinct feature from forward. Adds one new bean field, `original_message`, carrying the encrypted original `BasicMessageRequestBean` intact for in-conversation late-receiver recovery. The original sender's Ed25519 signature is preserved end-to-end and **cryptographically verifiable** by the recipient (who shares the conversation key). Different trust model, different UI treatment, different field — not a variant of forward.

### 12.10 `forward` — re-encoded content with claimed origin

#### TL;DR — Why forward is structured this way

The Takamaka chat protocol gives every conversation its own AES-256 symmetric key, and that key never leaves the conversation. The participants of conversation A share key K_A; the participants of conversation B share key K_B. Encrypted message bodies, attachment ciphertexts, and stream-encryption descriptors are all bound to the key of the conversation they were produced in.

Forwarding is the awkward case this design implies. If Alice wants to take a message from conversation A and show it to Bob in conversation B, the obvious "preserve the original" approach hits an immovable wall: Bob does not have K_A and never can. Handing him the encrypted blob would be useless to him. Handing him K_A would punch a hole in conversation A's zero-knowledge property — every message ever sent in A, past and future, would become readable to him. Neither is acceptable. So whatever Alice forwards has to be **re-encoded under K_B**: she decrypts the message in her own client, re-encrypts the content with the destination conversation's key, and signs the new envelope with her own identity. By the time the message reaches Bob, the original signature is gone — replaced by Alice's signature on the new envelope. The original ciphertext is gone — replaced by fresh ciphertext under K_B. The original `encrypted_file_hash` is gone — Alice has to re-upload any attachments she's bringing along, encrypted under K_B with fresh random IV and salt.

What survives the trip is only the plaintext that Alice chose to copy across, plus a `targets[0]` field carrying the public key Alice **claims** the content originally came from. The claim is unverifiable. There is no cryptographic operation Bob can run that proves the content actually came from the person Alice names — because none of the original signed material reached him. Alice is signing an attestation: "I am forwarding this content, and I claim it originally came from X." Bob verifies that Alice signed it. He cannot verify the claim about X. This is the same trust model a screenshot has: Bob trusts Alice to be honest about what she's quoting and who she's quoting it from, exactly as he would for a screenshot. The protocol is honest about this rather than dressing it up to look like verified provenance.

That honesty is also why the protocol stops here and resists enrichment. Every "improvement" that would make a forward look more authoritative — preserving the original signature, attaching cryptographic provenance, embedding a verifier-side proof — would either require sharing source-conversation material (eroding zero-knowledge) or would create a visual affordance that misleads users about what the cryptography actually guarantees. A forward that looks signed-by-the-claimed-author when it isn't would be worse than the current honest design, because users would extend it trust the cryptography hasn't earned. So the protocol's stance is: forward is useful, sometimes legitimate, structurally equivalent to a screenshot, and that is **all** it is. If you need more than a screenshot — verified attribution, preserved reply chains, end-to-end original timestamps — the answer is not to extend forward. The answer is to compose the content natively in the destination conversation (the people who should be cryptographically attributable can author signed messages themselves) or to use a separate out-of-band channel.

The visible signs of this design are exactly what you would predict. `targets[0]` carries a public key rather than a message signature, because nothing about the original message survives that would resolve to anything. Cardinality is zero-or-one: forwards may either claim an origin or stay anonymous, both being honest forms of a screenshot. Chain provenance is flat — a forward of a forward doesn't carry the chain, because there is no honest way to attest to a chain you can't verify either. Clients are required to visually distinguish forwarded content from natively-signed messages, because rendering them identically would be the UI lie that this design exists to prevent. And the protocol carries no semantic context from the original message — if the source was a reply or a reaction or an edit, that fact is lost, because trying to reconstruct it in the destination would either require the original signature (gone) or would let the forwarder fabricate context with no way to check. The design is minimal on purpose, and the minimum is exactly the screenshot equivalent: this content, signed by whoever forwarded it, attributed to whoever the forwarder claims, take it for what it's worth.

---

**Purpose.** Re-share content from one conversation into another (or back into the same conversation) without leaking the source conversation's symmetric key. The forwarded content is re-encoded by the forwarder under the destination conversation's key, signed by the forwarder's identity, and decorated with an **unverifiable claim** about the original author.

**Threat-model framing.** Forward is **structurally equivalent to a screenshot**. The forwarder cryptographically attests "I am forwarding this content and I claim it originally came from public-key X" — but the recipient has no cryptographic path to verify either the content authenticity or the origin claim. This is the only model compatible with cross-conversation E2E. See §6.4 for the full security disclosure.

**Wire shape.**
- `action = "forward"`
- `targets` cardinality is **0 or 1**.
  - `targets.size() == 1` → `targets[0]` is the **claimed-origin public key** of the message being forwarded. Format = Ed25519 public key (Base64URL with `.` padding), regex `^[A-Za-z0-9_-]{43}\.$`.
  - `targets.size() == 0` (null, empty list, or absent) → **anonymous forward**: no origin attribution. Renders without an attribution name.
- `text_message` carries the forwarded text. MAY be empty if the forwarded content was attachment-only.
- `attached_media` MAY carry re-encrypted attachments (§12.10.1).

**Authorization.** None (Pattern 1, §12.7a). Any conversation participant may forward to any conversation they are in.

#### 12.10.1 Attachment handling — normative

**Attachments MUST be re-encrypted and re-uploaded by the forwarder under the destination conversation's encryption parameters.** This rule has no exceptions.

Concretely, when forwarding a message that contains `attached_media`:

1. **Inline attachments** (`isTheObject == true`): the forwarder already has the plaintext bytes in their decrypted message cache. They re-encode the placeholder under the destination message's parameters (the inline body remains inline; just present it again as part of the new message's `attached_media`). The new placeholder's `unencrypted_content_hash` is recomputed (it may equal the original since the plaintext is identical, but it MUST be computed from the new placeholder's `preview` bytes — clients MUST NOT copy the field blindly).

2. **Regular (server-uploaded) attachments** (`isTheObject == false`): the forwarder re-encrypts the plaintext file under a **fresh** `StreamEncryptedDescriptor` (new random salt + IV) using the destination conversation's symmetric key, then uploads the new ciphertext via the standard `submitattachment` endpoint, then references the new `encrypted_file_hash` in the forward message's `attached_media`. The forwarder MUST NOT:
   - Reference the original `encrypted_file_hash` (the destination's participants do not have the source conversation's key and cannot decrypt the original blob).
   - Share the source conversation's symmetric key in any form.
   - Share the original `StreamEncryptedDescriptor` (it binds to the source key).

3. **Cost disclosure.** Forwarding a 1 GB video means uploading 1 GB again. There is no protocol-level deduplication path that preserves zero-knowledge — fresh per-upload IV/salt means even identical plaintexts produce different `encrypted_file_hash` values. Client UIs SHOULD warn the user before forwarding large attachments.

#### 12.10.2 Receiver validation

1. Standard pipeline (§4.2 steps 1–3) with cardinality `0 or 1` and public-key regex for step 3.
2. Step 4 (same-conversation lookup) is **skipped** for `forward` — the target is a PK, not a signature. Clients SHOULD try to resolve the PK against their local contacts / registered-users cache to render a display name. Resolution failure is normal: the PK may not be in any conversation the recipient is in. Fall back to a truncated PK label (e.g., "Forwarded from `abc...xyz.`") or "(unknown user)".
3. Step 5 (authorization) is skipped — no check.

A forward whose `targets[0]` is the forwarder's own PK (self-attribution) is permitted; clients render normally.

#### 12.10.3 Lossy semantics — what is NOT carried (intentional, see §12.10.5)

**Wrapper vs inner — what is preserved.** Cross-conversation forward preserves the **decrypted inner content** of the original (the `BasicMessageEncryptedContentBean`) inside `fw_content` — text, media, **action**, **targets**, and recursive `fw_content` if the original was itself a forward. It does **not** preserve the original signed wrapper or its `cited_users` (which lived at the outer signed-content level). The preserved inner action/targets remain structurally visible (e.g. "this was originally a reply") but their targets are signatures from the **source** conversation, so they will almost always be broken-reference in the destination. Clients SHOULD render the embedded references with the same broken-reference decoration used elsewhere (§4.2 step 4) — the structural signal is informative and the forwarder explicitly vouched for it; silently stripping it would underclaim.

**Reactions and replies do not travel with the forward.** Source-conversation reactions and replies on the original message never reach the destination conversation; they were independent signed messages in the source's history, not part of the original's content. Reactions and replies in the destination conversation target the **forwarder's** envelope signature (the forward IS a new message in the destination, with its own signature). The full rule is in §5.4.1.

By design, a forward **does not preserve**:

- The original signature (the forwarded envelope was signed by the original author; the new envelope is signed by the forwarder).
- The original encrypted blob or its hash.
- The source conversation's symmetric key (MUST NOT be transmitted).
- The original message's `action` / `targets` context. If the source message was a reply, a reaction, or a redact, that semantic does not transfer to the forward. The forwarder is creating a new top-level message with `action="forward"`; any original-action context must be encoded into `text_message` if the forwarder wants to preserve it (purely informational, client-formatted).
- **Chain provenance beyond one hop (FLAT model — normative).** Each forward carries **exactly zero or one** claimed-origin PK. The wire protocol does **not** preserve a chain of forwarders. If the forwarder is themselves forwarding a previously-forwarded message, they pick one PK to attribute to (their discretion — typically either the original-claimed origin or the previous forwarder) and emit a fresh `action="forward"`. The result is a single attribution rendered in the destination conversation. Clients MAY display a "forwarded multiple times" UX heuristic by detecting that the source they're re-forwarding is itself an `action="forward"` in their local cache; this is purely cosmetic and does not propagate over the wire to the next recipient.

#### 12.10.4 Client UI — normative visual distinction

Clients **MUST** visually distinguish forwarded content from native (signed-by-claimed-author) content. The intent is to prevent users from mistaking a forward for cryptographically-attributed content.

Acceptable visual distinctions include (any one suffices, all client-defined):
- A "forwarded" badge or icon adjacent to the claimed-origin display name.
- A distinct envelope, border, or background tint.
- A header line like `forwarded from <display_name>` above the body.
- For anonymous forwards (targets empty), the same badge without an attribution name.

What clients **MUST NOT** do: render the forwarded content under the claimed origin's display name with the same visual treatment as a natively-signed message. That would mislead the user about the cryptographic guarantees and is the kind of UI affordance that turns a screenshot-equivalent into apparent attribution.

#### 12.10.5 Design stance — forward is deliberately minimal

The lossy properties in §12.10.3 are **not limitations awaiting future improvements**. They are intentional design choices and the spec records this stance so future revisions resist enrichment pressure.

The protocol's position:

- **`forward` exists because it is sometimes useful and sometimes legitimate** — sharing a news snippet, a code fragment, an instruction, a piece of context from elsewhere. Removing it entirely would be over-correction.
- **The protocol does not incentivize forwarding over native composition.** A forward is structurally a screenshot, no more. Composing the same content natively in the destination conversation — where the author signs their own envelope and the recipients verify it — is always the cryptographically stronger option, and the spec prefers it.
- **The protocol's answer to "I want richer forwards" is "take a screenshot."** A screenshot is unverifiable, lossy, and a deliberate user act — exactly the properties forward has. Anything the protocol adds beyond this baseline either misleads recipients about authenticity or pressures the source conversation's zero-knowledge property.

Two design-rule corollaries that future contributors should apply when proposals arrive:

1. **Trust-hygiene corollary.** Any change that makes forwards *look* more authoritative — preserving the original signature, embedding a verifier-side proof, attaching cryptographic provenance, anything that strengthens the apparent attribution — is rejected on the grounds that it misleads users about what the cryptographic guarantees actually cover.
2. **Cross-conversation E2E corollary.** Any change that requires sharing source-conversation material — the symmetric key, the original `StreamEncryptedDescriptor`, the original `encrypted_file_hash` as a resolvable reference — is rejected because it erodes the zero-knowledge property of the source conversation. The bright-line rule (§12.10.1) "the source's symmetric key MUST NOT travel with the forwarded content" is non-negotiable.

If a use case genuinely needs more than what `forward` provides — verified attribution, preserved reply chains, end-to-end original timestamps — the answer is **not** to extend forward. Compose natively (the participants can author signed messages in the destination conversation) or use an out-of-band mechanism (screenshot, copy/paste, file share, separate conversation).

This stance is recorded so reviewers can reject future "enrichment" proposals with a citation rather than rerunning the design conversation.

---

### 12.11 `share_history` — in-conversation rebroadcast of an encrypted original

#### TL;DR — Why share_history exists and how it differs from forward

The Takamaka chat server bounds message retention. By default a conversation's `server_messages_liveness` is 7 days (see `rschat ConversationService.java:145`); after that, the server has discarded the original encrypted blob. Participants who were offline during that window, restored from a backup that predates it, or lost their local cache have **no path** to recover the message through the server. The only remaining source is another participant — someone who still has the message in local cache — relaying it back.

Because every participant of the same conversation shares the same symmetric key K, the relayed copy needs none of the re-encoding that cross-conversation forward requires. The original signed envelope travels intact: same encrypted content blob, same Ed25519 signature by the original sender, same `encrypted_file_hash` references (still resolvable on the server if attachment retention allows, or stale if not). The receiving participant decrypts the embedded content using the same conversation key they were going to use for native messages, verifies the original sender's signature against the embedded `from` PK, and obtains **full cryptographic provenance** — end-to-end original-author authentication, not a claim.

This is the inverse of forward's trust story. Forward delivers an unverifiable claim across conversation boundaries; share_history delivers a verifiable original within the same conversation. The UI treatment matches the cryptography: forwards MUST be visually distinguished from native content because their authentication chain is short and ends at the forwarder. Share-history can be rendered with the original sender's verified attribution — the cryptography backs that attribution. The only honest acknowledgement to add to the UI is a small "shared by [relayer]" badge indicating the delivery path; it is informational, not skeptical.

Share-history is a participant-courtesy mechanism, not a durability guarantee. If everyone with a local copy lost their cache, share_history cannot help — the data is gone. It also does not restore the **original `reception_timestamp`**: the share gets a fresh server timestamp at re-delivery, so the message appears in conversation history at the share time rather than the original time. These are accepted limitations of going through the standard `messages` channel rather than a dedicated history-share endpoint; a future specialized endpoint with batching, compression, and timestamp preservation can be added without breaking this design.

The feature is deliberately not a chain. There is no "share_history of a share_history." A second-hop late receiver should be served by sharing the **original** directly, not by relaying a chain of relays. Forensic data about who has shared what to whom is explicitly **not** something this feature collects — see §12.11.3.

#### 12.11.1 Wire shape

- `action = "share_history"`
- `targets` cardinality **0** (MUST be null, empty list, or absent — the embedded envelope self-identifies; there is no signature pointer to target).
- `original_message` MUST be a non-null `BasicMessageRequestBean` — the full original signed envelope (from + signature + signed-content-bean containing conversation_hash, cited_users, and encrypted_content).
- `text_message` MAY carry an optional relayer note ("Sharing this because Bob asked for it"). SHOULD be ≤ 200 chars; not normative.
- `attached_media` MAY carry additional content from the relayer (rare; usually empty). The embedded envelope's own attachments are inside `original_message.basic_message_signed_content_bean.encrypted_content` and travel with it.
- `fw_content` MUST be null. Share-history and cross-conversation forward do not compose at the same level — they are different operations with different keys.
- `re_shared` (optional Boolean). `true` if the relayer themselves received this content via a prior share_history (not directly). `false`/null if the relayer is the original direct receiver doing a first share. Unverifiable trace, informational only. See §12.11.10.

#### 12.11.2 Authorization

None (Pattern 1, §12.7a). Any conversation participant may share history. Limiting to creator-only would defeat the use case: the creator might not be the participant with the local cached copy that's needed.

#### 12.11.3 Why no nested share_history — explicit non-feature

A `share_history` message whose embedded `original_message` itself decrypts to a bean with `action="share_history"` is **rejected** by validation (decoration `INVALID share_history (nested share_history)`, outer wrapper still renders).

The reasoning is positive, not just structural:

- The recovery use case is "deliver the original to a late receiver." A late receiver who eventually receives a relayed copy of *another* relayed copy gains nothing the direct relay wouldn't have given them. They want the original, not a chain.
- Tracking the chain of relayers would create **forensic metadata** about who has been sharing what with whom, including timing. That is information the protocol explicitly does not need and should not collect. Forwards carry chain metadata as a deliberate accountability signal (§12.10 — recursive `fw_content`); share_history is the opposite kind of operation and has no equivalent need.
- Without the nesting prohibition, a relayer could construct arbitrarily-deep nested envelopes simply by replaying their cached copies of previous shares. Bandwidth and parsing cost grow with no benefit.

The rule is therefore: cardinality of the chain is 1 (the relayer's wrapper + one embedded original), enforced by validation. Anyone who wants more receivers to get the same content sends multiple `share_history` messages — each is independent, each verifiable, none chained.

#### 12.11.4 Receiver validation pipeline

For an incoming message with `action="share_history"`, after the standard outer-envelope verification:

1. Verify `original_message != null`. If null, decoration `INVALID share_history (missing original_message)`, body still renders.
2. **Verify the embedded inner signature** against `original_message.from`. Use the same Ed25519 verification machinery as the outer envelope. Mismatch → decoration `INVALID share_history (inner signature)`, outer wrapper still renders.
3. **Verify same-conversation invariant.** `original_message.basic_message_signed_content_bean.conversation_hash_name` MUST equal the outer message's `conversation_hash_name`. Mismatch → decoration `INVALID share_history (cross-conversation)`. (Cross-conversation share_history is meaningless: the embedded content is encrypted under a different key and cannot be decrypted by the receiver.)
4. **Decrypt the inner `encrypted_content`** using the same conversation key the receiver already holds.
5. **Reject nested share_history.** If the decrypted inner `BasicMessageEncryptedContentBean` has `action="share_history"`, decoration `INVALID share_history (nested share_history)`, outer wrapper still renders.
6. **Render the inner content** as if it were a native message from the original sender — same trust treatment, same attribution. Add an informational "shared by [relayer]" badge or equivalent indicator of the delivery path.

#### 12.11.5 Attachment behavior

Because share_history operates within a single conversation, attachment references in the embedded `original_message` MUST NOT be modified. The original `encrypted_file_hash`, the original `sed`, and the original `unencrypted_content_hash` travel unchanged. The receiver, sharing the same conversation key, can fetch and decrypt the attachment from the server using the unchanged `encrypted_file_hash` — exactly as they would have if they had received the original directly.

Caveat: server-side attachment retention is also bounded. If the encrypted blob has been garbage-collected from `verified_attachment`, the receiver's fetch will fail. The UI renders the embedded attachment with a `[attachment no longer available]` decoration. The signed envelope's metadata (filename, size, hashes) is still preserved and visible.

#### 12.11.6 Lossy semantics — what is NOT preserved

By design:

- **Original `reception_timestamp`.** The share gets a fresh server-side timestamp. The original's timestamp existed only in `RetrieveMessagesResponseBean.receptionTimestamp` from the original delivery, which is server-layer metadata not part of the signed envelope. The relayer cannot reconstruct it cryptographically. Recipients see the message at the share time and apply contextual judgment.
- **Original conversation-history position.** The share appears in the conversation timeline at the share time, not the original send time. Reactions that aggregated against the original signature still aggregate correctly (§5.4 uses the original envelope's signature, which is preserved).
- **Server-side attachment availability** (see §12.11.5).

#### 12.11.7 What IS preserved (and why this is different from forward)

- **Original sender's signature** — verifiable.
- **Original `encrypted_content`** — decryptable with the same key.
- **Original message structure**, including its own `action` and `targets`. If the original was a reply, a reaction, or an edit, the share recipient sees the embedded `action`/`targets` and resolves them against their local cache normally.
- **Original sender's identity** — cryptographically attested, not claimed.
- **Original `cited_users`** — preserved at the outer signed-content level of `original_message` (the embedded envelope). These were the original sender's mention list and reference conversation members; they remain meaningful in the same conversation. Server fan-out for FCM does not re-fire on them (the server only reads the **current** outer wrapper's `cited_users`, which belongs to the relayer's intent for this hop).
- **Inner action/targets references** — preserved AND **typically resolvable** because the receiver is in the same conversation as the original. If the original was a reply, the receiver can typically resolve `targets[0]` to the parent message in their local cache and render the reply with its parent context. This is the key contrast with forward, where the same action/targets are preserved but unresolvable.

**Reactions and replies do not bind to the share wrapper.** Pre-existing reactions and replies on the embedded original remain anchored to the original's signature, not to the share's wrapper signature. A reactor reacted to the original in its conversational context at their time; a late receiver obtaining the original via a share at a later time sees it in a different context. Clients MUST NOT render pre-existing reactions/replies (those with `reception_timestamp` earlier than the share's `reception_timestamp`) as decorations on the share's rendering. The reactions still aggregate against the original target's signature per §5.4 — they just don't decorate the re-delivery envelope. Full rule in §5.4.1.

This is the structural inverse of `forward`: forward preserves the structured inner content but loses the signature and the `cited_users`; share_history preserves the entire signed envelope including `cited_users`. The honest framing is therefore not "structurally a screenshot" (forward's framing in §12.10.5) but "structurally a delayed receipt of an authentic original."

#### 12.11.8 UI guidance — informational, not skeptical

Clients SHOULD render a share_history-delivered message with the **original sender's verified attribution** treated identically to natively-delivered messages, with a small informational badge or icon indicating the delivery path ("shared by Carol", "shared message").

What clients MUST NOT do:

- Apply forward-style "claimed-from" skeptical decoration. The cryptography is real here; suggesting it isn't would underclaim.
- Hide the relayer's identity. The relayer signed the wrapper; their participation in the recovery path is visible and verifiable, and that visibility is part of why this is a courtesy rather than an anonymous broadcast.

What clients MAY do:

- Group multiple share_history-delivered messages of the same logical original into a single "recovered from history" pane, deduplicating by the original envelope's signature.
- Suppress the relayer badge for messages that the receiver had already received natively (the share is redundant).
- Offer the receiver a "request history" affordance that asks other participants to send share_history messages for a date range.

#### 12.11.9 Cost analysis and future optimization

**Bandwidth.** A share roughly doubles per-message size (outer wrapper + inner envelope). Acceptable for small messages, more expensive for attachment-heavy content (though the attachments themselves are not duplicated — only their metadata travels in the embedded bean).

**Crypto.** Two AES-GCM decryptions and two Ed25519 verifications per shared message: outer wrapper + embedded original. Linear in number of shared messages.

**Server-side storage.** Each share is a fresh message in `rschat.messages` with its own row, its own server timestamp, its own retention clock. Rate-limited by the existing `RateLimitService` on per-user message emission.

**Specialized optimization, deferred.** A future dedicated `sharehistory` (or similar) RSocket endpoint could provide batched delivery of multiple originals in a single request, with optional compression. It would not change the trust model — clients would still verify each embedded envelope individually. Out of scope for v1.5.

#### 12.11.10 Canonical re-share — unwrap-and-rewrap (normative)

When a participant who **themselves received** a `share_history` message wants to relay the same content to another late receiver in the same conversation, the canonical operation is **unwrap-and-rewrap**: the relayer discards the previous outer wrapper entirely and emits a fresh outer wrapper around the same preserved inner envelope.

**The operation:**

1. **Verify the incoming envelope.** Validate the previous relayer's signature on the outer wrapper (standard envelope check), then validate the embedded `original_message` per §12.11.4 (inner signature, same-conversation invariant). If either fails, the relayer's local cache should have rejected this content in the first place; do not propagate.
2. **Extract the embedded `BasicMessageRequestBean`** (the original signed envelope from `original_message`). Do **not** modify it. The byte-for-byte preservation of the original envelope is what gives every downstream recipient verifiable original-sender attribution.
3. **Construct a fresh `BasicMessageEncryptedContentBean`** with:
   - `action = "share_history"`
   - `targets = []` (cardinality 0)
   - `original_message =` the verified inner envelope from step 2, unchanged
   - `re_shared = true` (you received this via a prior share_history, so the trace flag indicates not-first-share)
   - `text_message =` optional relayer note for the new recipient (anything you wrote in your local view of the previous relayer's wrapper is **not** propagated — see §12.11.11)
   - all other fields null
4. **Sign and encrypt** the new outer envelope normally. Your envelope is signed by your identity; the encrypted content is under the conversation's symmetric key.
5. **Submit** via the standard `messages` endpoint.

**Why unwrap-and-rewrap, not nest:**

- **Bounded message size.** Every re-share is the size of a first-share. The wire payload does not grow with the relay chain.
- **No forensic chain.** Each downstream recipient sees only the most recent relayer and the original sender. Intermediate relayers are not enumerated. This is the privacy stance you accept by adopting share_history.
- **The original signature is preserved across an unlimited chain.** Every recipient at every hop verifies the same Ed25519 signature against the same original `from` PK.
- **No "nested share_history" can be constructed by a correct client.** The recursive case is structurally impossible. The validation rule §12.11.3 still rejects nested share_history defensively, but cooperating clients will never produce it.

#### 12.11.11 Per-hop wrapper — losses on unwrap (accepted by design)

The unwrap-and-rewrap pattern is **lossy at the wrapper level**. The following are deliberately discarded when a relayer unwraps the previous wrapper:

- **The previous relayer's `cited_users` list.** The previous wrapper may have included recipient-targeted `cited_users` for FCM push routing to its specific intended audience. When unwrapped, this list is gone. The new relayer constructs their own `cited_users` for their own intended recipient(s). The inner envelope's `cited_users` (the original sender's mentions, at the time the original was authored) are preserved inside `original_message` but are never read by the server — they live inside the encrypted body and only become visible to clients after decryption.
- **The previous relayer's wrapper-level `text_message` note.** If Carol wrote "FYI Dave, here's that message Bob asked about" in her wrapper when she shared to Dave, Dave does not propagate that note when he re-shares to Erin. Each wrapper's note is **per-hop**: it speaks from the current relayer to the current recipient and does not carry downstream. Dave writes his own note for Erin (or omits it).
- **The previous relayer's `attached_media`** (the wrapper-level additions, rare). Same per-hop rule.
- **The previous relayer's signature.** Their participation in the relay chain is not visible downstream. They contributed anonymously to the recovery path.

What is **never** lost on unwrap:

- The original sender's signature (inside the preserved `original_message` envelope).
- The original sender's identity, encrypted content, action/targets context, and attachments.
- The fact that the new recipient gets a cryptographically-verifiable original.

This per-hop semantics is what makes share_history different from cross-conversation forward: forward deliberately accumulates a chain of claims (each `fw_content` level visible to all downstream recipients); share_history deliberately strips the chain (only the original and the most recent relayer are visible). The two operations have opposite metadata stances by design — both intentional.

---

## 13. Decisions (resolved 2026-05-27, second round)

| # | Question | Decision |
|---|---|---|
| 1 | Tier-1 ship cadence (`reaction_remove`, `edit`, `redact`) | Ship together with reply/reaction in 1.5.0. |
| 2 | Pin/unpin authorization | **Creator-only** — PK equality `envelope.from == creation_request.from`. No admin-set concept exists yet; if added later it would widen this check without a wire change. |
| 3 | Pin/unpin shape | **Single-slot model** (§5.5). One pinned message per conversation; `pin` overwrites, `unpin` clears the slot. `unpin` has empty `targets` (cleaner than a `pin_clear` action with sentinel targets). |
| 4 | Cross-action matrix (§12.8) | Approved as written. |
| 5 | Edit-chain max depth | 4 levels. |
| 6 | `forward` action | **In scope for 1.5.0** (§12.10). Re-encoded body under destination key; `targets[0]` = claimed-origin PK (cardinality 0 or 1); attachments MUST be re-encrypted and re-uploaded; chain is recursive via `fw_content` (depth cap 10); visual distinction from native content is mandatory in clients. **Deliberately minimal — see §12.10.5.** Future enrichment proposals are out of scope on principle, not for lack of effort. |
| 7 | `share_history` action | **In scope for 1.5.0** (§12.11). Different feature from forward. In-conversation only. Cardinality 0; embedded `original_message` carries the encrypted original signed envelope, preserved intact (signature verifiable, same conversation key). Nested share_history rejected — no forensic chain. UI renders the verified original-sender attribution normally with an informational "shared by" badge; no skeptical decoration. |
| 8 | Polls, read receipts, typing, vote, acknowledge | Deferred (§12.6). |
| 9 | `conversation_role` column | Dead data (§6.6). Cleanup tracked separately, not blocking this branch. |
