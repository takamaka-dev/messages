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
- New `MessageAction` constants class + `MessageActionMeta` registry (no enum — string-typed wire values; see §2.7 and §3.2).
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

### 2.7 Action wire-string constants and case-insensitive normalization

**Decision (revised 2026-05-27):** the action value is represented across all language ports as a plain string with a fixed lowercase canonical form. **No enum.** Java enums interoperate poorly with non-Java implementations (Dart, Python, Rust, Go, JavaScript all have divergent enum semantics), and the wire format is a string regardless — the enum adds Java-side type safety we can mostly recover with `public static final String` constants while avoiding the cross-port headache.

**Canonical wire form:** lowercase strings — `"reply"`, `"reaction"`, `"reaction_remove"`, `"edit"`, `"redact"`, `"pin"`, `"unpin"`, `"forward"`, `"share_history"`.

**Inbound normalization rule (normative for the validator):** every incoming `action` value MUST be normalized to lowercase via `Locale.ROOT.toLowerCase()` (Java) or `String.toLowerCase()` (Dart, language default is locale-independent) before comparison against the closed registry. This protects against rushed implementations or hand-rolled JSON that emit `"Reply"` or `"REPLY"`: clients accept the case variant and proceed to render as the canonical form.

**Outbound emission rule:** all action values emitted by Phase-1 code MUST use the canonical lowercase form verbatim — no case mixing, no whitespace. Test vectors assert byte-exact match.

**Phase 1 commitment:** the `MessageAction` constants class declares the canonical strings as `public static final String`. The `MessageActionMeta` registry is keyed by these constants. Inbound normalization is applied at a single chokepoint in the validator. Tests cover both happy-path lowercase and case-variant inbound strings.

**Cross-platform implication:** Phase 2 (Dart) mirrors with `static const String reply = 'reply'` etc. in a `class MessageAction`. Dart's `String.toLowerCase()` is locale-independent by default; safe to use without `Locale.ROOT` equivalent. The bean's `action` field stays `String?` on both platforms — no `@JsonValue` annotation, no enum-mapping logic.

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

The bean already carries `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor`, and (pre-applied during the plan-refinement phase) `@Builder`. This commit completes the class-level annotation set by adding the two Jackson annotations:

```java
@Data
@Builder           // already present
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)        // NEW in commit 1
@JsonIgnoreProperties(ignoreUnknown = true)        // NEW in commit 1
public class BasicMessageEncryptedContentBean {
    // ...fields unchanged from current branch HEAD...
}
```

Imports added:
```java
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
```

**Acceptance:** `mvn clean compile` passes. JSON round-trip test confirms null fields are omitted. Round-trip with an extra unknown field (`{"text_message": "x", "unknown_field": 42}`) succeeds without exception.

### 3.2 `MessageAction` constants + `MessageActionMeta` registry (commit 2)

**New file 1:** `src/main/java/io/takamaka/messages/chat/message/MessageAction.java`

A constants holder — NOT an enum. Each action's canonical lowercase wire-string is exposed as a `public static final String`. Provides a closed registry (`KNOWN` set), a case-insensitive normalization helper, and a membership check.

```java
public final class MessageAction {
    public static final String REPLY            = "reply";
    public static final String REACTION         = "reaction";
    public static final String REACTION_REMOVE  = "reaction_remove";
    public static final String EDIT             = "edit";
    public static final String REDACT           = "redact";
    public static final String PIN              = "pin";
    public static final String UNPIN            = "unpin";
    public static final String FORWARD          = "forward";
    public static final String SHARE_HISTORY    = "share_history";

    /** Closed registry of recognized actions. */
    public static final Set<String> KNOWN = Set.of(
        REPLY, REACTION, REACTION_REMOVE, EDIT, REDACT,
        PIN, UNPIN, FORWARD, SHARE_HISTORY
    );

    /** Normalize an inbound action string for comparison. Returns null if input is null. */
    public static String normalize(String raw) {
        return raw == null ? null : raw.toLowerCase(java.util.Locale.ROOT);
    }

    /** Case-insensitive membership check. */
    public static boolean isKnown(String raw) {
        return raw != null && KNOWN.contains(normalize(raw));
    }

    private MessageAction() {}
}
```

**New file 2:** `src/main/java/io/takamaka/messages/chat/message/MessageActionMeta.java`

Per-action metadata registry keyed by the lowercase action string. Carries cardinality, target format, and authorization pattern. The validator (§3.3) dispatches off this registry.

```java
public final class MessageActionMeta {

    public enum Cardinality { ZERO, ONE, ZERO_OR_ONE }
    public enum TargetFormat { NONE, SIGNATURE, PUBLIC_KEY }
    public enum AuthPattern  { NO_CHECK, SELF_AUTHOR, CONVERSATION_CREATOR }

    public record ActionSpec(Cardinality cardinality,
                             TargetFormat targetFormat,
                             AuthPattern  authPattern) {}

    private static final Map<String, ActionSpec> REGISTRY = Map.of(
        MessageAction.REPLY,           new ActionSpec(Cardinality.ONE,         TargetFormat.SIGNATURE,  AuthPattern.NO_CHECK),
        MessageAction.REACTION,        new ActionSpec(Cardinality.ONE,         TargetFormat.SIGNATURE,  AuthPattern.NO_CHECK),
        MessageAction.REACTION_REMOVE, new ActionSpec(Cardinality.ONE,         TargetFormat.SIGNATURE,  AuthPattern.SELF_AUTHOR),
        MessageAction.EDIT,            new ActionSpec(Cardinality.ONE,         TargetFormat.SIGNATURE,  AuthPattern.SELF_AUTHOR),
        MessageAction.REDACT,          new ActionSpec(Cardinality.ONE,         TargetFormat.SIGNATURE,  AuthPattern.SELF_AUTHOR),
        MessageAction.PIN,             new ActionSpec(Cardinality.ONE,         TargetFormat.SIGNATURE,  AuthPattern.CONVERSATION_CREATOR),
        MessageAction.UNPIN,           new ActionSpec(Cardinality.ZERO,        TargetFormat.NONE,       AuthPattern.CONVERSATION_CREATOR),
        MessageAction.FORWARD,         new ActionSpec(Cardinality.ZERO_OR_ONE, TargetFormat.PUBLIC_KEY, AuthPattern.NO_CHECK),
        MessageAction.SHARE_HISTORY,   new ActionSpec(Cardinality.ZERO,        TargetFormat.NONE,       AuthPattern.NO_CHECK)
    );

    /** Lookup by case-insensitive action string. Returns empty for unknown actions
     *  (graceful-degrade trigger per spec §12.7). */
    public static Optional<ActionSpec> lookup(String action) {
        if (action == null) return Optional.empty();
        return Optional.ofNullable(REGISTRY.get(MessageAction.normalize(action)));
    }

    /** Closed registry view for completeness tests. */
    public static Set<String> registeredActions() {
        return REGISTRY.keySet();
    }

    private MessageActionMeta() {}
}
```

The three small enums (`Cardinality`, `TargetFormat`, `AuthPattern`) are **internal-only** — they never appear on the wire and never travel between language ports. Cross-platform interop happens only through the string action value. The internal enums can be replaced with constants or sealed classes per language without protocol consequence.

**Rationale recap (see §2.7):** keeping the wire-level value as `String` avoids enum-mapping divergence between Java, Dart, and any future port. Inbound `action` strings are normalized via `Locale.ROOT.toLowerCase()` so case variants like `"Reply"` are accepted gracefully. Outbound strings are emitted in canonical lowercase.

**Acceptance:**
- Unit tests assert each `public static final String` exposes the exact canonical lowercase form.
- `MessageAction.isKnown("Reply")` returns `true` (case-insensitive).
- `MessageAction.isKnown("__test_unknown_action__")` returns `false`.
- `MessageActionMeta.lookup("FORWARD")` returns the correct `ActionSpec` (case-insensitive).
- **Registry-completeness test:** assert `MessageAction.KNOWN.equals(MessageActionMeta.registeredActions())` — guards against forgetting to register metadata for a new action.

### 3.3 `MessageActionValidator` + hard/soft split (commit 3)

**Decision recap.** The validator implements the §4.2 pipeline and the strict-drop family (§12.7 + §12.11.4 step 2). Two failure categories:

- **Hard violations (six total — H1-H6).** The validator **throws** a typed `HardProtocolViolationException` subclass. The caller catches; the message is dropped (not rendered). Hard violations correspond to "we cannot trust this bean's identity at all" — version mismatch (H1-H4), unknown action (H5), embedded inner-signature failure (H6).
- **Soft violations.** The validator **returns** a `ValidationResult` record with a list of `Decoration` entries. The body still renders; decorations attach informational, structural, or tamper-signal hints. Severity is a String field with canonical values held in a constants class (consistent with `MessageAction`'s strings-not-enum pattern, per the cross-compat stance in §2.7).

#### 3.3.1 Files created

| File | Purpose |
|---|---|
| `MessageActionValidator.java` | Validator entry-point and helper methods |
| `ValidationResult.java` | Record for soft-violation results |
| `Decoration.java` | Record for a single decoration entry |
| `DecorationSeverity.java` | String constants holder (INFO / WARN / ERROR) |
| `ValidationDecorationCodes.java` | String constants holder for the soft-violation codes |
| `HardProtocolViolationException.java` | Base hard-violation exception |
| `MalformedProtocolVersionException.java` | H1 |
| `IncompatibleMajorVersionException.java` | H2 |
| `MissingVersionWithFeaturesException.java` | H3 |
| `LegacyVersionWithFeaturesException.java` | H4 |
| `UnknownActionException.java` | H5 |
| `InnerSignatureFailureException.java` | H6 |

All six hard-violation subclasses extend `HardProtocolViolationException` extends `ChatMessageException`. The base class carries a `code` field (string matching the H-list code), the offending value (e.g., the unrecognized action string), and optional context (conversation hash, sender PK) for logging.

#### 3.3.2 Record shapes

```java
public record ValidationResult(
    boolean overallValid,            // true iff decorations contains no ERROR-severity entries
    List<Decoration> decorations     // possibly empty; multiple findings compound
) {
    public boolean hasErrors() {
        return decorations.stream()
            .anyMatch(d -> DecorationSeverity.ERROR.equals(d.severity()));
    }
}

public record Decoration(
    String code,                     // e.g. "BROKEN_REFERENCE_REPLY" (from ValidationDecorationCodes)
    String humanReadableMessage,     // for client UI
    String severity,                 // canonical: "INFO" | "WARN" | "ERROR" (from DecorationSeverity)
    String affectedField             // optional pointer to bean field, e.g. "targets[0]"; may be null
) {}
```

`overallValid` is derived from the decoration list — `true` iff no decoration carries severity `ERROR`. Callers can either check `overallValid` for a single boolean dispatch or iterate `decorations` for fine-grained handling (logging, telemetry, per-decoration UI).

#### 3.3.3 String-constants holders

**Severity constants** — `DecorationSeverity.java`:

```java
public final class DecorationSeverity {
    public static final String INFO  = "INFO";
    public static final String WARN  = "WARN";
    public static final String ERROR = "ERROR";

    public static final Set<String> KNOWN = Set.of(INFO, WARN, ERROR);

    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isKnown(String raw) {
        return raw != null && KNOWN.contains(normalize(raw));
    }

    private DecorationSeverity() {}
}
```

**Decoration codes** — `ValidationDecorationCodes.java`:

```java
public final class ValidationDecorationCodes {
    // Cardinality / target format
    public static final String INVALID_REPLY_MALFORMED_TARGETS         = "INVALID_REPLY_MALFORMED_TARGETS";
    public static final String INVALID_REACTION_MALFORMED_TARGETS      = "INVALID_REACTION_MALFORMED_TARGETS";
    public static final String INVALID_REACTION_REMOVE_MALFORMED_TARGETS = "INVALID_REACTION_REMOVE_MALFORMED_TARGETS";
    public static final String INVALID_EDIT_MALFORMED_TARGETS          = "INVALID_EDIT_MALFORMED_TARGETS";
    public static final String INVALID_REDACT_MALFORMED_TARGETS        = "INVALID_REDACT_MALFORMED_TARGETS";
    public static final String INVALID_PIN_MALFORMED_TARGETS           = "INVALID_PIN_MALFORMED_TARGETS";
    public static final String INVALID_UNPIN_MALFORMED_TARGETS         = "INVALID_UNPIN_MALFORMED_TARGETS";
    public static final String INVALID_FORWARD_MALFORMED_TARGETS       = "INVALID_FORWARD_MALFORMED_TARGETS";
    public static final String INVALID_SHARE_HISTORY_MALFORMED_TARGETS = "INVALID_SHARE_HISTORY_MALFORMED_TARGETS";

    public static final String INVALID_REPLY_BAD_SIGNATURE_FORMAT    = "INVALID_REPLY_BAD_SIGNATURE_FORMAT";
    public static final String INVALID_REACTION_BAD_SIGNATURE_FORMAT = "INVALID_REACTION_BAD_SIGNATURE_FORMAT";
    // ... etc. for other signature-typed targets
    public static final String INVALID_FORWARD_BAD_PUBLIC_KEY_FORMAT = "INVALID_FORWARD_BAD_PUBLIC_KEY_FORMAT";

    // Broken references (target not in local cache)
    public static final String BROKEN_REFERENCE_REPLY    = "BROKEN_REFERENCE_REPLY";
    public static final String BROKEN_REFERENCE_REACTION = "BROKEN_REFERENCE_REACTION";
    public static final String BROKEN_REFERENCE_PIN      = "BROKEN_REFERENCE_PIN";

    // Authorization failures
    public static final String UNAUTHORIZED_EDIT             = "UNAUTHORIZED_EDIT";
    public static final String UNAUTHORIZED_REDACT           = "UNAUTHORIZED_REDACT";
    public static final String UNAUTHORIZED_REACTION_REMOVE  = "UNAUTHORIZED_REACTION_REMOVE";
    public static final String UNAUTHORIZED_PIN              = "UNAUTHORIZED_PIN";
    public static final String UNAUTHORIZED_UNPIN            = "UNAUTHORIZED_UNPIN";

    // share_history structural soft cases
    public static final String INVALID_SHARE_HISTORY_MISSING_ORIGINAL_MESSAGE = "INVALID_SHARE_HISTORY_MISSING_ORIGINAL_MESSAGE";
    public static final String INVALID_SHARE_HISTORY_CROSS_CONVERSATION       = "INVALID_SHARE_HISTORY_CROSS_CONVERSATION";   // severity ERROR
    public static final String INVALID_SHARE_HISTORY_NESTED                   = "INVALID_SHARE_HISTORY_NESTED";

    // Forward
    public static final String FORWARD_DEPTH_EXCEEDED = "FORWARD_DEPTH_EXCEEDED";

    // Inline content
    public static final String INLINE_DECODE_ERROR        = "INLINE_DECODE_ERROR";
    public static final String INLINE_SIZE_VIOLATION      = "INLINE_SIZE_VIOLATION";
    public static final String INLINE_HASH_MISMATCH       = "INLINE_HASH_MISMATCH";        // severity ERROR
    public static final String INLINE_DIMENSION_VIOLATION = "INLINE_DIMENSION_VIOLATION";
    public static final String INLINE_SIZE_MISMATCH       = "INLINE_SIZE_MISMATCH";
    public static final String INLINE_FIELD_VIOLATION     = "INLINE_FIELD_VIOLATION";
    public static final String INLINE_MIME_VIOLATION      = "INLINE_MIME_VIOLATION";

    // Reaction SHOULD-rules
    public static final String REACTION_TEXT_NOT_EMPTY = "REACTION_TEXT_NOT_EMPTY";

    // Closed set for completeness tests
    public static final Set<String> ALL = Set.of(/* all of the above */);

    private ValidationDecorationCodes() {}
}
```

(Hard violation codes — `INVALID_PROTOCOL_VERSION_*`, `INVALID_ACTION`, `INNER_SIGNATURE_FAILURE` — are NOT in this class. They are exposed as `getCode()` accessors on the exception subclasses themselves, since they never appear in a `ValidationResult`.)

#### 3.3.4 Validator methods

```java
public final class MessageActionValidator {

    /**
     * Top-level validation. Throws hard-violation exceptions; returns soft results.
     */
    public static ValidationResult validate(
        BasicMessageEncryptedContentBean bean,
        BasicMessageRequestBean outerEnvelope,
        ValidationContext ctx
    ) throws HardProtocolViolationException {
        // Step 0: protocol version gate (may throw H1-H4)
        validateProtocolVersion(bean);

        // Step 1: action recognition (may throw H5)
        String normalizedAction = validateActionRecognition(bean);
        if (normalizedAction == null) {
            // null/empty action → plain message, no further checks
            return ValidationResult.empty();
        }

        // Steps 2-5: per-action validation (collects soft decorations)
        List<Decoration> decorations = new ArrayList<>();
        validateCardinality(normalizedAction, bean, decorations);
        validateTargetFormat(normalizedAction, bean, decorations);
        validateTargetResolvable(normalizedAction, bean, ctx, decorations);
        validateAuthorization(normalizedAction, bean, outerEnvelope, ctx, decorations);
        validateActionSpecific(normalizedAction, bean, outerEnvelope, ctx, decorations); // may throw H6 for share_history

        return new ValidationResult(
            decorations.stream().noneMatch(d -> DecorationSeverity.ERROR.equals(d.severity())),
            List.copyOf(decorations)
        );
    }

    // Step 0
    private static void validateProtocolVersion(BasicMessageEncryptedContentBean bean)
        throws HardProtocolViolationException { /* ... */ }

    // Step 1
    private static String validateActionRecognition(BasicMessageEncryptedContentBean bean)
        throws UnknownActionException { /* returns normalized lowercase, or null for absent */ }

    // Steps 2-5
    private static void validateCardinality(String action, BasicMessageEncryptedContentBean bean, List<Decoration> out) { /* ... */ }
    private static void validateTargetFormat(String action, BasicMessageEncryptedContentBean bean, List<Decoration> out) { /* ... */ }
    private static void validateTargetResolvable(String action, BasicMessageEncryptedContentBean bean, ValidationContext ctx, List<Decoration> out) { /* ... */ }
    private static void validateAuthorization(String action, BasicMessageEncryptedContentBean bean, BasicMessageRequestBean outer, ValidationContext ctx, List<Decoration> out) { /* ... */ }

    // Action-specific
    private static void validateActionSpecific(String action, BasicMessageEncryptedContentBean bean, BasicMessageRequestBean outer, ValidationContext ctx, List<Decoration> out)
        throws InnerSignatureFailureException { /* H6 thrown for share_history inner-signature failure */ }

    // Helper for the forward depth walk (caps at 10; emits FORWARD_DEPTH_EXCEEDED decoration if over)
    private static void validateForwardDepth(BasicMessageEncryptedContentBean bean, List<Decoration> out) { /* ... */ }

    private MessageActionValidator() {}
}
```

`ValidationContext` is a small record bundling the conversation creator PK lookup, local message cache lookup function, registered-user lookup function. Pure data; passed by the client.

#### 3.3.5 Validator behavior — hard/soft summary

| Step | Failure mode | Behavior |
|---|---|---|
| 0 | Version malformed / incompatible MAJOR / missing-with-features / declares-legacy-with-features | **Throw H1-H4.** Message dropped. |
| 1 | Action unrecognized (after case-insensitive normalization) | **Throw H5.** Message dropped. |
| 2 | Cardinality mismatch for known action | Decoration (`INVALID_<ACTION>_MALFORMED_TARGETS`, severity WARN). Body renders. |
| 3 | Target format mismatch (regex fail) | Decoration (`INVALID_<ACTION>_BAD_<SIGNATURE\|PUBLIC_KEY>_FORMAT`, severity WARN). Body renders. |
| 4 | Target not resolvable in local cache (signature-typed targets only) | Decoration (`BROKEN_REFERENCE_<ACTION>`, severity INFO). Body renders. |
| 5 | Authorization failure (self-author, creator-only) | Decoration (`UNAUTHORIZED_<ACTION>`, severity WARN). Body renders. |
| share_history inner-signature failure | Embedded signature fails verification | **Throw H6.** Message dropped (per data-integrity-first stance, §13 decision 11). |
| share_history cross-conversation | Embedded `conversation_hash_name` ≠ outer | Decoration (`INVALID_SHARE_HISTORY_CROSS_CONVERSATION`, severity ERROR — `overallValid: false`). Outer wrapper still renders. |
| share_history nested | Embedded inner bean has `action="share_history"` | Decoration (`INVALID_SHARE_HISTORY_NESTED`, severity WARN). Outer wrapper still renders. |
| Forward depth > 10 | `fw_content` chain too deep | Truncate deepest branch + Decoration (`FORWARD_DEPTH_EXCEEDED`, severity INFO). Body renders (truncated). |
| Inline content checks | Various (decode, size, hash, dimension, MIME) | Decoration with appropriate code; severity ERROR for hash mismatch (tamper signal), WARN otherwise. Parent message renders; inline content shown as broken. |

#### 3.3.6 Acceptance criteria

- **Hard violations (six):** `assertThrows(<Exception>.class, () -> validator.validate(...))` for each H1-H6 with a fixture demonstrating the violation. The exception's `getCode()` returns the canonical code string.
- **Soft violations (~18):** `assertEquals(<DecorationCode>, result.decorations().get(0).code())` for each soft case, with severity also asserted. Multiple-finding cases assert the decoration list size and contents.
- **Plain message (no action):** returns `ValidationResult.empty()` (overallValid=true, empty list).
- **Forward-compat synthetic test:** `assertThrows(UnknownActionException.class, ...)` for action `"__test_unknown_action__"`. The exception carries `getCode() == "INVALID_ACTION"` and `getOffendingValue() == "__test_unknown_action__"`.
- **Registry-completeness tests:** assert `MessageAction.KNOWN.equals(MessageActionMeta.registeredActions())` and `DecorationSeverity.KNOWN.size() == 3`.
- **Case-insensitive action normalization:** `"Reply"` and `"REPLY"` and `"reply"` all produce the same validation result.
- **Coverage target:** 85%+ line coverage on `MessageActionValidator`. 100% of `DecorationSeverity.KNOWN`, 100% of `MessageAction.KNOWN`, all decoration codes in `ValidationDecorationCodes.ALL` exercised at least once.

### 3.4 `ChatCryptoUtils` helpers — per-action with shared internal predicates (commit 4)

**Decision recap.** Per-action public methods (Option A — chosen for clarity over compactness; spec mapping visible inline). Internal helpers extracted for genuinely-shared work (regex predicates, the crypto path). No generic switch-on-action helpers (those are the anti-pattern the per-action choice was meant to avoid). Auto-stamp `client_protocol_version` always; `@VisibleForTesting` package-private variants for test-fixture generation.

#### 3.4.1 Files modified / created

| File | Change |
|---|---|
| `ChatCryptoUtils.java` | 10 new public methods + ~7 private/package-private helpers |
| `BasicMessageEncryptedContentBean.java` | (no change — `@lombok.Builder` was pre-applied during the plan-refinement phase; commit 1 §3.1 adds the remaining Jackson annotations) |
| `SendContext.java` (new) | Common context record passed as first parameter to every helper |
| `ChatCryptoConstructionException.java` (new) | Base exception for sender-side errors |
| `ForwardDepthExceededException.java` (new) | Typed subclass for depth-cap violation |
| `InvalidEmbeddedEnvelopeException.java` (new) | Typed subclass for share_history bad embeds |
| `InlineContentViolationException.java` (new) | Typed subclass for reaction inline-content limits |
| `MalformedTargetException.java` (new) | Typed subclass for signature/PK regex failures |

#### 3.4.2 Shared `SendContext` record

```java
public record SendContext(
    InstanceWalletKeystoreInterface signingWallet,
    int keyIndex,
    String conversationHashName,
    String conversationEncryptionKey,        // base64url of the AES-256 symmetric key
    Function<String, byte[]> keyDerivationFn  // nullable; null = default derivation
) {}
```

Carries the five recurring infrastructure parameters every helper needs. Each helper takes `SendContext ctx` as its first parameter; action-specific parameters follow.

#### 3.4.3 Internal helpers (shared across the 10 public methods)

| Internal helper | Used by | Responsibility |
|---|---|---|
| `validateSendContext(SendContext)` | all 10 | Null-check the record + required fields; throws `ChatCryptoConstructionException` |
| `validateSignatureFormat(String)` | reply, reaction, reaction_remove, edit, redact, pin | Apply `^[A-Za-z0-9_-]{86}\.\.$`; throws `MalformedTargetException` |
| `validatePublicKeyFormat(String)` | forward (when claimed origin set) | Apply `^[A-Za-z0-9_-]{43}\.$`; throws `MalformedTargetException` |
| `validatePinReason(String)` | pin | Length ≤ 200 (SHOULD); doesn't throw, logs WARN on overflow |
| `validateReactionPayload(ChatMediaPlaceholderBean)` | reaction | Inline limits + reaction MIME whitelist; throws `InlineContentViolationException` |
| `walkForwardDepth(BasicMessageEncryptedContentBean)` | forward | Returns int depth of `fw_content` chain (0 for leaf); helper uses to enforce ≤ 10 |
| `buildAndSign(SendContext, BasicMessageEncryptedContentBean, List<String> citedUsers)` | all 10 | Existing encrypt + sign + envelope-wrap path (currently `getBasicMessageBean(...)`); refactored to take the inner bean + cited_users |

Seven helpers total. Each is single-purpose, narrowly scoped, easy to test in isolation. **No `validateActionInputs(action, ...)` umbrella, no `buildSignatureTargetedBean(action, ...)` generic.** Per-action logic stays in the per-action public method, where the spec mapping is visible.

#### 3.4.4 Public method shape — one example

```java
public static BasicMessageRequestBean getReplyMessageBean(
    SendContext ctx,
    String parentSignature,
    String replyText,
    List<ChatMediaPlaceholderBean> attachedMedia,
    List<String> citedUsers
) throws ChatCryptoConstructionException, MalformedTargetException {
    validateSendContext(ctx);
    validateSignatureFormat(parentSignature);

    BasicMessageEncryptedContentBean inner = BasicMessageEncryptedContentBean.builder()
        .textMessage(replyText)
        .attachedMedia(attachedMedia)
        .action(MessageAction.REPLY)
        .targets(List.of(parentSignature))
        .clientProtocolVersion(MessageProtocolVersion.CURRENT)
        .build();

    return buildAndSign(ctx, inner, citedUsers);
}
```

Reader's flow: validate → construct per spec § registry row → sign and return. The spec mapping is visible inline. The crypto path is delegated. The validation predicates are named.

#### 3.4.5 Full public method list

Ten methods. Each follows the same internal structure (validate inputs → build bean via builder → `buildAndSign`):

```java
// Plain message (no action; helpers auto-stamp version)
public static BasicMessageRequestBean getPlainMessageBean(
    SendContext ctx, String textMessage,
    List<ChatMediaPlaceholderBean> attachedMedia, List<String> citedUsers
) throws ChatCryptoConstructionException;

public static BasicMessageRequestBean getReplyMessageBean(
    SendContext ctx, String parentSignature, String replyText,
    List<ChatMediaPlaceholderBean> attachedMedia, List<String> citedUsers
) throws ChatCryptoConstructionException, MalformedTargetException;

public static BasicMessageRequestBean getReactionMessageBean(
    SendContext ctx, String parentSignature,
    ChatMediaPlaceholderBean reactionPayload, List<String> citedUsers
) throws ChatCryptoConstructionException, MalformedTargetException, InlineContentViolationException;

public static BasicMessageRequestBean getReactionRemoveMessageBean(
    SendContext ctx, String parentSignature
) throws ChatCryptoConstructionException, MalformedTargetException;

public static BasicMessageRequestBean getEditMessageBean(
    SendContext ctx, String parentSignature, String newText,
    List<ChatMediaPlaceholderBean> newAttachedMedia, List<String> citedUsers
) throws ChatCryptoConstructionException, MalformedTargetException;

public static BasicMessageRequestBean getRedactMessageBean(
    SendContext ctx, String parentSignature, String optionalReason
) throws ChatCryptoConstructionException, MalformedTargetException;

public static BasicMessageRequestBean getPinMessageBean(
    SendContext ctx, String targetMessageSignature, String optionalReason
) throws ChatCryptoConstructionException, MalformedTargetException;

public static BasicMessageRequestBean getUnpinMessageBean(
    SendContext ctx
) throws ChatCryptoConstructionException;

public static BasicMessageRequestBean getForwardMessageBean(
    SendContext ctx, BasicMessageEncryptedContentBean beanToForward,
    String forwarderText, String claimedOriginPk
) throws ChatCryptoConstructionException, ForwardDepthExceededException;

public static BasicMessageRequestBean getShareHistoryMessageBean(
    SendContext ctx, BasicMessageRequestBean originalEnvelope,
    String relayerNote, boolean reShared
) throws ChatCryptoConstructionException, InvalidEmbeddedEnvelopeException;
```

#### 3.4.6 Auto-stamp version contract

Every helper sets `clientProtocolVersion = MessageProtocolVersion.CURRENT` via the builder's `.clientProtocolVersion(...)` step. **No public override.** Test fixtures requiring legacy / future-version beans use `@VisibleForTesting` package-private variants (e.g. `getReplyMessageBeanWithVersion(...)` in the same package; accessible only from `src/test/java`).

This ensures production callers never accidentally emit unversioned or mis-versioned v1.1+ messages.

#### 3.4.7 Exception hierarchy

```java
public class ChatCryptoConstructionException extends ChatMessageException {
    private final String code;
    public ChatCryptoConstructionException(String code, String message) { ... }
    public String getCode() { return code; }
    // Public string-constants for known codes — see below
}

public class MalformedTargetException extends ChatCryptoConstructionException { ... }
public class ForwardDepthExceededException extends ChatCryptoConstructionException {
    private final int actualDepth, maxDepth;
    // accessors for UI: "current chain is N levels, max is 10"
}
public class InvalidEmbeddedEnvelopeException extends ChatCryptoConstructionException { ... }
public class InlineContentViolationException extends ChatCryptoConstructionException { ... }
```

Code constants on `ChatCryptoConstructionException` (public static final String):
- `MISSING_PARENT_SIGNATURE`, `MISSING_ORIGINAL_ENVELOPE`, `MISSING_TARGET_MESSAGE_SIGNATURE`, `MISSING_REACTION_PAYLOAD`
- `MALFORMED_PARENT_SIGNATURE`, `MALFORMED_CLAIMED_ORIGIN_PK`, `MALFORMED_TARGET_MESSAGE_SIGNATURE`
- `FORWARD_DEPTH_EXCEEDED`
- `EMBEDDED_INNER_SIGNATURE_INVALID`, `EMBEDDED_INNER_CONVERSATION_MISMATCH`, `NESTED_SHARE_HISTORY`
- `INLINE_CONTENT_TOO_LARGE`, `INLINE_CONTENT_DIMENSION_VIOLATION`, `INLINE_DECODE_FAILURE`
- `REACTION_MIME_NOT_ALLOWED`
- `PIN_REASON_TOO_LONG` (warning only; not thrown)
- `INCOHERENT_BEAN_CONSTRUCTION` (catch-all)

#### 3.4.8 Acceptance

- Each public method: happy-path test + at least one bad-input `assertThrows` test per checked exception type it declares.
- `ForwardDepthExceededException` test asserts the `actualDepth` accessor matches the input.
- `MessageAction` registry-completeness sibling test: assert every entry in `MessageAction.KNOWN` has a corresponding `get*MessageBean` helper.
- Auto-stamp test: assert every helper's output bean has `clientProtocolVersion == MessageProtocolVersion.CURRENT`.
- Builder-vs-AllArgsConstructor parity test: `BasicMessageEncryptedContentBean.builder().textMessage("x").build()` and `new BasicMessageEncryptedContentBean("x", null, null, null, null, null, null, null)` produce equal beans.
- Coverage target: 90%+ on `ChatCryptoUtils` (helpers are small and well-tested per-method).

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

- `MessageAction` constants + `MessageActionMeta` registry — canonical lowercase wire-string per constant; `isKnown` membership check (positive + negative + case-variant); `MessageActionMeta.lookup` returns correct `ActionSpec` for each registered action and empty `Optional` for unknown/null/whitespace input; **registry-completeness test** (`MessageAction.KNOWN == MessageActionMeta.registeredActions()`). Target: 100% of `MessageAction.KNOWN` set members, all three normalization paths (lowercase, mixed-case, null).
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
2. All new tests pass (target: 80%+ coverage on `MessageActionValidator`, 100% of constants in `MessageAction.KNOWN` plus the registry-completeness test).
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
