# Share history

Re-shares an earlier message to late joiners **with cryptographic provenance**. Unlike
[forward](forward.md) (an unverifiable copy), share_history embeds the **original,
still-signed envelope** in `original_message`, so any conversation member can verify the
original author's Ed25519 signature and decrypt the original `encrypted_content` with the
shared conversation key. `re_shared` distinguishes a first share from a relay-of-a-relay.

→ [index](README.md) · [forward](forward.md) · [API doc §4.4](../architecture/MESSAGE_ACTIONS_API_1_5_0.md) · [spec §12.11](../roadmap/MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md)

## 1. Canonical wire format (fixture-anchored)

### First share (`re_shared` absent)

`original_message` is a complete `BasicMessageRequestBean` — the original `from`,
`signature`, and `encrypted_content` carried verbatim.

<!-- fixture: V18_share_history_first/expected_inner_plaintext.json -->
```json
{
  "text_message" : "sharing history",
  "action" : "share_history",
  "original_message" : {
    "from" : "9xIsD_XELYretqJPxcXdDD1qIFGe5v-2mOktmIjRaTo.",
    "signature" : "bPSUbr5g9Plwt_JSFKxPMlTICUisvyDQzY3jloCo7VlQbPMhC-qOFnltCx752ylH2jXRtkKePbYWawcHtWGrCw..",
    "message_type" : "TOPIC_MESSAGE",
    "signature_type" : "Ed25519BC",
    "basic_message_signed_content_bean" : {
      "conversation_hash_name" : "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd",
      "cited_users" : null,
      "encrypted_content" : {
        "pa" : "PBKDF2WithHmacSHA512",
        "it" : 20000,
        "tr" : "AES/CBC/PKCS5Padding",
        "ka" : "AES",
        "tv" : "v0_1_a",
        "kl" : 256,
        "ec" : "UTF-8",
        "em" : [ "nhso6xq1Q2F48yfHtliZFw..", "AmylWQ1pM7YH1IMqXnN1k0KniKoszZSVV1rp5xJKpFyD451mebg0vebVC6a5Qs1KkxJVhQV9yR2A_K36Ur1nPjbLdBVNWp4OGbVjRsBu-gQ." ]
      }
    }
  },
  "client_protocol_version" : "1.1"
}
```

<!-- fixture: V18_share_history_first/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ ]
}
```

### Re-share (`re_shared = true`)

A relay of a previously-shared original. Same embedded-envelope shape, with the flag set.

<!-- fixture: V19_share_history_reshare/expected_inner_plaintext.json -->
```json
{
  "text_message" : "re-sharing",
  "action" : "share_history",
  "original_message" : {
    "from" : "9xIsD_XELYretqJPxcXdDD1qIFGe5v-2mOktmIjRaTo.",
    "signature" : "UMk4qQI4F8Lkeiiu-WkIufjwmV1F-A6NwS9aoCcqmm7ZgWk7_FRJJMMivlXxeroWfyHlB72JQQHOhf-BFugmDQ..",
    "message_type" : "TOPIC_MESSAGE",
    "signature_type" : "Ed25519BC",
    "basic_message_signed_content_bean" : {
      "conversation_hash_name" : "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd",
      "cited_users" : null,
      "encrypted_content" : {
        "pa" : "PBKDF2WithHmacSHA512",
        "it" : 20000,
        "tr" : "AES/CBC/PKCS5Padding",
        "ka" : "AES",
        "tv" : "v0_1_a",
        "kl" : 256,
        "ec" : "UTF-8",
        "em" : [ "Tz3-cxblHTEuC0c_JDdy1w..", "wpvcLCoADdfHvaiE70TGE439dxw4WtjaV1c0iIk9rdsXdJVa9aywgFHByR-eT-RggWrKRTqd-jiEYRYWt1ICioULDZu4ARGxEdE0M-fhYrI." ]
      }
    }
  },
  "re_shared" : true,
  "client_protocol_version" : "1.1"
}
```

### Edge cases

**Hard** — embedded inner signature does not verify → `InnerSignatureFailureException`
(`INNER_SIGNATURE_FAILURE`); the message is **dropped**, not rendered. Fixture
`I07_share_history_bad_inner_signature`:

<!-- fixture: I07_share_history_bad_inner_signature/expected_validation.json -->
```json
{
  "type" : "throws",
  "exception" : "InnerSignatureFailureException",
  "code" : "INNER_SIGNATURE_FAILURE"
}
```

**Soft ERROR** — embedded original belongs to a different conversation →
`INVALID_SHARE_HISTORY_CROSS_CONVERSATION` (`overallValid=false`). Fixture
`I14_share_history_cross_conversation`:

<!-- fixture: I14_share_history_cross_conversation/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : false,
  "decorations" : [ {
    "code" : "INVALID_SHARE_HISTORY_CROSS_CONVERSATION",
    "severity" : "ERROR"
  } ]
}
```

**Soft WARN** — the embedded original is itself a share_history (nesting) →
`INVALID_SHARE_HISTORY_NESTED`. Fixture `I15_share_history_nested`:

<!-- fixture: I15_share_history_nested/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ {
    "code" : "INVALID_SHARE_HISTORY_NESTED",
    "severity" : "WARN"
  } ]
}
```

## 2. Reference

**Sender** — `ChatCryptoUtils.getShareHistoryMessageBean` (`utils/ChatCryptoUtils.java:762`):

```java
getShareHistoryMessageBean(SendContext ctx, BasicMessageRequestBean originalEnvelope,
        String relayerNote, boolean reShared)
    throws ChatCryptoConstructionException, InvalidEmbeddedEnvelopeException;
```

Construction guards — the embedded `originalEnvelope` must (a) carry a valid inner
Ed25519 signature, (b) belong to the **same** conversation as `ctx.conversationHashName`,
and (c) **not** itself be a share_history. Violations throw
`InvalidEmbeddedEnvelopeException` with the matching code. `re_shared=false` is omitted
from the wire (`@JsonInclude(NON_NULL)`); `true` is emitted (V19).

**Receiver** — the embedded inner-signature failure is the only **hard** case here
(drop). Cross-conversation is the `ERROR` decoration (sets `overallValid=false`);
nesting is a `WARN`. The body always renders for the soft cases.

> **Shell caveat (F5):** the shell `share-history` command **rejects a
> cross-conversation source at compose** (`embedded original_message belongs to a
> different conversation`), so the receive-side
> `INVALID_SHARE_HISTORY_CROSS_CONVERSATION` decoration is not reachable through the
> normal shell path — it is exercised by fixture `I14` and the validator directly.
> See `MESSAGE_ACTION_TESTPLAN.md` F5.

**Shell CLI** (dash-safe; `ChatCommands.shareHistory`; `--note`/`--reShared` optional):

```
share-history --sourceSignature "<originalMsgSig>" --note "<relay note>"
share-history --sourceSignature "<originalMsgSig>" --note "<note>" --reShared true
```

The source is resolved from the receiver cache by signature (retrieve its conversation's
history first), and must be in the *current* conversation.
