package io.takamaka.messages.chat.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.takamaka.messages.chat.attachment.ChatMediaPlaceholderBean;

/**
 * F15 — <b>Java parses a DART-produced message envelope and demands that the attachment is in it.</b>
 *
 * <p><b>The defect.</b> tkmChat uploaded an attachment blob to the server and then sent a message saying
 * there was none. Its send path built the content bean from the text alone and never passed
 * {@code attachedMedia}, so the envelope carried {@code attached_media: null}. Every attachment ever sent
 * from that client was invisible to shell, chat-web-gui and every other tkmChat, <b>with no error at any
 * layer</b>, and the uploaded blob became storage referenced by nothing.
 *
 * <p><b>Why no existing test could have caught it, and why this one can.</b> Three separate reasons, all
 * structural:
 *
 * <ul>
 *   <li><b>A Dart round-trip is blind to it.</b> The send path built one bean and the Dart assertions read
 *       that same bean back, so "the attachment is missing" and "the attachment was never added" are
 *       indistinguishable.</li>
 *   <li><b>Java never received anything Dart produced.</b> Every cross-platform vector in this estate was
 *       Java-generated and Dart-verified — a one-way street. The consumer that would have noticed an empty
 *       {@code attached_media} was never handed a Dart artefact to notice it in. This is the same
 *       structural gap that let F11 ship, on a different field.</li>
 *   <li><b>The parse degrades silently by design.</b> {@link BasicMessageEncryptedContentBean} carries
 *       {@code @JsonIgnoreProperties(ignoreUnknown = true)}, so a divergent field name is dropped without
 *       a warning and becomes indistinguishable from "the sender attached nothing" — see
 *       {@link #aDroppedAttachmentIsIndistinguishableFromNoAttachment_whichIsWhyThisTestExists()}, which
 *       pins that property deliberately rather than leaving it as folklore.</li>
 * </ul>
 *
 * <p>The vector is produced by tkmChat's
 * {@code test/cross_platform/generate_dart_attachment_envelope_vector_test.dart}, <b>through the real
 * production seam</b> ({@code MessageService.buildMessageContent}) rather than by constructing a bean
 * directly — the bean was always capable of carrying the placeholder; it was the send path that never
 * passed one, so a vector that bypassed it would prove nothing.
 *
 * <p><b>On the plaintext form.</b> The vector is the decrypted content bean, not an encrypted envelope.
 * F15's proof was a decrypt taken below Jackson that read
 * {@code {"text_message":"…","attached_media":null}}; the defect is entirely in what the producer
 * serialises, and every layer above it was working. Encrypting would add a random IV and test nothing F15
 * is about.
 */
public class DartProducedAttachmentEnvelopeTest {

    private static final String VECTOR = "/dart_attachment_envelope_vector.json";

    private static JsonNode vector;
    private static ObjectMapper mapper;

    @BeforeAll
    public static void loadVector() throws Exception {
        mapper = new ObjectMapper();
        try (InputStream in = DartProducedAttachmentEnvelopeTest.class.getResourceAsStream(VECTOR)) {
            Assumptions.assumeTrue(in != null,
                    "dart_attachment_envelope_vector.json absent — regenerate with tkmChat's "
                            + "generate_dart_attachment_envelope_vector_test.dart. Skipping, not failing: "
                            + "a Java-only checkout must still build.");
            vector = mapper.readTree(in);
        }
    }

    /**
     * Non-vacuity guard, and it fails rather than skips.
     *
     * <p>The {@code @BeforeAll} above skips when the fixture is ABSENT, which is an environmental fact
     * about the checkout. A fixture that is PRESENT but does not carry an attachment is a different thing
     * entirely — it means the Dart producer regressed to the F15 behaviour — and it must be loud. Without
     * this, a regenerated-but-broken vector would make every assertion below trivially satisfiable and the
     * suite would go green while testing nothing.
     */
    @Test
    @DisplayName("the vector actually exercises F15 (guard: a vacuous fixture FAILS, it does not skip)")
    public void theVectorExercisesF15() {
        assertTrue(vector.hasNonNull("expected_media_count"),
                "vector is malformed: no expected_media_count");
        assertTrue(vector.get("expected_media_count").asInt() >= 1,
                "the Dart producer wrote a vector with no attachment — that IS F15 recurring, "
                        + "not a reason to pass");
        JsonNode media = vector.get("envelope_plaintext").get("attached_media");
        assertTrue(media != null && media.isArray() && media.size() >= 1,
                "the vector's own JSON carries no attached_media array; nothing below would be tested");
    }

    @Test
    @DisplayName("JAVA parses the DART envelope and finds the attachment (the F15 assertion)")
    public void javaFindsTheAttachmentInTheDartEnvelope() throws Exception {
        BasicMessageEncryptedContentBean content = mapper.treeToValue(
                vector.get("envelope_plaintext"), BasicMessageEncryptedContentBean.class);

        assertEquals(vector.get("expected_text").asText(), content.getTextMessage(),
                "the text must survive too — if it did not, the failure below would be about "
                        + "parsing in general rather than about the attachment");

        assertNotNull(content.getAttachedMedia(),
                "F15: Dart uploaded a blob and announced no attachment. This assertion is the one "
                        + "that would have caught it — a Java consumer reading a Dart-produced envelope.");
        assertEquals(1, content.getAttachedMedia().size());
    }

    @Test
    @DisplayName("every placeholder field survives the Dart -> Java crossing")
    public void everyPlaceholderFieldSurvives() throws Exception {
        BasicMessageEncryptedContentBean content = mapper.treeToValue(
                vector.get("envelope_plaintext"), BasicMessageEncryptedContentBean.class);
        List<ChatMediaPlaceholderBean> media = content.getAttachedMedia();
        assertNotNull(media, "see javaFindsTheAttachmentInTheDartEnvelope");
        ChatMediaPlaceholderBean p = media.get(0);

        // A partial placeholder is as unusable to the recipient as a missing one, and each of these
        // fields fails a different way: no efh and it cannot request the blob; no sed and it cannot
        // decrypt it; no uch and it cannot verify what it got against what the sender signed.
        assertEquals("image/jpeg", p.getMediaType());
        assertEquals("image_hot.jpg", p.getFileName());
        assertEquals(43253L, p.getOriginalSize(), "plaintext size");
        assertEquals(57692L, p.getSize(), "wire size (unwrapped base64 — Dart's form, see F14)");
        assertEquals("88f94d338a224f2bbded1092ae0c65f05b3911369a2fee5b9eb957ce60b1cf87",
                p.getUnencryptedContentHash(),
                "uch is the only value the sender signs ABOUT THE CONTENT");
        assertNotNull(p.getEncryptedFileHash(), "without efh the blob cannot be requested");
        assertFalse(Boolean.TRUE.equals(p.getIsTheObject()), "this is a by-reference attachment");

        assertNotNull(p.getSed(), "without the descriptor the blob cannot be decrypted");
        assertEquals("AES/GCM/NoPadding", p.getSed().getTransformation());
        assertEquals("PBKDF2WithHmacSHA512", p.getSed().getPasswordHashAlgorithm());
        assertEquals(20000, p.getSed().getIterations(),
                "iteration count must agree across platforms — a mismatch derives a different key");
        assertNotNull(p.getSed().getIv());
        assertNotNull(p.getSed().getSalt());
    }

    /**
     * Pins the silence itself, so it is a documented property rather than folklore.
     *
     * <p>This is the mechanism that made F15 undetectable from the consumer side: an envelope whose
     * {@code attached_media} is null parses perfectly and yields a bean that is indistinguishable from a
     * genuine text-only message. There is nothing for a Java client to report, which is why the defect
     * has to be caught at the producer (the test above) rather than by hardening the consumer.
     *
     * <p>If someone later "fixes" this by making the parse reject a null {@code attached_media}, this test
     * fails and they are forced to notice they would break every legitimate text message.
     */
    @Test
    @DisplayName("a dropped attachment is indistinguishable from no attachment — the reason F15 was silent")
    public void aDroppedAttachmentIsIndistinguishableFromNoAttachment_whichIsWhyThisTestExists()
            throws Exception {
        // Byte-for-byte what the F15 investigation recovered from the wire, below Jackson.
        String f15Plaintext = "{\"text_message\":\"F14 leg C from flu\",\"attached_media\":null}";
        BasicMessageEncryptedContentBean broken =
                mapper.readValue(f15Plaintext, BasicMessageEncryptedContentBean.class);

        assertEquals("F14 leg C from flu", broken.getTextMessage());
        assertNull(broken.getAttachedMedia(),
                "parses cleanly — no exception, no warning, nothing to log");

        String genuinelyTextOnly = "{\"text_message\":\"F14 leg C from flu\"}";
        BasicMessageEncryptedContentBean plain =
                mapper.readValue(genuinelyTextOnly, BasicMessageEncryptedContentBean.class);

        assertEquals(broken.getTextMessage(), plain.getTextMessage());
        assertEquals(broken.getAttachedMedia(), plain.getAttachedMedia(),
                "the dropped-attachment envelope and the genuine text-only envelope are the SAME bean. "
                        + "No consumer-side check can separate them, which is why this file tests the "
                        + "producer's output instead.");
    }
}
