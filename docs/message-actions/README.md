# Message Actions — per-action reference (Messages 1.5.0)

**Module:** `io.takamaka.messages:messages` `1.5.0-SNAPSHOT` · **Java:** 17 · **Wire protocol:** `client_protocol_version = "1.1"`

This subsection documents each of the nine message actions
(`reply`, `reaction`, `reaction_remove`, `edit`, `redact`, `pin`, `unpin`,
`forward`, `share_history`) with the **exact JSON it produces on the wire**,
the validator outcome, the sender/receiver code, and the shell CLI form.

## How these docs relate to the other action docs (read this first)

There are four artifacts describing this protocol. They do **not** compete — each
has one job:

| Artifact | Role | When to read it |
|---|---|---|
| [`roadmap/MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md`](../roadmap/MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md) | **Normative** protocol prose (the "law") | Settling *what the rule is* |
| [`architecture/MESSAGE_ACTIONS_API_1_5_0.md`](../architecture/MESSAGE_ACTIONS_API_1_5_0.md) | Consumer **API reference** (all signatures, types, one-pass overview) | Calling the library |
| **this subsection** | **Per-action JSON-by-example + edge cases + CLI**, one page per action | Implementing/debugging one specific action |
| [`src/test/resources/cross-platform-vectors/fixtures/`](../../src/test/resources/cross-platform-vectors/fixtures/) | **Source of truth** — 37 CI-validated golden vectors | Verifying anything above |

### Page structure (the hybrid, and why)

Each per-action page has two parts:

1. **Canonical wire format (fixture-anchored).** Every JSON block here is copied
   **verbatim from a named fixture** and tagged with an HTML marker
   (`<!-- fixture: V03_reply_text_only/expected_inner_plaintext.json -->`).
   [`verify_docs_against_fixtures.sh`](verify_docs_against_fixtures.sh) asserts these
   blocks still equal their fixtures, so this part **cannot silently drift**. This
   is the *only* place authoritative JSON appears on a page.
2. **Standalone reference** — purpose, sender helper, validator rules, field
   semantics, and shell CLI. This part is intentionally self-contained (some
   overlap with the API doc is accepted for readability); wherever it restates a
   signature or rule it cites `class:method:line` so it stays checkable. It never
   introduces a second, hand-edited JSON — it points back to the anchored block.

> Design rationale: drift risk is concentrated in the one zone that is machine-verified
> (the wire JSON, where a mistake is catastrophic); the prose around it changes rarely.

## Shared model (applies to every action)

The action layer rides **inside** the AES-256 `encrypted_content` of the standard
signed envelope `BasicMessageRequestBean`. The server is **action-blind**: it stores
the envelope keyed by its Ed25519 `signature` and never sees `action`, `targets`,
`fw_content`, `original_message`, `re_shared`, or `text_message`. See API doc §5 for
the wire envelope; the inner decrypted bean is `BasicMessageEncryptedContentBean`.

### Inner-plaintext fields (`BasicMessageEncryptedContentBean`)

`@JsonInclude(NON_NULL)` omits unset fields; keys are emitted in canonical
(alphabetical) order for signing.

| Wire key | Java field | Type | Used by |
|---|---|---|---|
| `action` | `action` | string | all actions (absent ⇒ plain message) |
| `attached_media` | `attachedMedia` | `ChatMediaPlaceholderBean[]` | plain, reply, reaction, edit |
| `client_protocol_version` | `clientProtocolVersion` | `"1.1"` | every v1.1 helper |
| `fw_content` | `fwContent` | **recursive** `BasicMessageEncryptedContentBean` | forward |
| `original_message` | `originalMessage` | `BasicMessageRequestBean` (full signed envelope) | share_history |
| `re_shared` | `reShared` | boolean (omitted when false) | share_history re-share |
| `targets` | `targets` | string[] | reply, reaction, reaction_remove, edit, redact, pin, forward |
| `text_message` | `textMessage` | string | plain, reply, edit, redact reason, forward note, share note |

### Target formats (enforced at construction)

| Target kind | Regex | Used by |
|---|---|---|
| Message signature | `^[A-Za-z0-9_-]{86}\.\.$` | reply, reaction, reaction_remove, edit, redact, pin |
| Public key | `^[A-Za-z0-9_-]{43}\.$` | forward (claimed-origin, optional) |

> **CLI note (dash-safe):** a base64url signature/address can start with `-`.
> Shell action commands must pass it as a **double-quoted explicit option**
> (`reply --parentSignature "<sig>" ...`); positional / `--opt=val` / unquoted forms
> break. See `SHELL_BOOTSTRAP §9.1 #8`.

## The nine actions

| Action | Page | Wire `action` | Sender helper (`ChatCryptoUtils.java`) | Shell command | Key fixtures |
|---|---|---|---|---|---|
| Reply | [reply.md](reply.md) | `reply` | `getReplyMessageBean` (:618) | `reply` | V03, V04 / I08, I09, I11 |
| Reaction | [reaction.md](reaction.md) | `reaction` | `getReactionMessageBean` (:636) | `react` | V05–V07 / I17, I18 |
| Reaction remove | [reaction-remove.md](reaction-remove.md) | `reaction_remove` | `getReactionRemoveMessageBean` (:659) | `react-remove` | V08 |
| Edit | [edit.md](edit.md) | `edit` | `getEditMessageBean` (:674) | `edit` | V09, V10 / I12 |
| Redact | [redact.md](redact.md) | `redact` | `getRedactMessageBean` (:692) | `redact` | V11, V12 |
| Pin | [pin.md](pin.md) | `pin` | `getPinMessageBean` (:708) | `pin` | V13 / I13 |
| Unpin | [unpin.md](unpin.md) | `unpin` | `getUnpinMessageBean` (:725) | `unpin` | V14 |
| Forward | [forward.md](forward.md) | `forward` | `getForwardMessageBean` (:735) | `forward` | V15–V17 / I10, I16 |
| Share history | [share-history.md](share-history.md) | `share_history` | `getShareHistoryMessageBean` (:762) | `share-history` | V18, V19 / I07, I14, I15 |

(Baseline plain messages use `getPlainMessageBean` (:605); see fixtures V01/V02.)

## Protocol-version gate (applies before any action dispatch)

`MessageActionValidator.validate` first runs the version gate. These are **hard**
violations — the message is **dropped** (a typed `HardProtocolViolationException` is
thrown; the receiver renders nothing):

| Condition | Exception | Code | Fixture |
|---|---|---|---|
| Patch level (`1.0.0`) / prefixed (`v1.0`) version | `MalformedProtocolVersionException` | `INVALID_PROTOCOL_VERSION_MALFORMED` | I01, I02 |
| Incompatible MAJOR (`2.0`) | `IncompatibleMajorVersionException` | `INVALID_PROTOCOL_VERSION_INCOMPATIBLE_MAJOR` | I03 |
| No version but action/feature fields present | `MissingVersionWithFeaturesException` | `INVALID_PROTOCOL_VERSION_MISSING_WITH_FEATURES` | I04 |
| Legacy version (`1.0`) with feature fields | `LegacyVersionWithFeaturesException` | `INVALID_PROTOCOL_VERSION_LEGACY_WITH_FEATURES` | I05 |
| Unknown action string | `UnknownActionException` | `INVALID_ACTION` | I06 |

A v1.0 / no-version message with **no** feature fields is a valid legacy plain
message (V01). The current sender always stamps `"1.1"` (V02).

## Hard vs soft outcomes

- **Hard** (drop): version gate + unknown action + `share_history` embedded
  inner-signature failure → `HardProtocolViolationException`.
- **Soft** (render the body, attach a `Decoration`): cardinality / target-format /
  broken-reference / authorization / forward-depth / share_history structural /
  inline-content issues. `overallValid` is `false` **iff** a decoration is `ERROR`
  (today: `INVALID_SHARE_HISTORY_CROSS_CONVERSATION`, `INLINE_HASH_MISMATCH`);
  everything else is `WARN` or `INFO` and still renders.

The closed code set is `ValidationDecorationCodes.ALL`
(`chat/message/ValidationDecorationCodes.java`).

## Verifying these docs

```bash
# from the repo (Messages built at least once so the fixtures are present)
bash Messages/docs/message-actions/verify_docs_against_fixtures.sh
```

It pairs every `<!-- fixture: ... -->` block to its file under
`src/test/resources/cross-platform-vectors/fixtures/` and fails on any mismatch.
The fixtures themselves are validated against the code by `CrossPlatformVectorTest`.

## Cross-platform (Dart port)

Parity is verified by validation-outcome agreement on the frozen fixtures, not by
byte-identical regeneration (AES IV / PBKDF2 salt are random per call). Encoding
asymmetry: `ChatMediaPlaceholderBean.preview` is **standard Base64**; all hash
fields (signatures, `unencrypted_content_hash`, conversation hashes) are
**Base64URL with `.` padding**. See API doc §6.
