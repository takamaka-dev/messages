# Forward

Re-sends another message's content into the current conversation. The forwarded body
is **copied** into `fw_content` (a nested `BasicMessageEncryptedContentBean`); the
original signature is **not** preserved — a forward is an *unattributed claim*, not a
cryptographically verifiable relay (for that, see [share-history](share-history.md)).

`targets[0]` is the **claimed-origin public key** (43 chars + `.`) for an attributed
forward, or `targets` is empty for an anonymous one. Forwards nest recursively and are
capped at depth **10** (`MessageActionValidator.MAX_FORWARD_DEPTH`,
`chat/message/MessageActionValidator.java:66`).

→ [index](README.md) · [share-history](share-history.md) · [API doc §4.2/§4.3](../architecture/MESSAGE_ACTIONS_API_1_5_0.md) · [spec §12.10](../roadmap/MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md)

## 1. Canonical wire format (fixture-anchored)

### Attributed, depth 1

`targets[0]` = claimed origin public key; `fw_content` = the forwarded body.

<!-- fixture: V15_forward_attributed/expected_inner_plaintext.json -->
```json
{
  "text_message" : "fyi",
  "action" : "forward",
  "targets" : [ "9xIsD_XELYretqJPxcXdDD1qIFGe5v-2mOktmIjRaTo." ],
  "fw_content" : {
    "text_message" : "forwarded body"
  },
  "client_protocol_version" : "1.1"
}
```

<!-- fixture: V15_forward_attributed/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ ]
}
```

### Anonymous, depth 1

Empty `targets` ⇒ the forwarder makes no origin claim.

<!-- fixture: V16_forward_anonymous/expected_inner_plaintext.json -->
```json
{
  "text_message" : "fyi",
  "action" : "forward",
  "targets" : [ ],
  "fw_content" : {
    "text_message" : "forwarded body"
  },
  "client_protocol_version" : "1.1"
}
```

### Recursive `fw_content` — depth 3 (forward of a forward of a forward)

Each forward layer wraps the previous body in a fresh `fw_content`. Depth here is 3
(`fw_content` chain length); the validator counts the chain and the construction helper
rejects anything that would exceed 10.

<!-- fixture: V17_forward_depth_3/expected_inner_plaintext.json -->
```json
{
  "text_message" : "fyi",
  "action" : "forward",
  "targets" : [ ],
  "fw_content" : {
    "text_message" : "n1",
    "fw_content" : {
      "text_message" : "n0",
      "fw_content" : {
        "text_message" : "leaf"
      }
    }
  },
  "client_protocol_version" : "1.1"
}
```

<!-- fixture: V17_forward_depth_3/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ ]
}
```

### Edge cases

Claimed-origin not matching the public-key regex → `INVALID_FORWARD_BAD_PUBLIC_KEY_FORMAT`
(WARN), fixture `I10_forward_bad_public_key_format`:

<!-- fixture: I10_forward_bad_public_key_format/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ {
    "code" : "INVALID_FORWARD_BAD_PUBLIC_KEY_FORMAT",
    "severity" : "WARN"
  } ]
}
```

Chain depth over the cap (here depth 11) → `FORWARD_DEPTH_EXCEEDED` (INFO); the
validator **truncates** the deepest branch rather than dropping. Fixture
`I16_forward_depth_exceeded`:

<!-- fixture: I16_forward_depth_exceeded/expected_validation.json -->
```json
{
  "type" : "result",
  "overallValid" : true,
  "decorations" : [ {
    "code" : "FORWARD_DEPTH_EXCEEDED",
    "severity" : "INFO"
  } ]
}
```

## 2. Reference

**Sender** — `ChatCryptoUtils.getForwardMessageBean` (`utils/ChatCryptoUtils.java:735`):

```java
getForwardMessageBean(SendContext ctx, BasicMessageEncryptedContentBean beanToForward,
        String forwarderText, String claimedOriginPk)   // claimedOriginPk null ⇒ anonymous
    throws ChatCryptoConstructionException, ForwardDepthExceededException;
```

`beanToForward` becomes the new `fw_content`. `prospectiveDepth = 1 + walkForwardDepth(beanToForward)`;
if it exceeds `MAX_FORWARD_DEPTH` (10) the helper throws `ForwardDepthExceededException`
(`ChatCryptoUtils.java:746-748`).

**Receiver** — soft action. The validator walks the `fw_content` chain; format issues
on the claimed origin are `WARN`, an over-cap chain is `INFO` (with truncation). The
receiver-side `cacheMessagePlaceholders` walks `fw_content` **recursively** so nested
attachments are surfaced for download.

### Forward-with-attachment: per-layer re-encryption (spec §12.10.1)

A forwarded **server-stored** attachment cannot be reused as-is: the placeholder
references a blob encrypted under the *source* conversation key, which a destination
member does not hold. The forwarder therefore **re-encrypts and re-uploads** the blob
under the destination conversation key and rewrites the placeholder — **recursively at
every `fw_content` layer**, keeping each re-linked placeholder inside the layer that
carries it (never flattened). In the shell this is
`ChatCommandsAttachments.reEncryptAndReuploadAttachment` +
`ChatCommandMessages.relinkForwardAttachments`. Inline attachments
(`is_the_object=true`) need no re-upload — their `preview` rides inside the re-encrypted
`fw_content` body.

> Live-proven at depth-1 **and** depth-2 by
> `shell/tests/integration/test_forward_attachment.sh`: a member holding only the
> destination key decrypts a (twice-)re-encrypted blob whose hash differs from every
> upstream layer. See `SHELL_BOOTSTRAP §9.1 #7`.

**Shell CLI** (dash-safe; `ChatCommands.forward`; attachment re-encryption is automatic):

```
forward --sourceSignature "<sourceMsgSig>" --text "<your note>"
forward --sourceSignature "<sourceMsgSig>" --text "<note>" --claimedOriginPublicKey "<pk>"
```

The source is looked up from the receiver cache by signature, so retrieve the source
conversation's history first.
