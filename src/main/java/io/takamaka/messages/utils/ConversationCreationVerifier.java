package io.takamaka.messages.utils;

import io.takamaka.messages.chat.conversation.CreateConversationRequestBean;
import io.takamaka.messages.chat.conversation.CreateConversationResponseBean;
import io.takamaka.messages.chat.conversation.TopicKeyDistributionItemBean;
import io.takamaka.messages.chat.conversation.TopicTitleKeyBean;
import io.takamaka.messages.chat.core.SignedContentTopicBean;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The STATELESS verifier of a fetched conversation creation — the one gate every Java client (shell join,
 * chat-web-gui {@code unwrapJoin}) passes an answer of {@code retrieveconversation} /
 * {@code retrieveallconversations} through before it adopts the conversation key or names a creator.
 *
 * <p>Operator rulings 2026-11-11: <b>the identity is the public key</b> — a creation establishes a creator
 * only if its signer is one of the identities of the SIGNED roster; <b>refuse everywhere</b>; and the
 * <b>title-hash gate</b> stands before a key is adopted. «Narrowed R4»: this class is verification only —
 * no honoring logic, no transport, no state, no storage, no decryption of the invite. It returns a typed
 * verdict; what a refusal means for the client (message, exception, unjoinable record) is the client's.</p>
 *
 * <h2>Two phases</h2>
 * <ol>
 *   <li>{@link #verifyAnswer} — everything decidable WITHOUT the conversation key; run it BEFORE the RSA
 *       unwrap so an unverified answer never reaches the identity's private key:
 *     <ol>
 *       <li>the answer is complete ({@link Refusal#MISSING});</li>
 *       <li>its {@code conversation_hash_name} is the requested hash ({@link Refusal#ANSWER_MISMATCH}) —
 *           the one UNSIGNED field of the bean: being right proves nothing, being wrong is refused;</li>
 *       <li>the envelope is a {@code TOPIC_CREATION} ({@link Refusal#WRONG_TYPE});</li>
 *       <li>the signature verifies under {@code from} — production
 *           {@link ChatCryptoUtils#verifySignedMessage(String, String...)}, canonical (JCS) form re-derived
 *           from the parsed topic ({@link Refusal#BAD_SIGNATURE});</li>
 *       <li>the requested hash equals {@link ChatUtils#getConversationName} over the signed roster and
 *           the signed {@code topic_title_hash} — the conversation identity is DERIVED, never taken from
 *           the server ({@link Refusal#HASH_MISMATCH});</li>
 *       <li>{@code from} is a roster identity, exact string match (identities are Base64URL with
 *           {@code .} padding, {@code BASE64_ENCODING_CONTRACT.md} §2.1 — no alphabet normalisation)
 *           ({@link Refusal#SIGNER_NOT_MEMBER}).</li>
 *     </ol></li>
 *   <li>{@link #keyBindingProblem} — after the CALLER has unwrapped its invite and decrypted the topic:
 *       the key commitment ({@link Refusal#KEY_COMMITMENT}) and the title-hash gate
 *       {@code SHA3-256(title + salt) == topic_title_hash} ({@link Refusal#TITLE_HASH_MISMATCH}). The
 *       commitment alone is true of a key the signer INVENTED for an existing conversation whose roster and
 *       {@code topic_title_hash} it copied; a title+salt hashing to the copied {@code topic_title_hash}
 *       is what it cannot produce.</li>
 * </ol>
 *
 * <p>Every method is a pure function of its arguments and never throws. The verdicts are pinned to the
 * Dart port ({@code ConversationCreator}) by {@code java_conversation_creator_member_vectors.json}
 * ({@code ConversationCreationVerifierVectorTest}).</p>
 *
 * @since Messages 1.12.0
 */
public final class ConversationCreationVerifier {

    /** {@link Verdict#wireName()} of a creation that verifies (the vector's {@code expected} value). */
    public static final String VERIFIED_WIRE_NAME = "verified";

    private ConversationCreationVerifier() {
    }

    /**
     * Why a creation was refused. {@link #wireName()} is the cross-platform name: the Dart
     * {@code CreatorProblem} name where one exists, and the name the shell prints / the GUI records.
     */
    public enum Refusal {
        /** No creation envelope, topic, signer, roster or requested hash. */
        MISSING("missing", "the server's answer is incomplete (no creation envelope, signer or roster)"),
        /** The answer's (unsigned) {@code conversation_hash_name} is not the requested hash. */
        ANSWER_MISMATCH("answerMismatch",
                "the server answered with a different conversation than the one requested"),
        /** {@code message_type} is not {@code TOPIC_CREATION}. */
        WRONG_TYPE("wrongType", "the envelope is not a conversation creation (TOPIC_CREATION)"),
        /** The signature does not verify under {@code from}. */
        BAD_SIGNATURE("badSignature", "the creation signature does not verify under its 'from' key"),
        /** The signed roster + title hash do not derive the requested hash. */
        HASH_MISMATCH("hashMismatch",
                "the signed roster and title hash do not derive the requested conversation hash"),
        /** {@code from} is not one of the signed roster's identities. */
        SIGNER_NOT_MEMBER("signerNotMember",
                "the creation is signed by a key that is not a member of the conversation"),
        /** The decrypted key does not hash to the signed {@code topic_symmetric_key_signature}. */
        KEY_COMMITMENT("keyCommitment",
                "the conversation key is not the one the creation commits to (or the topic cannot be "
                + "decrypted with it)"),
        /** The decrypted title + salt do not hash to the signed {@code topic_title_hash}. */
        TITLE_HASH_MISMATCH("titleHashMismatch",
                "the decrypted title+salt do not hash to the signed topic_title_hash: the key does not "
                + "belong to this conversation's title");

        private final String wireName;
        private final String explanation;

        Refusal(String wireName, String explanation) {
            this.wireName = wireName;
            this.explanation = explanation;
        }

        /** The cross-platform verdict name (the vector's {@code expected} / {@code expected_title}). */
        public String wireName() {
            return wireName;
        }

        /** One clause, in plain words, that completes "refused: …". No secret, no key material. */
        public String explanation() {
            return explanation;
        }
    }

    /**
     * The outcome of {@link #verifyAnswer} / {@link #verifyCreation}.
     *
     * @param refusal null when the creation verifies, otherwise why it was refused
     * @param conversationHash the verified conversation identity (= the requested hash = the hash derived
     * from the signed roster and title hash); null when refused
     * @param creator the verified creator (the envelope's {@code from}, a roster identity); null when
     * refused
     * @param roster the signed roster (the invitation list's keys, in the envelope's order, immutable);
     * empty when refused
     * @param detail diagnostic text for a log line when refused (the underlying exception's message, or
     * null); never shown as the reason — that is {@link Refusal#explanation()}
     */
    public record Verdict(Refusal refusal, String conversationHash, String creator, List<String> roster,
            String detail) {

        /** Whether the creation verifies. */
        public boolean verified() {
            return refusal == null;
        }

        /** {@value ConversationCreationVerifier#VERIFIED_WIRE_NAME} or the refusal's wire name. */
        public String wireName() {
            return refusal == null ? VERIFIED_WIRE_NAME : refusal.wireName();
        }

        private static Verdict refused(Refusal refusal, String detail) {
            return new Verdict(refusal, null, null, List.of(), detail);
        }
    }

    /**
     * Phase 1 on a server ANSWER: checks 1–6 of the class comment, in that order. Never throws.
     *
     * @param requestedHash the conversation hash the client asked the server for
     * @param answer the server's answer
     * @return the verdict; when {@link Verdict#verified()}, {@code conversationHash}, {@code creator} and
     * {@code roster} are the only values the client may adopt
     */
    public static Verdict verifyAnswer(String requestedHash, CreateConversationResponseBean answer) {
        if (answer == null) {
            return Verdict.refused(Refusal.MISSING, "no answer");
        }
        if (requestedHash == null || requestedHash.isEmpty() || answer.getCreateConversationRequestBean() == null) {
            return Verdict.refused(Refusal.MISSING, "no requested hash or no creation envelope");
        }
        if (!requestedHash.equals(answer.getConversationHashName())) {
            return Verdict.refused(Refusal.ANSWER_MISMATCH, "answered for " + answer.getConversationHashName());
        }
        return verifyCreation(requestedHash, answer.getCreateConversationRequestBean());
    }

    /**
     * Phase 1 on a creation ENVELOPE alone (no answer label to compare): checks 1 and 3–6. For a caller
     * that holds the signed envelope and the hash it is filed under, not a server answer. Never throws.
     *
     * @param conversationHash the hash the creation must derive
     * @param creation the signed {@code TOPIC_CREATION} envelope
     * @return the verdict
     */
    public static Verdict verifyCreation(String conversationHash, CreateConversationRequestBean creation) {
        if (conversationHash == null || conversationHash.isEmpty() || creation == null
                || creation.getTopic() == null || creation.getFrom() == null || creation.getFrom().isEmpty()) {
            return Verdict.refused(Refusal.MISSING, "no hash, envelope, topic or signer");
        }
        // verifySignedMessage dispatches on message_type; only a TOPIC_CREATION is a creation.
        if (!CHAT_MESSAGE_TYPES.TOPIC_CREATION.name().equals(creation.getMessageType())) {
            return Verdict.refused(Refusal.WRONG_TYPE, "message_type " + creation.getMessageType());
        }
        try {
            // Re-derives the canonical (JCS) form from the parsed topic and verifies under `from`.
            ChatCryptoUtils.verifySignedMessage(TkmTextUtils.getJacksonMapper().writeValueAsString(creation));
        } catch (Exception ex) {
            return Verdict.refused(Refusal.BAD_SIGNATURE, ex.getMessage());
        }
        final SignedContentTopicBean topic = creation.getTopic();
        final List<String> roster;
        final String derived;
        try {
            final Map<String, TopicKeyDistributionItemBean> invites = topic.getTopicMembersMap().getTopicInvitationList();
            roster = List.copyOf(invites.keySet());
            // getConversationName sorts the array it is given: the hash commits to the SORTED roster.
            derived = ChatUtils.getConversationName(roster.toArray(String[]::new), topic.getTopicTitleHash());
        } catch (Exception ex) {
            return Verdict.refused(Refusal.MISSING, "roster unreadable: " + ex);
        }
        if (!conversationHash.equals(derived)) {
            return Verdict.refused(Refusal.HASH_MISMATCH, "the signed topic derives " + derived);
        }
        // Exact match on the identity string, as rschat's own self-invitation check.
        if (!roster.contains(creation.getFrom())) {
            return Verdict.refused(Refusal.SIGNER_NOT_MEMBER, "signer " + creation.getFrom());
        }
        return new Verdict(null, derived, creation.getFrom(), roster, null);
    }

    /**
     * Phase 2, pure: whether the topic the CALLER decrypted is bound to the signed topic. Run it on the
     * bean the key will be adopted from, after {@link #verifyAnswer} verified the envelope. Never throws.
     *
     * <ul>
     *   <li>{@link Refusal#KEY_COMMITMENT} — {@code decrypted} is null (the caller's decrypt failed), or
     *       its key does not hash to the signed {@code topic_symmetric_key_signature}
     *       ({@code Hash256B64URL(symmetric_key)}, the check
     *       {@link ChatCryptoUtils#decryptTopicTitleKeyBean} makes — repeated here so the verdict does not
     *       depend on which decrypt the caller used);</li>
     *   <li>{@link Refusal#TITLE_HASH_MISMATCH} — the decrypted {@code title + salt} do not hash to the
     *       signed {@code topic_title_hash}.</li>
     * </ul>
     *
     * @param topic the SIGNED topic of the verified creation
     * @param decrypted the title/key bean the caller decrypted from {@code topic.topic_description}; null
     * when the decrypt failed
     * @return empty when the key may be adopted, otherwise the refusal
     */
    public static Optional<Refusal> keyBindingProblem(SignedContentTopicBean topic, TopicTitleKeyBean decrypted) {
        if (!keyCommitmentHolds(decrypted, topic)) {
            return Optional.of(Refusal.KEY_COMMITMENT);
        }
        if (!titleBindingHolds(decrypted, topic)) {
            return Optional.of(Refusal.TITLE_HASH_MISMATCH);
        }
        return Optional.empty();
    }

    /**
     * Whether the DECRYPTED key hashes to the SIGNED {@code topic_symmetric_key_signature}. Never throws;
     * an uncomputable hash is "does not hold".
     *
     * @param decrypted the decrypted title/key bean
     * @param topic the signed topic
     * @return true when the commitment holds
     */
    public static boolean keyCommitmentHolds(TopicTitleKeyBean decrypted, SignedContentTopicBean topic) {
        try {
            return decrypted != null && topic != null && decrypted.getSymmetricKey() != null
                    && topic.getTopicSymmetricKeySignature() != null
                    && topic.getTopicSymmetricKeySignature().equals(
                            TkmSignUtils.Hash256B64URL(decrypted.getSymmetricKey()));
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Whether the DECRYPTED title+salt reproduce the SIGNED {@code topic_title_hash}. Never throws; an
     * uncomputable hash is "does not hold". Exact string comparison.
     *
     * @param decrypted the decrypted title/key bean
     * @param topic the signed topic
     * @return true when the title binding holds
     */
    public static boolean titleBindingHolds(TopicTitleKeyBean decrypted, SignedContentTopicBean topic) {
        try {
            return decrypted != null && topic != null && topic.getTopicTitleHash() != null
                    && decrypted.getTopicTitle() != null && decrypted.getConversationSalt() != null
                    && topic.getTopicTitleHash().equals(
                            topicTitleHashOf(decrypted.getTopicTitle(), decrypted.getConversationSalt()));
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * {@code topic_title_hash} exactly as {@link ChatCryptoUtils#getSignedContentTopicBean} makes it on
     * create: SHA3-256 over the UTF-8 bytes of the plain concatenation {@code title + salt}, Base64URL with
     * {@code .} padding. Dart: {@code ConversationCreator.topicTitleHashOf}.
     *
     * @param topicTitle the decrypted title
     * @param conversationSalt the decrypted conversation salt
     * @return the title hash
     * @throws Exception if the hash cannot be computed
     */
    public static String topicTitleHashOf(String topicTitle, String conversationSalt) throws Exception {
        return TkmSignUtils.Hash256B64URL(topicTitle + conversationSalt);
    }
}
