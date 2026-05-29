/*
 * Copyright 2026 AiliA SA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.takamaka.messages.chat.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.messages.chat.attachment.ChatMediaPlaceholderBean;
import io.takamaka.messages.chat.attachment.InlineContentLimits;
import io.takamaka.messages.chat.message.MessageActionMeta.ActionSpec;
import io.takamaka.messages.exception.HardProtocolViolationException;
import io.takamaka.messages.exception.IncompatibleMajorVersionException;
import io.takamaka.messages.exception.InnerSignatureFailureException;
import io.takamaka.messages.exception.LegacyVersionWithFeaturesException;
import io.takamaka.messages.exception.MalformedProtocolVersionException;
import io.takamaka.messages.exception.MissingVersionWithFeaturesException;
import io.takamaka.messages.exception.UnknownActionException;
import io.takamaka.messages.utils.SimpleRequestHelper;
import io.takamaka.wallet.TkmCypherProviderBCED25519;
import io.takamaka.wallet.beans.TkmCypherBean;
import io.takamaka.wallet.utils.TkmSignUtils;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Validator for the message-action layer (Phase 1 plan §3.3, spec §4.2).
 *
 * <p>Two failure categories:</p>
 * <ul>
 *   <li><strong>Hard violations (H1-H6)</strong> — the validator throws a
 *       typed {@link HardProtocolViolationException} subclass; the message
 *       MUST be dropped (not rendered).</li>
 *   <li><strong>Soft violations</strong> — the validator returns a
 *       {@link ValidationResult} carrying {@link Decoration} entries; the
 *       body still renders.</li>
 * </ul>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
@Slf4j
public final class MessageActionValidator {

    /** {@code ^[A-Za-z0-9_-]{86}\.\.$} — Ed25519 signature target form. */
    static final Pattern SIGNATURE_REGEX = Pattern.compile("^[A-Za-z0-9_-]{86}\\.\\.$");
    /** {@code ^[A-Za-z0-9_-]{43}\.$} — compact public-key target form. */
    static final Pattern PUBLIC_KEY_REGEX = Pattern.compile("^[A-Za-z0-9_-]{43}\\.$");

    /** Hard cap on the {@code fw_content} forward chain depth. */
    public static final int MAX_FORWARD_DEPTH = 10;

    /** Minor version at which v1.1 feature fields were introduced. */
    private static final int FEATURES_MIN_MINOR = 1;

    /**
     * Top-level validation. Throws hard-violation exceptions; returns soft
     * results.
     *
     * @param bean the decrypted inner content bean
     * @param outerEnvelope the signed outer envelope (sender PK, conversation)
     * @param ctx client-supplied lookup context
     * @return a {@link ValidationResult} for non-fatal outcomes
     * @throws HardProtocolViolationException for any of H1-H6
     */
    public static ValidationResult validate(
            BasicMessageEncryptedContentBean bean,
            BasicMessageRequestBean outerEnvelope,
            ValidationContext ctx) throws HardProtocolViolationException {

        final String senderPk = outerEnvelope == null ? null : outerEnvelope.getFrom();
        final String outerConversationHash = outerConversationHash(outerEnvelope);

        // Step 0: protocol version gate (may throw H1-H4)
        validateProtocolVersion(bean, outerConversationHash, senderPk);

        // Step 1: action recognition (may throw H5)
        String action = validateActionRecognition(bean, outerConversationHash, senderPk);
        if (action == null) {
            return ValidationResult.empty();
        }

        final ActionSpec spec = MessageActionMeta.lookup(action).orElseThrow();
        final List<Decoration> out = new ArrayList<>();

        // Steps 2-3: target shape
        boolean cardinalityOk = validateCardinality(action, spec, bean, out);
        boolean formatOk = true;
        if (cardinalityOk) {
            formatOk = validateTargetFormat(action, spec, bean, out);
        }
        // Steps 4-5: only meaningful on a well-formed target
        if (cardinalityOk && formatOk) {
            validateTargetResolvable(action, spec, bean, ctx, out);
            validateAuthorization(action, spec, bean, outerEnvelope, ctx, out);
        }

        // Action-specific (forward depth, share_history, inline content). May throw H6.
        validateActionSpecific(action, bean, outerEnvelope, outerConversationHash, senderPk, ctx, out);

        boolean overallValid = out.stream()
                .noneMatch(d -> DecorationSeverity.ERROR.equals(d.severity()));
        return new ValidationResult(overallValid, List.copyOf(out));
    }

    // ---- Step 0 ---------------------------------------------------------

    private static void validateProtocolVersion(BasicMessageEncryptedContentBean bean,
            String conversationHash, String senderPk) throws HardProtocolViolationException {
        String version = bean.getClientProtocolVersion();
        boolean features = hasV11Features(bean);

        if (MessageProtocolVersion.isAbsent(version)) {
            if (features) {
                throw new MissingVersionWithFeaturesException(null, conversationHash, senderPk);
            }
            return; // legacy v1.0, no features — grandfathered
        }

        Optional<MessageProtocolVersion.Parsed> parsed = MessageProtocolVersion.parse(version);
        if (parsed.isEmpty()) {
            throw new MalformedProtocolVersionException(version, conversationHash, senderPk);
        }
        if (!parsed.get().isCompatibleMajor()) {
            throw new IncompatibleMajorVersionException(version, conversationHash, senderPk);
        }
        // Same MAJOR. Feature fields require MINOR >= FEATURES_MIN_MINOR.
        if (parsed.get().minor() < FEATURES_MIN_MINOR && features) {
            throw new LegacyVersionWithFeaturesException(version, conversationHash, senderPk);
        }
    }

    private static boolean hasV11Features(BasicMessageEncryptedContentBean bean) {
        boolean targetsPopulated = bean.getTargets() != null && !bean.getTargets().isEmpty();
        return MessageAction.normalize(bean.getAction()) != null
                || targetsPopulated
                || bean.getFwContent() != null
                || bean.getOriginalMessage() != null
                || bean.getReShared() != null;
    }

    // ---- Step 1 ---------------------------------------------------------

    private static String validateActionRecognition(BasicMessageEncryptedContentBean bean,
            String conversationHash, String senderPk) throws UnknownActionException {
        String normalized = MessageAction.normalize(bean.getAction());
        if (normalized == null) {
            return null; // plain message
        }
        if (!MessageAction.KNOWN.contains(normalized)) {
            throw new UnknownActionException(bean.getAction(), conversationHash, senderPk);
        }
        return normalized;
    }

    // ---- Step 2: cardinality -------------------------------------------

    private static boolean validateCardinality(String action, ActionSpec spec,
            BasicMessageEncryptedContentBean bean, List<Decoration> out) {
        int size = bean.getTargets() == null ? 0 : bean.getTargets().size();
        boolean ok = switch (spec.cardinality()) {
            case ZERO -> size == 0;
            case ONE -> size == 1;
            case ZERO_OR_ONE -> size <= 1;
        };
        if (!ok) {
            out.add(new Decoration(
                    malformedTargetsCode(action),
                    "action '" + action + "' has an unexpected number of targets (" + size + ")",
                    DecorationSeverity.WARN,
                    "targets"));
        }
        return ok;
    }

    // ---- Step 3: target format -----------------------------------------

    private static boolean validateTargetFormat(String action, ActionSpec spec,
            BasicMessageEncryptedContentBean bean, List<Decoration> out) {
        if (spec.targetFormat() == MessageActionMeta.TargetFormat.NONE) {
            return true;
        }
        List<String> targets = bean.getTargets();
        if (targets == null || targets.isEmpty()) {
            return true; // ZERO_OR_ONE with no target
        }
        String t0 = targets.get(0);
        boolean matches;
        String code;
        if (spec.targetFormat() == MessageActionMeta.TargetFormat.SIGNATURE) {
            matches = t0 != null && SIGNATURE_REGEX.matcher(t0).matches();
            code = badSignatureCode(action);
        } else {
            matches = t0 != null && PUBLIC_KEY_REGEX.matcher(t0).matches();
            code = badPublicKeyCode(action);
        }
        if (!matches) {
            out.add(new Decoration(
                    code,
                    "action '" + action + "' target[0] does not match the required format",
                    DecorationSeverity.WARN,
                    "targets[0]"));
            return false;
        }
        return true;
    }

    // ---- Step 4: target resolvable (broken reference) -------------------

    private static void validateTargetResolvable(String action, ActionSpec spec,
            BasicMessageEncryptedContentBean bean, ValidationContext ctx, List<Decoration> out) {
        String code = brokenReferenceCode(action);
        if (code == null) {
            return; // only reply / reaction / pin carry a broken-reference signal
        }
        if (spec.targetFormat() != MessageActionMeta.TargetFormat.SIGNATURE) {
            return;
        }
        String target = bean.getTargets().get(0);
        String author = resolveAuthor(ctx, target);
        if (author == null) {
            out.add(new Decoration(
                    code,
                    "referenced message is not in the local cache",
                    DecorationSeverity.INFO,
                    "targets[0]"));
        }
    }

    // ---- Step 5: authorization -----------------------------------------

    private static void validateAuthorization(String action, ActionSpec spec,
            BasicMessageEncryptedContentBean bean, BasicMessageRequestBean outerEnvelope,
            ValidationContext ctx, List<Decoration> out) {
        String sender = outerEnvelope == null ? null : outerEnvelope.getFrom();
        switch (spec.authPattern()) {
            case NO_CHECK -> {
            }
            case SELF_AUTHOR -> {
                String target = bean.getTargets().get(0);
                String author = resolveAuthor(ctx, target);
                if (author != null && sender != null && !author.equals(sender)) {
                    out.add(new Decoration(
                            unauthorizedCode(action),
                            "action '" + action + "' must be performed by the original author",
                            DecorationSeverity.WARN,
                            "from"));
                }
            }
            case CONVERSATION_CREATOR -> {
                String creator = ctx == null ? null : ctx.conversationCreatorPk();
                if (creator != null && sender != null && !creator.equals(sender)) {
                    out.add(new Decoration(
                            unauthorizedCode(action),
                            "action '" + action + "' is restricted to the conversation creator",
                            DecorationSeverity.WARN,
                            "from"));
                }
            }
        }
    }

    // ---- Action-specific ------------------------------------------------

    private static void validateActionSpecific(String action,
            BasicMessageEncryptedContentBean bean, BasicMessageRequestBean outerEnvelope,
            String outerConversationHash, String senderPk,
            ValidationContext ctx, List<Decoration> out) throws InnerSignatureFailureException {
        switch (action) {
            case MessageAction.FORWARD ->
                validateForwardDepth(bean, out);
            case MessageAction.SHARE_HISTORY ->
                validateShareHistory(bean, outerConversationHash, senderPk, ctx, out);
            case MessageAction.REACTION ->
                validateInlineContent(action, bean, out);
            default -> {
                // reply / edit may also carry inline media
                validateInlineContent(action, bean, out);
            }
        }
    }

    private static void validateForwardDepth(BasicMessageEncryptedContentBean bean, List<Decoration> out) {
        if (truncateForwardDepth(bean, MAX_FORWARD_DEPTH)) {
            out.add(new Decoration(
                    ValidationDecorationCodes.FORWARD_DEPTH_EXCEEDED,
                    "forward chain exceeds the maximum depth of " + MAX_FORWARD_DEPTH + "; deepest branch truncated",
                    DecorationSeverity.INFO,
                    "fw_content"));
        }
    }

    /**
     * Truncates the {@code fw_content} chain at {@code max} links and reports
     * whether truncation occurred.
     *
     * @return {@code true} iff the chain was deeper than {@code max}
     */
    static boolean truncateForwardDepth(BasicMessageEncryptedContentBean bean, int max) {
        BasicMessageEncryptedContentBean node = bean.getFwContent();
        int depth = 1;
        while (node != null) {
            if (depth >= max) {
                if (node.getFwContent() != null) {
                    node.setFwContent(null);
                    return true;
                }
                return false;
            }
            node = node.getFwContent();
            depth++;
        }
        return false;
    }

    private static void validateShareHistory(BasicMessageEncryptedContentBean bean,
            String outerConversationHash, String senderPk,
            ValidationContext ctx, List<Decoration> out) throws InnerSignatureFailureException {
        BasicMessageRequestBean original = bean.getOriginalMessage();
        if (original == null) {
            out.add(new Decoration(
                    ValidationDecorationCodes.INVALID_SHARE_HISTORY_MISSING_ORIGINAL_MESSAGE,
                    "share_history is missing its original_message payload",
                    DecorationSeverity.WARN,
                    "original_message"));
            return;
        }

        // Inner-signature verification (H6 on failure).
        verifyInnerSignature(original, outerConversationHash, senderPk);

        // Cross-conversation (ERROR — overallValid false).
        String innerConversation = original.getBasicMessageSignedContentBean() == null
                ? null : original.getBasicMessageSignedContentBean().getConversationHashName();
        if (outerConversationHash != null && innerConversation != null
                && !outerConversationHash.equals(innerConversation)) {
            out.add(new Decoration(
                    ValidationDecorationCodes.INVALID_SHARE_HISTORY_CROSS_CONVERSATION,
                    "embedded original_message belongs to a different conversation",
                    DecorationSeverity.ERROR,
                    "original_message.conversation_hash_name"));
        }

        // Nested share_history (WARN) — requires decrypting the embedded inner.
        if (ctx != null && ctx.embeddedDecryptor() != null) {
            BasicMessageEncryptedContentBean inner = ctx.embeddedDecryptor().apply(original);
            if (inner != null && MessageAction.SHARE_HISTORY.equals(MessageAction.normalize(inner.getAction()))) {
                out.add(new Decoration(
                        ValidationDecorationCodes.INVALID_SHARE_HISTORY_NESTED,
                        "embedded original_message is itself a share_history (nesting is not allowed)",
                        DecorationSeverity.WARN,
                        "original_message"));
            }
        }
    }

    private static void verifyInnerSignature(BasicMessageRequestBean original,
            String outerConversationHash, String senderPk) throws InnerSignatureFailureException {
        try {
            String canonical = SimpleRequestHelper.getCanonicalJson(original.getBasicMessageSignedContentBean());
            TkmCypherBean verify = TkmCypherProviderBCED25519.verify(
                    original.getFrom(), original.getSignature(), canonical);
            if (!verify.isValid()) {
                throw new InnerSignatureFailureException(original.getSignature(), outerConversationHash, senderPk);
            }
        } catch (JsonProcessingException ex) {
            // Cannot canonicalize to verify -> cannot trust -> fail safe (drop).
            log.warn("share_history inner content not canonicalizable; treating as inner-signature failure", ex);
            throw new InnerSignatureFailureException(original.getSignature(), outerConversationHash, senderPk);
        }
    }

    private static void validateInlineContent(String action,
            BasicMessageEncryptedContentBean bean, List<Decoration> out) {
        List<ChatMediaPlaceholderBean> media = bean.getAttachedMedia();
        if (media == null) {
            return;
        }
        boolean isReaction = MessageAction.REACTION.equals(action);
        for (int i = 0; i < media.size(); i++) {
            ChatMediaPlaceholderBean m = media.get(i);
            if (m == null || !Boolean.TRUE.equals(m.getIsTheObject())) {
                continue; // not inline content
            }
            String fieldPrefix = "attached_media[" + i + "]";

            if (isReaction && !InlineContentLimits.isReactionImageMimeAllowed(m.getMediaType())) {
                out.add(new Decoration(
                        ValidationDecorationCodes.INLINE_MIME_VIOLATION,
                        "reaction inline media type '" + m.getMediaType() + "' is not allowed",
                        DecorationSeverity.WARN,
                        fieldPrefix + ".media_type"));
                continue; // disallowed payload — no point hashing it
            }

            String preview = m.getPreview();
            if (preview == null) {
                out.add(new Decoration(
                        ValidationDecorationCodes.INLINE_FIELD_VIOLATION,
                        "inline content is missing its preview payload",
                        DecorationSeverity.WARN,
                        fieldPrefix + ".preview"));
                continue;
            }

            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(preview);
            } catch (IllegalArgumentException ex) {
                out.add(new Decoration(
                        ValidationDecorationCodes.INLINE_DECODE_ERROR,
                        "inline content preview is not valid standard Base64",
                        DecorationSeverity.WARN,
                        fieldPrefix + ".preview"));
                continue;
            }

            if (decoded.length > InlineContentLimits.MAX_INLINE_BYTES) {
                out.add(new Decoration(
                        ValidationDecorationCodes.INLINE_SIZE_VIOLATION,
                        "inline content exceeds " + InlineContentLimits.MAX_INLINE_BYTES + " bytes",
                        DecorationSeverity.WARN,
                        fieldPrefix + ".preview"));
            }

            String expectedHash = m.getUnencryptedContentHash();
            if (expectedHash != null) {
                String actualHash = inlineContentHash(preview);
                if (actualHash == null || !expectedHash.equals(actualHash)) {
                    out.add(new Decoration(
                            ValidationDecorationCodes.INLINE_HASH_MISMATCH,
                            "inline content hash does not match unencrypted_content_hash",
                            DecorationSeverity.ERROR,
                            fieldPrefix + ".unencrypted_content_hash"));
                }
            }
        }
    }

    /**
     * Inline-content hash contract (Phase 1): SHA3-256 of the Base64 preview
     * string, Base64URL-encoded. Identical on the fixture generator and the
     * validator so the snapshot corpus agrees across platforms.
     *
     * @return the hash, or {@code null} if hashing failed
     */
    static String inlineContentHash(String preview) {
        try {
            return TkmSignUtils.Hash256B64URL(preview);
        } catch (Exception ex) {
            log.warn("inline content hashing failed", ex);
            return null;
        }
    }

    // ---- helpers --------------------------------------------------------

    private static String resolveAuthor(ValidationContext ctx, String targetSignature) {
        if (ctx == null || ctx.targetAuthorResolver() == null) {
            return null;
        }
        return ctx.targetAuthorResolver().apply(targetSignature);
    }

    private static String outerConversationHash(BasicMessageRequestBean outerEnvelope) {
        if (outerEnvelope == null || outerEnvelope.getBasicMessageSignedContentBean() == null) {
            return null;
        }
        return outerEnvelope.getBasicMessageSignedContentBean().getConversationHashName();
    }

    private static String malformedTargetsCode(String action) {
        return "INVALID_" + action.toUpperCase(Locale.ROOT) + "_MALFORMED_TARGETS";
    }

    private static String badSignatureCode(String action) {
        return "INVALID_" + action.toUpperCase(Locale.ROOT) + "_BAD_SIGNATURE_FORMAT";
    }

    private static String badPublicKeyCode(String action) {
        return "INVALID_" + action.toUpperCase(Locale.ROOT) + "_BAD_PUBLIC_KEY_FORMAT";
    }

    private static String unauthorizedCode(String action) {
        return "UNAUTHORIZED_" + action.toUpperCase(Locale.ROOT);
    }

    private static String brokenReferenceCode(String action) {
        return switch (action) {
            case MessageAction.REPLY ->
                ValidationDecorationCodes.BROKEN_REFERENCE_REPLY;
            case MessageAction.REACTION ->
                ValidationDecorationCodes.BROKEN_REFERENCE_REACTION;
            case MessageAction.PIN ->
                ValidationDecorationCodes.BROKEN_REFERENCE_PIN;
            default ->
                null;
        };
    }

    private MessageActionValidator() {
    }
}
