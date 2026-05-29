# Messages Module Documentation

This directory contains all documentation for the Takamaka Messages protocol module.

## Directory Structure

### 📐 [architecture/](architecture/)
Current architectural documentation, API references, and protocol specifications.

**Key Documents:**
- `MESSAGE_ACTIONS_API_1_5_0.md` - Consumer API reference for the 1.5.0 message-action layer (signatures, types, JSON)
- `MENTION_UTILITIES_API.md` - Complete API reference for mention parsing, validation, and rendering
- `AddressRegex.md` - Regex patterns for public key format validation

### 💬 [message-actions/](message-actions/)
Per-action reference for the 1.5.0 message actions (reply, reaction, reaction_remove,
edit, redact, pin, unpin, forward, share_history): exact wire JSON anchored to the
cross-platform fixtures, validator outcomes, code references, and shell CLI forms.
Includes [`verify_docs_against_fixtures.sh`](message-actions/verify_docs_against_fixtures.sh),
a drift guard that checks every JSON block against its fixture.

### 🗺️ [roadmap/](roadmap/)
Planning documents, proposals, and development strategies.

**Key Documents:**
- `MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md` - Normative spec for the message-action protocol
- `IMPL_PLAN_PHASE_1_MESSAGES_1_5_0.md` - Phase 1 implementation plan

### 📦 [archive/](archive/)
Historical documentation, superseded specifications, and completed plans.

**Status:** Currently empty - documents will be archived as they are superseded or completed.

## Quick Links

- **Message-action protocol (normative)?** → [roadmap/MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md](roadmap/MESSAGE_ACTIONS_REPLY_AND_REACTION_SPEC.md)
- **Message-action API reference?** → [architecture/MESSAGE_ACTIONS_API_1_5_0.md](architecture/MESSAGE_ACTIONS_API_1_5_0.md)
- **One specific action's JSON + CLI?** → [message-actions/](message-actions/)
- **Mention API reference?** → [architecture/MENTION_UTILITIES_API.md](architecture/MENTION_UTILITIES_API.md)
- **Key format validation?** → [architecture/AddressRegex.md](architecture/AddressRegex.md)
- **Module overview?** → [../README.md](../README.md)

## Module Overview

The Messages module provides protocol bean definitions, message format specifications, and cryptographic utilities for the Takamaka chat system. It supports end-to-end encryption, dual mention syntax (@public/#private), and multiple cryptographic key formats (Ed25519, qTesla, etc.).

**Version:** 1.5.0-SNAPSHOT
**Java Version:** 17

## Navigation

Use the directory links above to explore specific documentation categories. Each directory contains a README with detailed information about its contents.

---

**Last Updated:** 2025-10-16
