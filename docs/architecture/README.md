# Architecture Documentation

This directory contains architectural documentation and API references for the Messages module.

## Contents

### API References

- **[MENTION_UTILITIES_API.md](MENTION_UTILITIES_API.md)** - Complete API reference for mention system
  - **MentionParser** - Extract and validate @public/#private mentions
  - **MentionRenderer** - Render mentions with abbreviated keys
  - **AddressValidation** - Validate and categorize public key formats
  - **InvalidMentionException** - Exception handling for mention errors

### Protocol Specifications

- **[AddressRegex.md](AddressRegex.md)** - Regular expressions for public key format validation
  - Ed25519 format (44 chars)
  - Short B64 format (64 chars)
  - Short Hex format (96 chars)
  - qTesla format (19,840 chars)

## Document Purpose

### For Developers

Start with **MENTION_UTILITIES_API.md** to understand the mention system API. Refer to **AddressRegex.md** for key format validation patterns.

### For Integrators

Review **MENTION_UTILITIES_API.md** for integration examples showing how to parse messages, validate mentions, and render display text.

### For Architects

Both documents provide specifications for the dual mention syntax architecture that preserves privacy in zero-knowledge server environments.

## Related Documentation

- **[../roadmap/](../roadmap/)** - Planned features and improvements
- **[../archive/](../archive/)** - Historical documentation

## Recent Updates

### October 15, 2025
- ✅ **Mention Utilities API Published** - Complete API reference for version 1.3.0
- ✅ **Dual Mention Syntax** - @public and #private mention support
- ✅ **Four Key Format Support** - Ed25519, Short B64, Short Hex, qTesla validation

---

**Last Updated:** 2025-10-16
