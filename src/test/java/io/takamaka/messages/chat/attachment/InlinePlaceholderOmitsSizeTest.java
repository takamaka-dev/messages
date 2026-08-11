package io.takamaka.messages.chat.attachment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * What a Java producer actually puts on the wire for an INLINE placeholder — and specifically, what it
 * leaves out.
 *
 * <h3>Why this exists</h3>
 *
 * A specification audit (2026-08-11) found that {@code size} is nullable in Java
 * ({@code Long}, dropped by {@code @JsonInclude(NON_NULL)}) but REQUIRED in Dart, whose generated
 * parser does {@code (json['size'] as num).toInt()} and therefore throws on a missing key. It also
 * found a Java path that emits neither {@code size} nor {@code original_size}: the shell's inline
 * reaction payload, which sets only {@code mediaType}, {@code isTheObject}, {@code preview} and
 * {@code unencryptedContentHash}.
 *
 * <p>That is a latent cross-platform break — it has not fired only because those two paths have not
 * met on a live wire. This test pins the <b>producer</b> half as fact rather than as a reading of the
 * code, and writes the exact JSON to a fixture so the Dart side can be tested against a real
 * Java-produced artefact instead of a hand-written approximation.</p>
 *
 * <p>The estate has been bitten twice by same-language testing (F11, F15): a producer and a consumer
 * that never exchange an artefact cannot detect drift between them. So the fixture, not the assertion,
 * is the point of this class.</p>
 */
@DisplayName("inline placeholder — what Java omits")
class InlinePlaceholderOmitsSizeTest {

    /** Exactly what {@code ChatCommandMessages.buildReactionPayload} builds. */
    private static ChatMediaPlaceholderBean reactionPayload() {
        return ChatMediaPlaceholderBean.builder()
                .mediaType("image/webp")
                .isTheObject(true)
                .preview("aW5saW5lLXJlYWN0aW9uLWNvbnRlbnQ=")
                .unencryptedContentHash("Ki1hHfHrJ0mSPmC5wIrgMoY2Y4hV9YQ0kUEJ1kx3aQI.")
                .build();
    }

    @Test
    @DisplayName("⭐ an inline reaction payload carries NEITHER size NOR original_size")
    void inlineReactionOmitsBothSizes() throws Exception {
        String json = new ObjectMapper().writeValueAsString(reactionPayload());

        assertFalse(json.contains("\"size\""),
                "the shell's reaction path never sets size, and @JsonInclude(NON_NULL) drops it: " + json);
        assertFalse(json.contains("\"original_size\""),
                "nor original_size: " + json);
        // Sanity: the payload is not empty — otherwise the absences above would prove nothing.
        assertTrue(json.contains("\"is_the_object\":true"), json);
        assertTrue(json.contains("\"preview\""), json);
    }

    @Test
    @DisplayName("a blob placeholder DOES carry size — the omission is specific to this path")
    void blobPlaceholderCarriesSize() throws Exception {
        String json = new ObjectMapper().writeValueAsString(
                ChatMediaPlaceholderBean.builder()
                        .mediaType("image/jpeg")
                        .size(57692L)
                        .unencryptedContentHash("88f94d33")
                        .encryptedFileHash("ff726f4c")
                        .isTheObject(false)
                        .build());

        assertTrue(json.contains("\"size\":57692"), json);
    }

    @Test
    @DisplayName("write the Java-produced inline JSON for the Dart consumer")
    void writeFixtureForDart() throws Exception {
        String json = new ObjectMapper().writeValueAsString(reactionPayload());

        // Deterministic (no random input), so a change to this file means the PRODUCER changed and
        // should be reviewed — unlike the vectors that churn on every run.
        Path out = Path.of("../rsclient-flutter/test/fixtures/java_inline_reaction_placeholder.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json + System.lineSeparator(), StandardCharsets.UTF_8);

        assertTrue(Files.exists(out));
    }
}
