# Mention Utilities API Reference

**Version:** 1.3.0
**Package:** `io.takamaka.messages.chat.mention`
**Since:** 1.3.0

## Overview

The mention utilities provide parsing, validation, and rendering capabilities for user mentions in E2E encrypted chat messages. These utilities support dual mention syntax to preserve privacy in zero-knowledge server architectures:

- **@ mentions (public):** Server-visible mentions added to `citedUsers` field
- **# mentions (private):** Client-only mentions that remain encrypted

## Supported Key Formats

All utilities support four public key formats:

| Format | Length | Character Set | Example Use Case |
|--------|--------|---------------|------------------|
| **Ed25519** | 44 chars | `[a-zA-Z0-9-_.]` | Classical signatures |
| **Short B64** | 64 chars | `[a-zA-Z0-9-_.]` | Alternative encoding |
| **Short Hex** | 96 chars | `[a-zA-Z0-9]` | Hexadecimal keys |
| **qTesla** | 19,840 chars | `[a-zA-Z0-9-_.]` | Post-quantum signatures |

---

## AddressValidation

Validates and categorizes public key formats.

### KeyType Enum

```java
public enum KeyType {
    ED25519(44, "^[a-zA-Z0-9-_.]{44}$"),
    SHORT_B64(64, "^[a-zA-Z0-9-_.]{64}$"),
    SHORT_HEX(96, "^[a-zA-Z0-9]{96}$"),
    QTESLA(19840, "^[a-zA-Z0-9-_.]{19840}$"),
    INVALID(0, "")
}
```

#### Methods
- `int getLength()` - Returns character length for this key type
- `String getRegex()` - Returns validation regex pattern

### Static Methods

#### `isValidPublicKey(String key)`

Validates if a string matches any supported key format.

```java
boolean isValid = AddressValidation.isValidPublicKey(
    "7tVZucubIlMg2eWX6CzOOOU1-GMPQmNFK-R4i1tczms."
);
// Returns: true
```

**Parameters:**
- `key` - Public key string to validate

**Returns:** `true` if valid format, `false` otherwise

**Behavior:**
- Returns `false` for `null` or empty strings
- Validates against all four key type patterns
- Pattern matching is case-sensitive

---

#### `getKeyType(String key)`

Determines the key type from a public key string.

```java
KeyType type = AddressValidation.getKeyType(
    "f54848b4c119be770f87bc5ef54848b4c119be770f87bc5ef54848b4c119be770f87bc5ef54848b4c119be770f87bc5e"
);
// Returns: KeyType.SHORT_HEX
```

**Parameters:**
- `key` - Public key string

**Returns:** `KeyType` enum value (or `INVALID` if no match)

**Use Cases:**
- Format detection before processing
- Routing to type-specific handlers
- Display customization by key type

---

#### `abbreviateForDisplay(String key)`

Abbreviates public keys for display purposes.

```java
// Ed25519 key - returned unchanged
String abbreviated = AddressValidation.abbreviateForDisplay(
    "7tVZucubIlMg2eWX6CzOOOU1-GMPQmNFK-R4i1tczms."
);
// Returns: "7tVZucubIlMg2eWX6CzOOOU1-GMPQmNFK-R4i1tczms."

// qTesla key - abbreviated to 96-char hex hash
String abbreviated = AddressValidation.abbreviateForDisplay(qTeslaKey);
// Returns: "f54848b4c119be77..." (96 chars, SHA3-384 hash)
```

**Parameters:**
- `key` - Full public key

**Returns:** Abbreviated string suitable for display

**Behavior:**
- **Ed25519/Short formats:** Returns full key (readable length)
- **qTesla (19,840 chars):** Returns 96-char hex hash (SHA3-384)
- **Null/empty:** Returns empty string `""`
- **Invalid format:** Fallback to truncated display with ellipsis

**Implementation Detail:**
qTesla abbreviation uses `TkmSignUtils.Hash384B64URL()` followed by `TkmSignUtils.fromB64UrlToHEX()` for consistent, collision-resistant hashing.

---

## MentionParser

Extracts and validates mentions from message text.

### ParseResult Class

Container for parsed mention data.

```java
@Data
@AllArgsConstructor
public static class ParseResult {
    private String protocolText;
    private List<String> publicMentions;
    private List<String> privateMentions;
}
```

#### Fields

- **`protocolText`** - Message text (currently unchanged from input)
- **`publicMentions`** - List of public keys from @ mentions (for `citedUsers` field)
- **`privateMentions`** - List of public keys from # mentions (logging only, NOT for `citedUsers`)

### Static Methods

#### `parseMessage(String userText, List<String> conversationMembers)`

Parses message text and extracts validated mentions.

```java
List<String> members = Arrays.asList(
    "7tVZucubIlMg2eWX6CzOOOU1-GMPQmNFK-R4i1tczms.",
    "abc123XYZ_-.def456GHI789jkl012MNO345pqr678ST"
);

ParseResult result = MentionParser.parseMessage(
    "Hey @7tVZ...czms check #abc1...78ST",
    members
);

// Use result.getPublicMentions() for citedUsers field
List<String> citedUsers = result.getPublicMentions();
```

**Parameters:**
- `userText` - Message text containing mentions
- `conversationMembers` - List of valid public keys in the conversation

**Returns:** `ParseResult` with extracted and validated mentions

**Throws:** `InvalidMentionException` if:
- `userText` is `null`
- `conversationMembers` is `null` or empty
- Mentioned key has invalid format
- Mentioned user is not in `conversationMembers` list

**Behavior:**
- Extracts both @ and # mentions using regex matching
- Validates each mention against conversation membership
- Deduplicates mentions (same key mentioned multiple times)
- Preserves original message text in `protocolText`
- Pattern matching is greedy (longest match first to handle overlapping lengths)

**Security Consideration:**
Only public mentions (`@`) should be added to the `citedUsers` field. Private mentions (`#`) remain encrypted and server-invisible.

---

## MentionRenderer

Renders mentions with abbreviated keys for display.

### Static Methods

#### `renderForDisplay(String messageText)`

Renders message text with abbreviated mentions for display.

```java
String displayText = MentionRenderer.renderForDisplay(
    "Hey @7tVZucubIlMg2eWX6CzOOOU1-GMPQmNFK-R4i1tczms. check this"
);
// Returns: "Hey @7tVZucubIlMg2eWX6CzOOOU1-GMPQmNFK-R4i1tczms. check this"
// (Ed25519 unchanged)

String displayText = MentionRenderer.renderForDisplay(
    "Hey @" + qTeslaKey + " check this"
);
// Returns: "Hey @f54848b4c119be77... check this"
// (qTesla abbreviated to 96-char hex)
```

**Parameters:**
- `messageText` - Raw message text with mentions

**Returns:** Text with abbreviated mentions suitable for display

**Behavior:**
- **Ed25519/Short keys:** Displayed in full (readable length)
- **qTesla keys:** Displayed as 96-char hex hash (SHA3-384)
- **Both @ and # mentions** are processed identically
- **Null/empty:** Returns input unchanged
- Preserves message structure and non-mention content

**Use Cases:**
- Display messages in chat UI
- Show mentions in notifications
- Render message previews

---

#### `renderKey(String publicKey)`

Renders a single public key for display (without mention syntax).

```java
String displayKey = MentionRenderer.renderKey(qTeslaKey);
// Returns: "f54848b4c119be77..." (96 chars)
```

**Parameters:**
- `publicKey` - Full public key

**Returns:** Abbreviated key suitable for display

**Behavior:**
- Delegates to `AddressValidation.abbreviateForDisplay()`
- Convenient for displaying keys outside of message context

**Use Cases:**
- User list displays
- Key management interfaces
- Standalone key presentation

---

## InvalidMentionException

Exception thrown when mention validation fails.

```java
public class InvalidMentionException extends Exception {
    public InvalidMentionException(String message)
    public InvalidMentionException(String message, Throwable cause)
}
```

### Causes

- Invalid public key format
- Mentioned user not in conversation
- Malformed mention syntax
- Null or empty input

### Example Handling

```java
try {
    ParseResult result = MentionParser.parseMessage(userInput, members);
    // Process result...
} catch (InvalidMentionException e) {
    // Display user-friendly error
    System.err.println("Mention error: " + e.getMessage());
}
```

**Error Messages:**
- `"Message text cannot be null"`
- `"Conversation members list cannot be empty"`
- `"Invalid public key format: <abbreviated-key>"`
- `"User not in conversation: <abbreviated-key>"`

---

## Integration Examples

### Basic Message Send with Public Mentions

```java
// 1. Parse user input
List<String> conversationMembers = getConversationMembers();
ParseResult parsed = MentionParser.parseMessage(userInput, conversationMembers);

// 2. Create signed message request
BasicMessageRequestBean request = new BasicMessageRequestBean();
request.setMessage(parsed.getProtocolText());
request.setCitedUsers(parsed.getPublicMentions()); // Only @ mentions

// 3. Send to server
sendMessage(request);
```

### Display Received Message

```java
// 1. Retrieve message from server
MessageBean message = receiveMessage();

// 2. Render for display
String displayText = MentionRenderer.renderForDisplay(message.getText());

// 3. Show in UI
chatView.appendMessage(displayText);
```

### Validate Mention Before Sending

```java
try {
    ParseResult result = MentionParser.parseMessage(
        userInput,
        conversationMembers
    );

    if (!result.getPrivateMentions().isEmpty()) {
        // Inform user about private mentions (optional)
        logger.info("Message contains {} private mentions",
                    result.getPrivateMentions().size());
    }

    // Proceed with message send

} catch (InvalidMentionException e) {
    // Show error to user
    showError("Invalid mention: " + e.getMessage());
}
```

### Display User Key in Member List

```java
List<String> members = getConversationMembers();

for (String publicKey : members) {
    String displayKey = MentionRenderer.renderKey(publicKey);
    System.out.println("Member: " + displayKey);
}
```

---

## Privacy Architecture

### @ Mentions (Public)

- **Server visibility:** YES
- **Added to `citedUsers` field:** YES
- **Server can index/search:** YES
- **Notification routing:** Server can notify mentioned users

**Use case:** Convenience for server-assisted features like notifications and mention indexing.

### # Mentions (Private)

- **Server visibility:** NO (encrypted)
- **Added to `citedUsers` field:** NO
- **Server can index/search:** NO
- **Notification routing:** Client-side only

**Use case:** Maximum privacy when mentioning users in E2E encrypted conversations.

### Security Implications

1. **@-mentions leak metadata** to the server (who mentioned whom)
2. **#-mentions preserve privacy** but disable server-assisted notifications
3. **Default recommendation:** Use @ for convenience, # for sensitive contexts
4. **Validation:** Both types validated against conversation membership to prevent non-member mentions

---

## Performance Considerations

### Parsing

- **Regex complexity:** O(n) where n = message length
- **Validation:** O(m) where m = number of mentions
- **Membership check:** O(k) where k = conversation size (uses `List.contains()`)

**Optimization tip:** For large conversations, consider using `HashSet` for `conversationMembers` parameter.

### Rendering

- **qTesla abbreviation:** SHA3-384 hash computed once per unique key
- **Caching:** Consider caching abbreviated qTesla keys in long conversations

---

## Testing

### Unit Test Coverage

- **AddressValidation:** 15 test cases (format validation, type detection, abbreviation)
- **MentionParser:** 17 test cases (extraction, validation, edge cases)
- **MentionRenderer:** 23 test cases (rendering, abbreviation, consistency)

### Test Utilities

Sample keys provided in test classes:

```java
private static final String ED25519_KEY = "7tVZucubIlMg2eWX6CzOOOU1-GMPQmNFK-R4i1tczms.";
private static final String QTESLA_KEY = "a".repeat(19840);
```

Run all mention utility tests:

```bash
mvn test -Dtest="*Mention*Test"
```

---

## Dependencies

### Required

- `lombok` - For `@Data` and `@AllArgsConstructor` annotations
- `io.takamaka.wallet.utils.TkmSignUtils` - For SHA3-384 hashing and hex conversion

### Optional

- `org.slf4j` - For logging (if added to utilities)

---

## Version History

### 1.3.0 (Current)

- Initial release of mention utilities
- Support for dual mention syntax (@ and #)
- Four key format support (Ed25519, Short B64, Short Hex, qTesla)
- SHA3-384 abbreviation for qTesla keys
- Comprehensive unit test coverage

---

## Future Enhancements

Potential future improvements (not yet implemented):

1. **Nickname resolution** - Translate @nickname to full public key
2. **Autocomplete support** - Provide substring matching for mentions
3. **Mention highlighting** - CSS class injection for UI rendering
4. **Mention statistics** - Track mention frequency per user
5. **Batch rendering** - Optimize rendering for message lists

---

## See Also

- [Dual Mention Syntax Analysis](../DUAL_MENTION_SYNTAX_ANALYSIS.md) - Design document
- [Message Structure and Field Visibility](../MESSAGE_STRUCTURE_AND_FIELD_VISIBILITY.md) - Protocol details
- Shell Integration (Phase 2 - planned)
- RSocket Chat Server Integration (planned)

---

**Last Updated:** 2025-10-15
**Module:** io.takamaka.messages:messages:1.3.0-SNAPSHOT
