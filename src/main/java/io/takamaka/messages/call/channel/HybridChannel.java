package io.takamaka.messages.call.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.messages.call.CallBytes;
import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallCrypto;
import io.takamaka.messages.call.CallError;
import io.takamaka.messages.call.CallHashes;
import io.takamaka.messages.call.CallJson;
import io.takamaka.messages.call.CallProtocolException;
import io.takamaka.messages.call.CallSignatures;
import io.takamaka.messages.call.beans.CallAnnounceBean;
import io.takamaka.messages.call.beans.CallChannelBean;
import io.takamaka.messages.call.beans.CallChannelBodyBean;
import java.security.SecureRandom;
import java.util.Map;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;

/**
 * Pairwise hybrid channels (spec §5).
 *
 * <pre>
 * ss_x       = X25519(a_priv, B.x25519)
 * (ss_k, ct) = ML-KEM-768.Encaps(B.mlkem)
 * salt       = H(call_id ‖ ann_hash(A) ‖ ann_hash(B))
 * info       = ASCII("tkm-call/v1/channel") ‖ A.x25519 ‖ B.x25519 ‖ ct ‖ ASCII(direction)
 * chain_0    = HKDF-Expand(HKDF-Extract(salt, ss_x ‖ ss_k), info, 32)       direction ∈ {"A>B", "B>A"}
 * k_msg_k    = HKDF(chain_k, "tkm-call/v1/msg")
 * chain_k+1  = HKDF(chain_k, "tkm-call/v1/chain")
 * nonce      = BE32(k) ‖ BE64(seq)
 * </pre>
 *
 * A is always the channel OPENER (the encapsulating side) and B the recipient, for both directions: the strings
 * {@code "A>B"} and {@code "B>A"} are literal (ambiguity A-5 of the C182 status report).
 *
 * <p>Box format (A-7): {@code box = nonce(12) ‖ AES-256-GCM(k_msg_k, nonce, aad, plaintext) ‖ tag(16)}, hex on
 * the wire; the receiver reads k and seq from the nonce.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class HybridChannel {

    private HybridChannel() {
    }

    // ------------------------------------------------------------------ combiner (§5.1)

    public static byte[] salt(byte[] callId, byte[] annHashA, byte[] annHashB) {
        return CallCrypto.h(callId, annHashA, annHashB);
    }

    public static byte[] info(byte[] x25519A, byte[] x25519B, byte[] ct, String direction) {
        if (!CallConstants.DIR_A_TO_B.equals(direction) && !CallConstants.DIR_B_TO_A.equals(direction)) {
            throw new IllegalArgumentException("direction");
        }
        return CallBytes.concat(CallBytes.ascii(CallConstants.L_CHANNEL), x25519A, x25519B, ct, CallBytes.ascii(direction));
    }

    public static byte[] ikm(byte[] ssX, byte[] ssK) {
        return CallBytes.concat(ssX, ssK);
    }

    public static byte[] prk(byte[] salt, byte[] ikm) {
        return CallCrypto.hkdfExtract(salt, ikm);
    }

    public static byte[] chain0(byte[] prk, byte[] info) {
        return CallCrypto.hkdfExpand(prk, info, 32);
    }

    /** All intermediates of the combiner, for both directions (vector group 3). */
    public record Combined(byte[] ssX, byte[] ssK, byte[] ct, byte[] salt, byte[] ikm, byte[] prk,
            byte[] infoAB, byte[] infoBA, byte[] chainAB, byte[] chainBA) {

    }

    /** The combiner from both shared secrets (the part both ends compute identically). */
    public static Combined combine(byte[] callId, byte[] annHashA, byte[] annHashB, byte[] x25519A, byte[] x25519B,
            byte[] ssX, byte[] ssK, byte[] ct) {
        byte[] s = salt(callId, annHashA, annHashB);
        byte[] i = ikm(ssX, ssK);
        byte[] p = prk(s, i);
        byte[] infoAB = info(x25519A, x25519B, ct, CallConstants.DIR_A_TO_B);
        byte[] infoBA = info(x25519A, x25519B, ct, CallConstants.DIR_B_TO_A);
        return new Combined(ssX, ssK, ct, s, i, p, infoAB, infoBA, chain0(p, infoAB), chain0(p, infoBA));
    }

    // ------------------------------------------------------------------ chain, keys, box (§5.3)

    public static byte[] msgKey(byte[] chainK) {
        return CallCrypto.hkdf(chainK, CallBytes.ascii(CallConstants.L_MSG));
    }

    public static byte[] nextChain(byte[] chainK) {
        return CallCrypto.hkdf(chainK, CallBytes.ascii(CallConstants.L_CHAIN));
    }

    public static byte[] nonce(long k, long seq) {
        return CallBytes.concat(CallBytes.be32(k), CallBytes.be64(seq));
    }

    /** box = nonce ‖ ciphertext ‖ tag, under k_msg of {@code chainK}. */
    public static byte[] seal(byte[] chainK, long k, long seq, byte[] aad, byte[] plaintext) {
        byte[] nonce = nonce(k, seq);
        return CallBytes.concat(nonce, CallCrypto.aesGcmSeal(msgKey(chainK), nonce, aad, plaintext));
    }

    /** Result of opening a box. */
    public record Opened(long k, long seq, byte[] plaintext) {

    }

    /** Opens a box under {@code chainK}; refuses a box whose nonce names another k. */
    public static Opened open(byte[] chainK, long expectedK, byte[] aad, byte[] box) throws CallCrypto.AeadException {
        if (box.length < CallConstants.GCM_NONCE_LEN + CallConstants.GCM_TAG_LEN) {
            throw new CallCrypto.AeadException("box too short");
        }
        long k = CallBytes.readBe32(box, 0);
        long seq = CallBytes.readBe64(box, 4);
        if (k != expectedK) {
            throw new CallCrypto.AeadException("box under chain index " + k + ", expected " + expectedK);
        }
        if (seq < 0) {
            throw new CallCrypto.AeadException("seq out of range");
        }
        byte[] nonce = java.util.Arrays.copyOf(box, CallConstants.GCM_NONCE_LEN);
        byte[] ct = java.util.Arrays.copyOfRange(box, CallConstants.GCM_NONCE_LEN, box.length);
        return new Opened(k, seq, CallCrypto.aesGcmOpen(msgKey(chainK), nonce, aad, ct));
    }

    // ------------------------------------------------------------------ channel open (§5.2)

    /** The AAD of a channel open's box: canonical form of the object without {@code box} and {@code sg} (A-6). */
    public static byte[] channelAad(CallChannelBean ch) {
        return CallBytes.ascii(CallJson.canonicalWithout(ch, "box", "sg"));
    }

    /** Body plaintext: ASCII of the canonical JSON of the body ({@code {}} when empty). */
    public static byte[] bodyBytes(CallChannelBodyBean body) {
        return CallBytes.ascii(CallJson.canonical(body == null ? new CallChannelBodyBean() : body));
    }

    /** What the opener gets back: the signed object, its channel state, and every intermediate. */
    public record OpenResult(CallChannelBean channel, ChannelState state, Combined combined, byte[] aad, byte[] body) {

    }

    /**
     * Builds and signs the channel open from A to B. {@code encapsM} fixes the ML-KEM randomness (vectors only;
     * null draws it from {@code random}).
     */
    public static OpenResult openChannel(String callIdHex, CallAnnounceBean annA, byte[] aX25519Priv,
            CallAnnounceBean annB, MlKem768 kem, byte[] encapsM, SecureRandom random, CallChannelBodyBean body,
            AsymmetricCipherKeyPair identityA, long ts) {
        byte[] callId = CallBytes.unhex(callIdHex, CallConstants.HASH_LEN);
        byte[] annHashA = CallHashes.annHash(annA);
        byte[] annHashB = CallHashes.annHash(annB);
        byte[] xA = CallBytes.unhex(annA.getX25519(), 32);
        byte[] xB = CallBytes.unhex(annB.getX25519(), 32);
        if (!java.util.Arrays.equals(CallCrypto.x25519Public(aX25519Priv), xA)) {
            throw new IllegalArgumentException("X25519 private key does not match A's announcement");
        }
        byte[] ssX = CallCrypto.x25519(aX25519Priv, xB);
        byte[] ekB = CallBytes.unhex(annB.getMlkem(), MlKem768.EK_LEN);
        MlKem768.Encapsulation enc = encapsM != null ? kem.encaps(ekB, encapsM) : kem.encaps(ekB, random);
        Combined c = combine(callId, annHashA, annHashB, xA, xB, ssX, enc.sharedSecret(), enc.ciphertext());

        CallChannelBean ch = new CallChannelBean();
        ch.setAud(CallConstants.AUD_LEG + annB.getLeg());
        ch.setTs(ts);
        ch.setCall(callIdHex);
        ch.setFrom(annA.getLeg());
        ch.setTo(annB.getLeg());
        ch.setAnnf(CallBytes.hex(annHashA));
        ch.setAnnt(CallBytes.hex(annHashB));
        ch.setCt(CallBytes.hex(enc.ciphertext()));
        ch.setSuite(CallConstants.SUITE_HYBRID_1);
        ch.setF(CallSignatures.identityOf(identityA));
        byte[] aad = channelAad(ch);
        byte[] bodyBytes = bodyBytes(body);
        ChannelState state = new ChannelState(true, c.chainAB(), c.chainBA(), 0, -1);
        ch.setBox(CallBytes.hex(state.seal(aad, bodyBytes)));
        CallSignatures.sign(ch, identityA);
        return new OpenResult(ch, state, c, aad, bodyBytes);
    }

    /** What the recipient gets back. */
    public record AcceptResult(ChannelState state, CallChannelBodyBean body, byte[] bodyBytes, CallAnnounceBean opener) {

    }

    /**
     * Verifies and accepts a channel open on B (spec §5.2): the signature (type {@code channel}, audience
     * {@code leg:<B>}); {@code annt} is B's own announcement hash and {@code to} B's leg; {@code annf} is in B's
     * verified roster and its identity is {@code f} and its leg {@code from}; the suite; then the hybrid
     * derivation and the box (k = 0).
     *
     * @param verifiedRoster B's verified announcements, keyed by ann_hash hex
     */
    public static AcceptResult acceptChannel(CallChannelBean ch, Map<String, CallAnnounceBean> verifiedRoster,
            CallAnnounceBean annB, byte[] bX25519Priv, byte[] bMlkemDk, MlKem768 kem) throws CallProtocolException {
        CallSignatures.verify(ch, CallConstants.T_CHANNEL, CallConstants.AUD_LEG + annB.getLeg());
        String annHashB = CallHashes.annHashHex(annB);
        if (!annHashB.equals(ch.getAnnt()) || !annB.getLeg().equals(ch.getTo())) {
            throw refused("not addressed to this leg");
        }
        if (!annB.getCall().equals(ch.getCall())) {
            throw refused("call mismatch");
        }
        CallAnnounceBean annA = verifiedRoster.get(ch.getAnnf());
        if (annA == null) {
            throw refused("opener not in the verified roster");
        }
        if (!annA.getF().equals(ch.getF()) || !annA.getLeg().equals(ch.getFrom())) {
            throw refused("opener identity or leg mismatch");
        }
        if (!CallConstants.SUITE_HYBRID_1.equals(ch.getSuite()) || !CallConstants.SUITE_HYBRID_1.equals(annA.getSuite())) {
            throw refused("suite");
        }
        byte[] ct;
        byte[] box;
        try {
            ct = CallBytes.unhex(ch.getCt(), MlKem768.CT_LEN);
            box = CallBytes.unhex(ch.getBox());
        } catch (IllegalArgumentException ex) {
            throw refused("encoding");
        }
        byte[] xA = CallBytes.unhex(annA.getX25519(), 32);
        byte[] xB = CallBytes.unhex(annB.getX25519(), 32);
        byte[] ssX;
        try {
            ssX = CallCrypto.x25519(bX25519Priv, xA);
        } catch (IllegalStateException ex) {
            throw refused("X25519 all-zero");
        }
        byte[] ssK = kem.decaps(bMlkemDk, ct);
        Combined c = combine(CallBytes.unhex(ch.getCall(), CallConstants.HASH_LEN),
                CallBytes.unhex(ch.getAnnf(), 32), CallBytes.unhex(annHashB, 32), xA, xB, ssX, ssK, ct);
        ChannelState state = new ChannelState(false, c.chainBA(), c.chainAB(), 0, -1);
        byte[] bodyBytes;
        try {
            bodyBytes = state.open(channelAad(ch), box);
        } catch (CallCrypto.AeadException ex) {
            throw refused("box does not open");
        }
        CallChannelBodyBean body;
        try {
            body = CallJson.parse(new String(bodyBytes, java.nio.charset.StandardCharsets.US_ASCII), CallChannelBodyBean.class);
        } catch (JsonProcessingException ex) {
            throw refused("body");
        }
        return new AcceptResult(state, body, bodyBytes, annA);
    }

    private static CallProtocolException refused(String reason) {
        return new CallProtocolException(CallError.KEY_REFUSED, reason);
    }
}
