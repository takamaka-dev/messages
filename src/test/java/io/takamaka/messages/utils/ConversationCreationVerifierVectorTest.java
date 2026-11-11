package io.takamaka.messages.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.messages.chat.conversation.CreateConversationRequestBean;
import io.takamaka.messages.chat.conversation.CreateConversationResponseBean;
import io.takamaka.messages.chat.conversation.TopicTitleKeyBean;
import io.takamaka.messages.chat.core.SignedContentTopicBean;
import io.takamaka.messages.utils.ConversationCreationVerifier.Refusal;
import io.takamaka.messages.utils.ConversationCreationVerifier.Verdict;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ConversationCreationVerifier} against the shared cross-platform vector
 * {@code creator/java_conversation_creator_member_vectors.json} (generated from Java production signing by
 * rsclient-flutter {@code tool/vectors/ConversationCreatorMemberVector.java}; byte-identical to the copies
 * the shell, chat-web-gui and the Dart port assert), plus the shell's ({@code JoinVerifiedCreationTest})
 * and the GUI's ({@code ConversationCreatorMemberVectorTest}) extra cases.
 */
class ConversationCreationVerifierVectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode vector() throws Exception {
        try (InputStream in = ConversationCreationVerifierVectorTest.class
                .getResourceAsStream("/creator/java_conversation_creator_member_vectors.json")) {
            assertNotNull(in, "vector missing from test resources");
            return MAPPER.readTree(in);
        }
    }

    private static JsonNode byName(String name) throws Exception {
        for (JsonNode c : vector().get("cases")) {
            if (name.equals(c.get("name").asText())) {
                return c;
            }
        }
        throw new AssertionError("no case " + name);
    }

    private static CreateConversationRequestBean request(JsonNode c) throws Exception {
        return TkmTextUtils.getJacksonMapper().readValue(
                c.get("create_conversation_request").toString(), CreateConversationRequestBean.class);
    }

    /** The answer an honest server gives for the case: labelled with the requested hash. */
    private static CreateConversationResponseBean answer(JsonNode c) throws Exception {
        return new CreateConversationResponseBean(c.get("requested_hash").asText(), request(c));
    }

    /** The caller's part: the reference decrypt, or null when it fails. */
    private static TopicTitleKeyBean decrypt(SignedContentTopicBean topic, String key) {
        try {
            return ChatCryptoUtils.decryptTopicTitleKeyBean(topic.getTopicDescription(), key,
                    topic.getTopicSymmetricKeySignature());
        } catch (Exception ex) {
            return null;
        }
    }

    private static String hash() throws Exception {
        return vector().get("conversation_hash_name").asText();
    }

    @Test
    @DisplayName("vector: every case gets the envelope verdict and the title verdict the other clients give")
    void everyVectorCase() throws Exception {
        List<String> names = new ArrayList<>();
        for (JsonNode c : vector().get("cases")) {
            String name = c.get("name").asText();
            names.add(name);
            String requested = c.get("requested_hash").asText();
            CreateConversationRequestBean request = request(c);

            Verdict onAnswer = ConversationCreationVerifier.verifyAnswer(requested, answer(c));
            Verdict onEnvelope = ConversationCreationVerifier.verifyCreation(requested, request);
            assertEquals(c.get("expected").asText(), onAnswer.wireName(), name);
            assertEquals(onAnswer, onEnvelope, name + ": answer and envelope entry points agree");

            if (onAnswer.verified()) {
                assertNull(onAnswer.refusal(), name);
                assertEquals(c.get("expected_creator").asText(), onAnswer.creator(), name);
                assertEquals(requested, onAnswer.conversationHash(), name);
                assertEquals(new ArrayList<>(request.getTopic().getTopicMembersMap().getTopicInvitationList().keySet()),
                        onAnswer.roster(), name);
                assertTrue(onAnswer.roster().contains(onAnswer.creator()), name);
                assertThrows(UnsupportedOperationException.class, () -> onAnswer.roster().add("x"), name);
            } else {
                assertTrue(c.get("expected_creator").isNull(), name);
                assertNull(onAnswer.creator(), name + ": a refusal names no creator");
                assertNull(onAnswer.conversationHash(), name);
                assertTrue(onAnswer.roster().isEmpty(), name);
            }

            // phase 2 is independent of phase 1 (the vector states it for every case)
            SignedContentTopicBean topic = request.getTopic();
            TopicTitleKeyBean decrypted = decrypt(topic, c.get("symmetric_key").asText());
            assertNotNull(decrypted, name + ": the case's own key decrypts its topic");
            Optional<Refusal> binding = ConversationCreationVerifier.keyBindingProblem(topic, decrypted);
            assertEquals(c.get("expected_title").asText(), binding.map(Refusal::wireName).orElse("ok"), name);
        }
        assertEquals(List.of("genuine", "resigned_by_non_member", "from_is_not_the_signer",
                "another_conversation", "title_not_bound_to_key"), names);
    }

    @Test
    @DisplayName("ruling: a genuine topic re-signed by a non-member names no creator")
    void resignedByNonMember() throws Exception {
        CreateConversationRequestBean genuine = request(byName("genuine"));
        CreateConversationRequestBean resigned = request(byName("resigned_by_non_member"));
        // Same signed topic; only the signer differs, and it is not a roster identity.
        assertEquals(TkmTextUtils.getJacksonMapper().writeValueAsString(genuine.getTopic()),
                TkmTextUtils.getJacksonMapper().writeValueAsString(resigned.getTopic()));
        assertEquals(vector().get("outsider").asText(), resigned.getFrom());

        assertTrue(ConversationCreationVerifier.verifyCreation(hash(), genuine).verified());
        Verdict v = ConversationCreationVerifier.verifyCreation(hash(), resigned);
        assertEquals(Refusal.SIGNER_NOT_MEMBER, v.refusal());
        assertFalse(v.verified());
    }

    @Test
    @DisplayName("answered hash: a mislabelled answer is ANSWER_MISMATCH; a relabelled one is HASH_MISMATCH")
    void answeredHashMustBeTheRequestedHash() throws Exception {
        CreateConversationRequestBean genuine = request(byName("genuine"));
        String real = hash();
        String other = "0000000000000000000000000000000000000000000000000000000000000000";

        // asked for X, the answer says it is Y (even though the topic inside is X's)
        assertEquals(Refusal.ANSWER_MISMATCH, ConversationCreationVerifier
                .verifyAnswer(real, new CreateConversationResponseBean(other, genuine)).refusal());
        assertEquals(Refusal.ANSWER_MISMATCH, ConversationCreationVerifier
                .verifyAnswer(real, new CreateConversationResponseBean(null, genuine)).refusal());
        // asked for Y, the answer is labelled Y but carries X's genuine creation: the label proves nothing
        assertEquals(Refusal.HASH_MISMATCH, ConversationCreationVerifier
                .verifyAnswer(other, new CreateConversationResponseBean(other, genuine)).refusal());
        // the label is checked BEFORE anything inside the envelope is looked at
        CreateConversationRequestBean wrongType = request(byName("genuine"));
        wrongType.setMessageType("TOPIC_MESSAGE");
        assertEquals(Refusal.ANSWER_MISMATCH, ConversationCreationVerifier
                .verifyAnswer(real, new CreateConversationResponseBean(other, wrongType)).refusal());
    }

    @Test
    @DisplayName("incomplete or wrong-type envelopes are refused (MISSING / WRONG_TYPE)")
    void incompleteOrWrongType() throws Exception {
        String hash = hash();
        CreateConversationRequestBean genuine = request(byName("genuine"));

        assertEquals(Refusal.MISSING, ConversationCreationVerifier.verifyAnswer(hash, null).refusal());
        assertEquals(Refusal.MISSING, ConversationCreationVerifier
                .verifyAnswer(hash, new CreateConversationResponseBean(hash, null)).refusal());
        assertEquals(Refusal.MISSING, ConversationCreationVerifier
                .verifyAnswer(null, new CreateConversationResponseBean(hash, genuine)).refusal());
        assertEquals(Refusal.MISSING, ConversationCreationVerifier
                .verifyAnswer("", new CreateConversationResponseBean("", genuine)).refusal());
        assertEquals(Refusal.MISSING, ConversationCreationVerifier.verifyCreation(hash, null).refusal());
        assertEquals(Refusal.MISSING, ConversationCreationVerifier.verifyCreation(null, genuine).refusal());

        CreateConversationRequestBean noFrom = request(byName("genuine"));
        noFrom.setFrom(null);
        assertEquals(Refusal.MISSING, ConversationCreationVerifier.verifyCreation(hash, noFrom).refusal());
        noFrom.setFrom("");
        assertEquals(Refusal.MISSING, ConversationCreationVerifier.verifyCreation(hash, noFrom).refusal());
        CreateConversationRequestBean noTopic = request(byName("genuine"));
        noTopic.setTopic(null);
        assertEquals(Refusal.MISSING, ConversationCreationVerifier.verifyCreation(hash, noTopic).refusal());

        CreateConversationRequestBean wrongType = request(byName("genuine"));
        wrongType.setMessageType("TOPIC_MESSAGE");
        assertEquals(Refusal.WRONG_TYPE, ConversationCreationVerifier.verifyCreation(hash, wrongType).refusal());
        assertEquals(Refusal.WRONG_TYPE, ConversationCreationVerifier
                .verifyAnswer(hash, new CreateConversationResponseBean(hash, wrongType)).refusal());
        wrongType.setMessageType(null);
        assertEquals(Refusal.WRONG_TYPE, ConversationCreationVerifier.verifyCreation(hash, wrongType).refusal());
    }

    /**
     * Identities are Base64URL with {@code .} padding only ({@code BASE64_ENCODING_CONTRACT.md} §2.1) and
     * the roster comparison is exact. The same key bytes respelled in standard base64 name no creator on
     * either platform: Java refuses them at the signature, Dart (alphabet-lenient decoder) at the roster.
     */
    @Test
    @DisplayName("a `from` respelled in standard base64 names no creator")
    void standardAlphabetFrom() throws Exception {
        CreateConversationRequestBean respelled = request(byName("genuine"));
        String original = respelled.getFrom();
        respelled.setFrom(original.replace('-', '+').replace('_', '/').replace('.', '='));
        assertNotEquals(original, respelled.getFrom());
        Verdict v = ConversationCreationVerifier.verifyCreation(hash(), respelled);
        assertFalse(v.verified());
        assertTrue(v.refusal() == Refusal.BAD_SIGNATURE || v.refusal() == Refusal.SIGNER_NOT_MEMBER,
                "refusal " + v.refusal());
        assertNull(v.creator());
    }

    @Test
    @DisplayName("tampering inside the signature is BAD_SIGNATURE (roster, title hash)")
    void tamperedSignedContent() throws Exception {
        CreateConversationRequestBean rosterEdited = request(byName("genuine"));
        var invites = rosterEdited.getTopic().getTopicMembersMap().getTopicInvitationList();
        invites.put(vector().get("outsider").asText(), invites.values().iterator().next());
        assertEquals(Refusal.BAD_SIGNATURE, ConversationCreationVerifier.verifyCreation(hash(), rosterEdited).refusal());

        CreateConversationRequestBean titleHashEdited = request(byName("genuine"));
        titleHashEdited.getTopic().setTopicTitleHash(
                request(byName("another_conversation")).getTopic().getTopicTitleHash());
        assertEquals(Refusal.BAD_SIGNATURE,
                ConversationCreationVerifier.verifyCreation(hash(), titleHashEdited).refusal());
    }

    @Test
    @DisplayName("phase 2: a key built for an existing conversation is refused only by the title binding")
    void keyBuiltForAnExistingConversation() throws Exception {
        JsonNode genuine = byName("genuine");
        JsonNode built = byName("title_not_bound_to_key");
        SignedContentTopicBean g = request(genuine).getTopic();
        SignedContentTopicBean b = request(built).getTopic();
        assertEquals(g.getTopicTitleHash(), b.getTopicTitleHash());
        assertEquals(g.getTopicMembersMap().getTopicInvitationList().keySet(),
                b.getTopicMembersMap().getTopicInvitationList().keySet());
        assertNotEquals(genuine.get("symmetric_key").asText(), built.get("symmetric_key").asText());

        // signature + hash + signer-is-a-member all hold …
        assertTrue(ConversationCreationVerifier.verifyAnswer(hash(), answer(built)).verified());
        // … and so does the key commitment. Only the title binding is left.
        TopicTitleKeyBean decryptedBuilt = decrypt(b, built.get("symmetric_key").asText());
        assertTrue(ConversationCreationVerifier.keyCommitmentHolds(decryptedBuilt, b));
        assertFalse(ConversationCreationVerifier.titleBindingHolds(decryptedBuilt, b));
        assertEquals(Optional.of(Refusal.TITLE_HASH_MISMATCH),
                ConversationCreationVerifier.keyBindingProblem(b, decryptedBuilt));

        TopicTitleKeyBean decryptedGenuine = decrypt(g, genuine.get("symmetric_key").asText());
        assertEquals(Optional.empty(), ConversationCreationVerifier.keyBindingProblem(g, decryptedGenuine));
        assertEquals(g.getTopicTitleHash(), ConversationCreationVerifier.topicTitleHashOf(
                decryptedGenuine.getTopicTitle(), decryptedGenuine.getConversationSalt()));
    }

    @Test
    @DisplayName("phase 2: a key outside the commitment (or a failed decrypt) is KEY_COMMITMENT")
    void keyOutsideTheCommitment() throws Exception {
        JsonNode genuine = byName("genuine");
        JsonNode built = byName("title_not_bound_to_key");
        SignedContentTopicBean g = request(genuine).getTopic();

        // the shell's case: the genuine topic, another conversation key → the caller's decrypt fails → null
        TopicTitleKeyBean failed = decrypt(g, built.get("symmetric_key").asText());
        assertNull(failed);
        assertEquals(Optional.of(Refusal.KEY_COMMITMENT), ConversationCreationVerifier.keyBindingProblem(g, failed));

        // a bean decrypted from ANOTHER topic (its key is not the one this creation commits to): the
        // commitment is checked here too, whichever decrypt the caller used
        TopicTitleKeyBean foreign = decrypt(request(built).getTopic(), built.get("symmetric_key").asText());
        assertNotNull(foreign);
        assertFalse(ConversationCreationVerifier.keyCommitmentHolds(foreign, g));
        assertEquals(Optional.of(Refusal.KEY_COMMITMENT), ConversationCreationVerifier.keyBindingProblem(g, foreign));

        // never throws on nulls
        assertEquals(Optional.of(Refusal.KEY_COMMITMENT), ConversationCreationVerifier.keyBindingProblem(null, null));
        TopicTitleKeyBean ok = decrypt(g, genuine.get("symmetric_key").asText());
        assertEquals(Optional.of(Refusal.KEY_COMMITMENT), ConversationCreationVerifier.keyBindingProblem(null, ok));
        assertFalse(ConversationCreationVerifier.titleBindingHolds(ok, null));
        assertFalse(ConversationCreationVerifier.titleBindingHolds(null, g));
    }

    @Test
    @DisplayName("wire names: the cross-platform names, unique, with an explanation each")
    void wireNames() {
        assertEquals(List.of("missing", "answerMismatch", "wrongType", "badSignature", "hashMismatch",
                "signerNotMember", "keyCommitment", "titleHashMismatch"),
                Arrays.stream(Refusal.values()).map(Refusal::wireName).toList());
        Set<String> seen = new HashSet<>();
        for (Refusal r : Refusal.values()) {
            assertTrue(seen.add(r.wireName()));
            assertFalse(r.explanation().isBlank(), r.name());
            assertNotEquals(ConversationCreationVerifier.VERIFIED_WIRE_NAME, r.wireName());
        }
        assertEquals("verified", ConversationCreationVerifier.VERIFIED_WIRE_NAME);
    }

    @Test
    @DisplayName("stateless: no instance, no fields beyond the wire-name constant")
    void stateless() {
        assertTrue(Modifier.isFinal(ConversationCreationVerifier.class.getModifiers()));
        for (var f : ConversationCreationVerifier.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())
                    && f.getType() == String.class, f.getName());
        }
        for (var c : ConversationCreationVerifier.class.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(c.getModifiers()));
        }
    }
}
