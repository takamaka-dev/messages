# Implementation Plan — Phase 1: Messages 1.5.0 Foundation

**Date:** 2026-05-27
**Branch:** `feature/message-actions-reply-reaction`
**Target version:** `io.takamaka.messages:messages 1.5.0-SNAPSHOT`
**Status:** Ready to start. Design locked at branch HEAD `0d3e54e`.

This plan describes Phase 1 of the implementation rollout for the message-actions design in `MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md`. Phase 1 stabilizes the **Messages module** (Java protocol library) as the foundation that downstream consumers (`rsclient`, `rschat`, `shell`, `rsclient-flutter`, `tkmChat`) will build against. Subsequent phases are scoped at the end of this document.

---

## 1. Scope of Phase 1

In scope (this branch):

- Bean annotation polish on `BasicMessageEncryptedContentBean`.
- New `MessageAction` enum / constants class.
- New `MessageActionValidator` utility implementing the §4.2 pipeline.
- New `ChatCryptoUtils` helpers — one canonical-construction method per action.
- Cross-platform test vectors (fixtures generated from Java, consumed by future Dart port).
- Unit and integration tests.
- Maven version bump to `1.5.0-SNAPSHOT`.

Out of scope (subsequent phases):

- Any other module's code or version (`rsclient`, `rschat`, `shell`, `rsclient-flutter`, `tkmChat`).
- `rschat-docs` parent E2E protocol doc update (separate doc-only branch).
- Removal of dead `users_to_conversations.conversation_role` column (separate schema-migration branch).

---

## 2. Cross-compatibility concerns — must be addressed in Phase 1

These items affect protocol-level interoperability between the Java reference and any future Dart port. Each must be either resolved on this branch (Java side) or explicitly recorded as a binding constraint for the parallel Dart work in Phase 2.

### 2.1 Bean field parity gap (Java vs Dart) — known issue

Current state grep:

- Java `BasicMessageEncryptedContentBean.java` — **7 fields**: `text_message`, `attached_media`, `action`, `targets`, `fw_content`, `original_message`, `re_shared`.
- Dart `basic_message_encrypted_content_bean.dart` — **2 fields**: `text_message`, `attached_media`. Missing: `action`, `targets`, `fw_content`, `original_message`, `re_shared`.

**Impact:** any Dart client receiving a Java-emitted message that uses the new fields silently drops the action semantics. Until the Dart bean is updated, reply / reaction / forward / share_history / pin / etc. cannot round-trip Java↔Dart.

**Resolution boundary:** Phase 1 owns documenting the new fields and generating canonical-JSON test vectors. Phase 2 (rsclient-flutter) applies the mirror with `build_runner` regeneration. The Phase 2 branch must reference this plan and the spec.

### 2.2 Bean annotations — change of JSON serialization

Phase 1 adds class-level `@JsonInclude(JsonInclude.Include.NON_NULL)` and `@JsonIgnoreProperties(ignoreUnknown = true)` to `BasicMessageEncryptedContentBean`. Effects:

- **Existing simple messages** (only `text_message` set) now serialize as `{"text_message": "..."}` — they were already doing this in practice since the other fields were never set, but the new explicit class-level annotation makes the behavior canonical.
- **Old clients receiving new messages with unknown fields** continue to work because `ChatUtils.java:185` and `SimpleRequestHelper.java:169` already set `FAIL_ON_UNKNOWN_PROPERTIES = false`. The class-level annotation is defense-in-depth.
- **Dart compatibility:** the Dart bean already declares `@JsonSerializable(includeIfNull: false)`. Default Dart `json_serializable` behavior also ignores unknown fields on decode. Parity holds.

### 2.3 Canonical-JSON ordering with new fields

Signatures are computed over canonical JSON with keys sorted alphabetically (`SimpleRequestHelper.getCanonicalJson()` uses `SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS`). The Dart port must produce identical ordering.

Alphabetical order of the new full field set:
```
action, attached_media, fw_content, original_message, re_shared, targets, text_message
```

**Phase 1 commitment:** generate test vectors of canonical JSON for every bean field combination used by the validator/tests. Phase 2 (Dart) must reproduce these byte-for-byte.

### 2.4 Encoding asymmetry — `preview` (Base64 standard) vs hashes (Base64URL with `.` padding)

Documented in spec §11.5. Reaffirmed here as a binding constraint:

- `ChatMediaPlaceholderBean.preview` — **standard Base64** (RFC 4648 §4, `+`/`/`, `=` padding). Both Java and Dart MUST use the standard decoder for this field.
- All other hash fields (`unencrypted_content_hash`, `encrypted_file_hash`, `signature`, conversation hashes) — **Base64URL with `.` padding**. Both platforms MUST use the URL-safe decoder with `.` substituted for `=`.

**Phase 1 test vectors** must include at least one inline-image fixture that exercises both encodings in the same bean.

### 2.5 Recursive bean type — `BasicMessageEncryptedContentBean.fw_content`

The bean has a self-referential field (`fw_content` of its own type). Implications:

- **Jackson (Java):** handles via standard `@JsonProperty` annotation. No custom serializer needed.
- **json_serializable (Dart):** code generation handles recursive types. The `.g.dart` file produced by `build_runner` must be checked into the rsclient-flutter repo and verified.
- **Depth cap of 10** is enforced at the **validator** layer (Phase 1 Java responsibility), not at the Jackson layer. Both Java and Dart validators must implement the same cap.

**Phase 1 commitment:** the `MessageActionValidator` implements depth counting; test vectors include depth-1, depth-9 (boundary OK), and depth-11 (over-cap, truncated). Phase 2 Dart validator must match.

### 2.6 Heterogeneous embedded type — `BasicMessageEncryptedContentBean.original_message`

The `original_message` field is of type `BasicMessageRequestBean` (the full signed envelope including `SignedMessageBean` superclass fields). This is a different bean type embedded in the encrypted content.

- **Jackson (Java):** straightforward — `BasicMessageRequestBean` is in the same package, no cyclic import.
- **json_serializable (Dart):** the corresponding Dart class hierarchy (`BasicMessageRequestBean` extends `SignedMessageBean`) already exists. Phase 2 must verify that the generated `_$BasicMessageEncryptedContentBeanFromJson` correctly dispatches to `BasicMessageRequestBean.fromJson` for the embedded field.

**Phase 1 commitment:** a test vector for a share_history message (action="share_history" with `original_message` populated, embedded encrypted_content blob included verbatim).

### 2.7 Action enum value strings

`MessageAction` enum values in Java MUST serialize to lowercase strings matching the spec exactly: `"reply"`, `"reaction"`, `"reaction_remove"`, `"edit"`, `"redact"`, `"pin"`, `"unpin"`, `"forward"`, `"share_history"`.

Dart implementations may model this as a `String` constant set or a Dart enum with `@JsonValue` annotations. The wire-level string is what's binding.

**Phase 1 commitment:** Java enum has explicit lowercase JSON value getter; tests assert exact wire-string match for every action.

### 2.8 Regex parity

Two regexes the validator dispatches on:

| Target type | Regex | Used by |
|---|---|---|
| Signature | `^[A-Za-z0-9_-]{86}\.\.$` | `reply`, `reaction`, `reaction_remove`, `edit`, `redact`, `pin` |
| Public key | `^[A-Za-z0-9_-]{43}\.$` | `forward` (when `targets[0]` populated) |

Both are POSIX-compatible and portable. Phase 2 Dart port reuses them verbatim.

### 2.9 InlineContentLimits constants

Already declared in `InlineContentLimits.java` on this branch. Phase 2 must create a matching Dart class at `rsclient-flutter/lib/src/beans/attachment/inline_content_limits.dart`. Values:

| Constant | Java | Dart |
|---|---|---|
| Max inline bytes | `50 * 1024 = 51200` | `50 * 1024 = 51200` |
| Max thumbnail dimension (px) | `256` | `256` |
| Reaction MIME whitelist | `{png, jpeg, webp, gif}` (case-insensitive) | same |

**Phase 1 commitment:** verify the Java class compiles and is exported. The shell `ThumbnailService` migration (referencing these constants instead of declaring its own) is **Phase 3** work, not Phase 1.

### 2.10 `Boolean re_shared` — nullability semantics

Java field is `Boolean` (capital B, nullable wrapper type). On the wire:

- Not set → JSON omits the field (with `@JsonInclude(NON_NULL)`)
- `true` → JSON `"re_shared": true`
- `false` → JSON `"re_shared": false` (technically also a valid first-share marker, but absent is equivalent)

Dart equivalent: `bool? reShared`. Same three-state semantics; same serialization rules. `includeIfNull: false` on the Dart bean honors omission.

---

## 3. Task breakdown

### 3.1 Bean polish (commit 1)

**File:** `src/main/java/io/takamaka/messages/chat/message/BasicMessageEncryptedContentBean.java`

Add class-level annotations:

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BasicMessageEncryptedContentBean {
    // ...existing fields, unchanged...
}
```

Imports added:
```java
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
```

**Acceptance:** `mvn clean compile` passes. JSON round-trip test confirms null fields are omitted. Round-trip with an extra unknown field (`{"text_message": "x", "unknown_field": 42}`) succeeds without exception.

### 3.2 `MessageAction` enum (commit 2)

**New file:** `src/main/java/io/takamaka/messages/chat/message/MessageAction.java`

Closed-vocabulary enum with explicit JSON values. Each enum constant carries its lowercase wire-string. Provides:

- `value()` — returns the wire string (`"reply"`, etc.)
- `fromValue(String)` — case-sensitive lookup; returns `Optional<MessageAction>` (null/empty → unknown action → graceful degrade)
- `getTargetCardinality()` — returns `0`, `1`, or `0_OR_1` (small enum or constants)
- `getTargetFormat()` — returns `SIGNATURE` or `PUBLIC_KEY` or `NONE`
- `getAuthorizationPattern()` — returns `NO_CHECK`, `SELF_AUTHOR`, or `CONVERSATION_CREATOR`

```java
public enum MessageAction {
    REPLY("reply", Cardinality.ONE, TargetFormat.SIGNATURE, AuthPattern.NO_CHECK),
    REACTION("reaction", Cardinality.ONE, TargetFormat.SIGNATURE, AuthPattern.NO_CHECK),
    REACTION_REMOVE("reaction_remove", Cardinality.ONE, TargetFormat.SIGNATURE, AuthPattern.SELF_AUTHOR),
    EDIT("edit", Cardinality.ONE, TargetFormat.SIGNATURE, AuthPattern.SELF_AUTHOR),
    REDACT("redact", Cardinality.ONE, TargetFormat.SIGNATURE, AuthPattern.SELF_AUTHOR),
    PIN("pin", Cardinality.ONE, TargetFormat.SIGNATURE, AuthPattern.CONVERSATION_CREATOR),
    UNPIN("unpin", Cardinality.ZERO, TargetFormat.NONE, AuthPattern.CONVERSATION_CREATOR),
    FORWARD("forward", Cardinality.ZERO_OR_ONE, TargetFormat.PUBLIC_KEY, AuthPattern.NO_CHECK),
    SHARE_HISTORY("share_history", Cardinality.ZERO, TargetFormat.NONE, AuthPattern.NO_CHECK);
    // ...
}
```

**Acceptance:** unit tests assert exact wire strings for each action, round-trip parsing, and unknown-string handling returns empty Optional.

### 3.3 `MessageActionValidator` (commit 3)

**New file:** `src/main/java/io/takamaka/messages/chat/message/MessageActionValidator.java`

Pure functions implementing the §4.2 pipeline. Receives a decrypted `BasicMessageEncryptedContentBean` plus context (conversation creator PK, local message cache lookup function, registered-user lookup function). Returns `ValidationResult` — a structured record:

```java
public record ValidationResult(
    boolean valid,
    String decorationCode,    // null if valid; "INVALID_REPLY_BAD_SIGNATURE" etc. if not
    String decorationMessage  // human-readable for client UI
) {}
```

Validator methods:

- `validateAction(BasicMessageEncryptedContentBean bean)` — returns valid action, or graceful-degrade for unknown.
- `validateCardinality(MessageAction action, List<String> targets)`.
- `validateTargetFormat(MessageAction action, List<String> targets)` — dispatches regex on `getTargetFormat()`.
- `validateAuthorization(MessageAction action, String envelopeFrom, Context ctx)` — dispatches on auth pattern.
- `validateForwardDepth(BasicMessageEncryptedContentBean bean)` — walks `fw_content` chain, caps at 10.
- `validateShareHistory(BasicMessageEncryptedContentBean bean, BasicMessageRequestBean outerEnvelope, Context ctx)` — embedded-signature verification, same-conversation check, no-nested-share check.
- `validateAll(...)` — orchestrates the above and produces a single `ValidationResult`.

All decoration codes are constants in the validator class. They match the spec text exactly.

**Acceptance:** unit tests for every action × every failure mode (~30 cases). Forward-compat test: synthetic `"__test_unknown_action__"` → result is `valid=true` (graceful degrade) with a decoration noting the unknown action. Depth-11 forward → truncation decoration.

### 3.4 `ChatCryptoUtils` helpers (commit 4)

**File:** `src/main/java/io/takamaka/messages/utils/ChatCryptoUtils.java`

Add per-action canonical-construction methods. Each wraps the existing `getBasicMessageBean(...)` with action-specific defaults and the new field population. Signatures:

```java
public static BasicMessageRequestBean getReplyMessageBean(
    InstanceWalletKeystoreInterface iwkSign,
    int index,
    String conversationHashName,
    String conversationEncryptionKey,
    List<String> citedUsers,
    String replyText,
    String parentSignature,  // targets[0]
    Function<String, byte[]> keyDerivationFn
) throws ...;

public static BasicMessageRequestBean getReactionMessageBean(
    InstanceWalletKeystoreInterface iwkSign,
    int index,
    String conversationHashName,
    String conversationEncryptionKey,
    String parentSignature,
    ChatMediaPlaceholderBean reactionPayload,  // emoji or sticker or inline image
    Function<String, byte[]> keyDerivationFn
) throws ...;

// ... and one per other action:
// getReactionRemoveMessageBean, getEditMessageBean, getRedactMessageBean,
// getPinMessageBean, getUnpinMessageBean, getForwardMessageBean,
// getShareHistoryMessageBean
```

Each helper:
- Builds the `BasicMessageEncryptedContentBean` with the right field combination per the action's registry row.
- Calls the existing encryption + signature wrap.
- Returns a fully-formed `BasicMessageRequestBean`.

For `getForwardMessageBean`: accepts the prior `fw_content` to embed (or null for first-hop forwards), plus the claimed-origin PK (or null for anonymous). Enforces the depth-10 cap at construction time (throw if input would exceed).

For `getShareHistoryMessageBean`: accepts the original `BasicMessageRequestBean` to embed. Validates the embedded inner signature before constructing (refuse to wrap a forgery). Sets `re_shared` based on whether the caller indicates they received the original via a prior share.

**Acceptance:** integration test for each action — construct, serialize, parse, validate. Cross-platform vector fixture generated for each.

### 3.5 Cross-platform test vectors (commit 5, may be merged with commit 4)

**New directory:** `src/test/resources/cross-platform-vectors/`

For each action, one or more JSON fixtures with deterministic inputs:

- Bean state (the populated `BasicMessageEncryptedContentBean` before encryption)
- Canonical JSON of the above (output of `SimpleRequestHelper.getCanonicalJson()`)
- Signed envelope (`BasicMessageRequestBean` post-encryption + sign)
- Wire-format JSON
- Validator expected result

Fixtures are loaded by a `CrossPlatformVectorTest` in Java that asserts round-trip. They are also intended to be loaded by the future Dart port to verify byte-identical canonical JSON.

Minimum fixture set:

1. Plain message (no action)
2. Reply with text only
3. Reaction (emoji variant)
4. Reaction (inline-image variant, exercises both Base64 encodings)
5. Reaction_remove
6. Edit with new text + new media
7. Redact with reason text
8. Pin with reason
9. Unpin
10. Forward, depth 1, attributed
11. Forward, depth 1, anonymous (empty targets)
12. Forward, depth 3 (exercises recursive fw_content)
13. Forward, depth 11 (over cap, expected truncation)
14. Share_history with first-share (`re_shared=false`)
15. Share_history with re-share (`re_shared=true`)
16. Unknown action `__test_unknown_action__` (forward-compat case)

**Acceptance:** all fixtures load and round-trip in Java without exception. Each fixture's canonical-JSON output is byte-identical to the stored fixture (regression protection).

### 3.6 Version bump (commit 6, may be merged with commit 5)

**File:** `pom.xml`

`<version>1.4.0-SNAPSHOT</version>` → `<version>1.5.0-SNAPSHOT</version>`

**Acceptance:** `mvn clean install` produces `messages-1.5.0-SNAPSHOT.jar`.

---

## 4. Testing strategy

### 4.1 Unit tests

Coverage targets:

- `MessageAction` enum — wire-string serialization, parsing, unknown handling. Target: 100% of enum values.
- `MessageActionValidator` — every action × every failure mode in the spec. Target: every named decoration code triggered at least once.
- `ChatCryptoUtils` helpers — construct, serialize, parse, validate. Target: every helper exercised.
- `InlineContentLimits.isReactionImageMimeAllowed` — case-insensitive matching; reject all non-whitelisted types.

### 4.2 Integration tests

- **Round-trip per action:** construct → encrypt+sign → wire-format → parse → decrypt → validate → assert equality.
- **Backward compatibility:** a Messages-1.4-style bean (only `text_message` and `attached_media`) parsed by the 1.5 deserializer, fields populated, no extra fields.
- **Forward compatibility:** a synthetic Messages-1.6 future bean (with an unknown extra field `future_field: "x"`) parsed by the 1.5 deserializer, the unknown field ignored, no exception.

### 4.3 Cross-platform parity (preparation for Phase 2)

The test vectors in §3.5 are the contract between Java and Dart. Phase 2 reads the same fixture files; Dart-side round-trips must produce byte-identical canonical JSON. Phase 1 ships the fixtures as `src/test/resources/cross-platform-vectors/` so the Dart port has a fixed reference.

### 4.4 Negative tests

For each action, at least one negative case per failure mode in §4.2 of the spec:

- Bad cardinality (e.g., reply with 2 targets, unpin with 1 target)
- Bad target format (e.g., reply with a PK in `targets[0]`, forward with a signature)
- Authorization failure (e.g., edit by non-author, pin by non-creator)
- Action-specific (e.g., share_history with mismatched inner `conversation_hash_name`)
- Depth-11 forward → truncation decoration

---

## 5. Acceptance criteria — Phase 1 complete when

1. `mvn clean install` produces `messages-1.5.0-SNAPSHOT.jar` cleanly.
2. All new tests pass (target: 80%+ coverage on `MessageActionValidator`, 100% on `MessageAction` enum).
3. All cross-platform test vector fixtures load and round-trip in Java.
4. `BasicMessageEncryptedContentBean` is annotated with `@JsonInclude(NON_NULL)` and `@JsonIgnoreProperties(ignoreUnknown=true)`.
5. The Maven artifact has been published to local repo (`mvn install`) and the new artifact resolves cleanly when consumed by a test downstream project.
6. The branch contains a clean commit chain reviewed by Iris with no open review comments.

---

## 6. Subsequent phases (descoped from this plan, but tracked)

| Phase | Module | Scope |
|---|---|---|
| **2** | `rsclient-flutter` | Mirror the 5 new bean fields with `@JsonKey`, add `InlineContentLimits` Dart class, regenerate `*.g.dart` via `build_runner`, port `MessageAction` and `MessageActionValidator`, verify cross-platform vectors (§3.5) load byte-identically. |
| **3a** | `rsclient` (Java) | Dep bump to Messages 1.5.0, version bump to 1.5-SNAPSHOT. No API changes expected; existing `CallHelper` / `DefaultCalls` pass through. |
| **3b** | `rschat` (Java) | Dep bump to Messages 1.5.0, version bump to 0.5.1-SNAPSHOT. Server-side validation is unchanged (server is action-blind). |
| **3c** | `shell` (Java) | Dep bump to Messages 1.5.0 + rsclient 1.5, version bump to 0.6.0-SNAPSHOT. Migrate `ThumbnailService` to reference `InlineContentLimits` from Messages. Add new commands: `reply`, `react`, `unreact`, `edit`, `redact`, `pin`, `unpin`, `forward`, `share-history`. |
| **4** | `tkmChat` (Flutter) | Dep bump to rsclient-flutter 0.2.0, version bump to 0.2.0+1. Local Drift DB schema migration (reaction index, pin slot, edit chain). Update `message_service.dart` to pass new fields (currently drops `replyToSignature` at lines 255-258). UI for reply/react/edit/redact/pin/forward/share. |
| **5** | `rschat-docs` | Propagate the parent E2E protocol doc to v1.6, add §3.4 "Message Actions" referencing this spec. Add the §6.4 timestamp-threat caveat into §8.5 of the parent doc. Separate branch / repo because rschat is currently on unrelated work. |
| **6 (cleanup, low priority)** | `rschat` schema | Remove dead `users_to_conversations.conversation_role` column and `conversation_roles` lookup table (or wire to a real authorization model). Separate schema-migration branch. |

Phases 2 and 3 can run in parallel after Phase 1 ships. Phase 4 depends on Phase 2. Phase 5 is doc-only and can start any time after Phase 1.

---

## 7. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Dart bean diverges from Java in canonical-JSON ordering | Cross-platform test vectors (§3.5) shipped from Phase 1; Phase 2 fails its CI if any vector mismatches |
| Recursive `fw_content` causes Jackson/Dart parser stack issues | Hard depth cap of 10 enforced at validator layer; vector for depth 9 (OK) and depth 11 (truncated); no client should ever construct deeper |
| `original_message` embedded-signature verification edge cases (replay of redacted, replay of edit) | Spec §12.11 + §12.8 matrix cover these; validator tests assert decoration codes; client UI behaviour is informative |
| Unknown actions cause crashes in older clients | `FAIL_ON_UNKNOWN_PROPERTIES=false` is already set globally (ChatUtils:185, SimpleRequestHelper:169); class-level `@JsonIgnoreProperties` adds defense-in-depth; forward-compat test vector covers this |
| Encoding asymmetry (`preview` Base64-standard vs hashes Base64URL) trips up implementers | Spec §11.5 explicit; inline-image test vector exercises both encoders in one fixture |
| Bean field count growing (7 currently, likely 8+ after future actions) | Each field documented with action it supports; spec §12.6 deferred-actions list keeps the registry closed for v1.5 |

---

## 8. Definition of done — single sentence

**Phase 1 is done when an external consumer can pull `io.takamaka.messages:messages:1.5.0-SNAPSHOT` from the local Maven repo, call `ChatCryptoUtils.getReplyMessageBean(...)` (or any other action helper), serialize the result, hand the wire JSON to a future Dart implementation, and have the Dart implementation parse, validate, and round-trip it byte-identically.**

---

**Document Status:** Ready for implementation start. References branch HEAD `0d3e54e` (12 commits, design locked).
**Path:** `/home/h2tcoin.com/giovanni.antino/NetBeansProjects/Messages/docs/roadmap/IMPL_PLAN_PHASE_1_MESSAGES_1_5_0.md`
