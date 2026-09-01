package io.takamaka.messages.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.takamaka.messages.beans.MessageAddress;
import io.takamaka.messages.exception.MessageException;

/**
 * QR-04 — {@code SimpleRequestHelper.getAddress} must accept its own compact
 * output.
 *
 * <p><b>What was wrong.</b> The method opened with a branch for the 64-character
 * compact case that assigned two locals and then fell through to a
 * {@code switch} which reassigned both unconditionally. The branch was dead, so
 * a compact address went to {@code toCompactAddress}, came back
 * qTesla/undefined, failed the 19 840-character qTesla pattern, and threw
 * <em>"address not recognized (not qtesla)"</em> — while the method's own
 * javadoc specified that such an address "is considered as a compact address
 * and used directly".
 *
 * <p><b>Why it matters.</b> {@code getAddress} is the only public constructor of
 * a {@code MessageAddress}, and {@code signMessage} routes the signer's key
 * through it. A method that rejects the form it produces cannot round-trip an
 * address through the QR layer.
 *
 * <p>Recorded in {@code rschat-docs/roadmap/QR_ACTION_STRATEGY.md} as QR-04.
 */
@DisplayName("QR-04 — getAddress accepts the compact form it produces")
class SimpleRequestHelperGetAddressTest {

    /** A real Ed25519 address (Base64URL, '.' padding), 44 chars. */
    private static final String ED25519 = "4mfAa-hIJBU8_iU7IUDIgQpDZCpBVNp7oyCsMED6Y4A.";

    @Test
    @DisplayName("a 64-char Base64URL address is taken as compact, verbatim")
    void compactIsAcceptedVerbatim() throws Exception {
        String compact = "0123456789abcdef".repeat(4);
        MessageAddress a = SimpleRequestHelper.getAddress(compact);
        assertEquals("c", a.getType());
        // Verbatim: a compact address is already the identifier, so it must be
        // passed through, never re-derived.
        assertEquals(compact, a.getAddress());
    }

    @Test
    @DisplayName("⭐ getAddress is idempotent on its own compact output")
    void compactRoundTrips() throws Exception {
        // A qTesla-length input is the path that PRODUCES a compact address.
        MessageAddress produced = SimpleRequestHelper.getAddress("a".repeat(19840));
        assertEquals("c", produced.getType());
        assertEquals(64, produced.getAddress().length());

        // Feeding it back is what used to throw. This is the whole point of the
        // fix: the method must accept the form it emits.
        MessageAddress again = SimpleRequestHelper.getAddress(produced.getAddress());
        assertEquals(produced.getType(), again.getType());
        assertEquals(produced.getAddress(), again.getAddress());
    }

    @Test
    @DisplayName("the ed25519 path is unchanged — the early return swallows nothing")
    void ed25519StillResolvesToFull() throws Exception {
        // Positive control. If the compact short-circuit were too greedy this
        // would change type or address, and every signed QR would carry the
        // wrong `from`.
        MessageAddress a = SimpleRequestHelper.getAddress(ED25519);
        assertEquals("f", a.getType());
        assertEquals(ED25519, a.getAddress());
    }

    @Test
    @DisplayName("64 characters is not enough on its own — the charset still gates it")
    void sixtyFourBadCharactersIsStillRejected() {
        // The guard is length AND charset. Without this, the fix would turn
        // getAddress into a blanket accept for anything 64 bytes long.
        assertThrows(MessageException.class,
                () -> SimpleRequestHelper.getAddress("!".repeat(64)));
        assertThrows(MessageException.class,
                () -> SimpleRequestHelper.getAddress("*".repeat(64)));
    }

    @Test
    @DisplayName("an address of no recognised length is still refused")
    void unrecognisedLengthIsRefused() {
        assertThrows(MessageException.class,
                () -> SimpleRequestHelper.getAddress("too-short"));
    }
}
