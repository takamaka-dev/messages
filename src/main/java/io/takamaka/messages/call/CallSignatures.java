package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallSignedObject;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.TkmCypherProviderBCED25519;
import io.takamaka.wallet.beans.TkmCypherBean;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.KeyContexts;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.util.encoders.UrlBase64;

/**
 * Signatures of call objects (spec §3.2):
 *
 * <pre>
 * msg = ASCII("tkm-call/v1/" + t) ‖ 0x00 ‖ UTF-8(canonical(obj without sg))
 * sg  = Ed25519.sign(identity_private_key, msg)          — 64 bytes, lowercase hex on the wire
 * </pre>
 *
 * <p>The wallet-core provider ({@link TkmCypherProviderBCED25519}) converts the message String to octets with BC
 * {@code Strings.toByteArray} (low 8 bits of each char, DR-027). This class refuses any char above 0x7F on sign
 * AND on verify, so the octets are identical to ASCII/UTF-8 and the Dart port cannot diverge.
 *
 * <p>Because every call message starts with {@code tkm-call/} and every chat message with {@code {}, no
 * signature verifies in the other system (N13). Call code MUST NOT use the chat verify function; chat code MUST
 * NOT use this one.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallSignatures {

    private CallSignatures() {
    }

    /** {@code canonical(obj)}: JCS of the object with {@code sg} removed (spec §2.3). */
    public static String canonical(CallSignedObject obj) {
        return CallJson.canonicalWithout(obj, "sg");
    }

    /**
     * The signing message as a Java String: {@code "tkm-call/v1/" + t + '\u0000' + canonical}. Throws
     * {@link IllegalArgumentException} on any non-ASCII char.
     */
    public static String signingMessage(String t, String canonical) {
        Objects.requireNonNull(t, "t");
        Objects.requireNonNull(canonical, "canonical");
        if (!CallBytes.isAscii(t) || !CallBytes.isAscii(canonical)) {
            throw new IllegalArgumentException("non-ASCII character in a signed call object");
        }
        if (t.isEmpty() || t.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("invalid object type");
        }
        return CallConstants.PREFIX + t + '\u0000' + canonical;
    }

    /** The signed octets of spec §3.2 (ASCII). */
    public static byte[] signingOctets(String t, String canonical) {
        return CallBytes.ascii(signingMessage(t, canonical));
    }

    /** The signed octets of a bean. */
    public static byte[] signingOctets(CallSignedObject obj) {
        return signingOctets(obj.getT(), canonical(obj));
    }

    /** Registration-string form of an Ed25519 public key: Base64URL with {@code .} padding. */
    public static String identityOf(AsymmetricCipherKeyPair keyPair) {
        return urlBase64(((Ed25519PublicKeyParameters) keyPair.getPublic()).getEncoded());
    }

    /**
     * Signs in place with a raw Ed25519 key pair: sets {@code f} (or checks it if already set), clears and then
     * sets {@code sg}. Returns the hex signature.
     */
    public static String sign(CallSignedObject obj, AsymmetricCipherKeyPair keyPair) {
        String identity = identityOf(keyPair);
        if (obj.getF() == null) {
            obj.setF(identity);
        } else if (!obj.getF().equals(identity)) {
            throw new IllegalArgumentException("f does not name the signing key");
        }
        if (obj.getV() == null) {
            obj.setV(CallConstants.VERSION);
        }
        obj.setSg(null);
        String msg = signingMessage(obj.getT(), canonical(obj));
        TkmCypherBean res = TkmCypherProviderBCED25519.sign(keyPair, msg);
        if (!res.isValid()) {
            throw new IllegalStateException("Ed25519 signing failed", res.getEx());
        }
        String sgHex = CallBytes.hex(urlUnBase64(res.getSignature()));
        obj.setSg(sgHex);
        return sgHex;
    }

    /** Signs in place with the wallet key at {@code index} (Ed25519 wallets only). */
    public static String sign(CallSignedObject obj, InstanceWalletKeystoreInterface iwk, int index) throws WalletException {
        if (iwk.getWalletCypher() != KeyContexts.WalletCypher.Ed25519BC) {
            throw new IllegalArgumentException("call objects are signed with Ed25519 only");
        }
        String identity = iwk.getPublicKeyAtIndexURL64(index);
        if (obj.getF() == null) {
            obj.setF(identity);
        }
        return sign(obj, iwk.getKeyPairAtIndex(index));
    }

    /**
     * Verifies a parsed object (spec §3.2): {@code t} equals {@code expectedT}, {@code aud} equals
     * {@code expectedAud}, every char ASCII, {@code sg} a 64-byte hex, and the Ed25519 signature verifies under
     * {@code f}. The signed octets are rebuilt from the PARSED bean, never from wire bytes.
     *
     * @throws CallProtocolException {@link CallError#BAD_SIGNATURE} on any failure
     */
    public static void verify(CallSignedObject obj, String expectedT, String expectedAud) throws CallProtocolException {
        verify(obj, expectedT, expectedAud, null);
    }

    /**
     * As {@link #verify(CallSignedObject, String, String)}, and additionally requires {@code f} to equal
     * {@code expectedSigner} when it is not null (pinned service key, N14; the committer; the announcer).
     */
    public static void verify(CallSignedObject obj, String expectedT, String expectedAud, String expectedSigner)
            throws CallProtocolException {
        if (obj == null) {
            throw bad("null object");
        }
        if (obj.getV() == null || obj.getV() != CallConstants.VERSION) {
            throw bad("version");
        }
        if (expectedT == null || !expectedT.equals(obj.getT())) {
            throw bad("type context");
        }
        if (expectedAud == null || !expectedAud.equals(obj.getAud())) {
            throw bad("audience");
        }
        if (obj.getF() == null || !CallBytes.isAscii(obj.getF())) {
            throw bad("signer");
        }
        if (expectedSigner != null && !expectedSigner.equals(obj.getF())) {
            throw bad("unexpected signer");
        }
        if (obj.getSg() == null || !CallBytes.isLowerHex(obj.getSg(), CallConstants.SIG_LEN)) {
            throw bad("signature encoding");
        }
        String canonical = canonical(obj);
        if (!CallBytes.isAscii(canonical)) {
            throw bad("non-ASCII");
        }
        String msg = signingMessage(obj.getT(), canonical);
        TkmCypherBean res = TkmCypherProviderBCED25519.verify(obj.getF(), urlBase64(CallBytes.unhex(obj.getSg())), msg);
        if (!res.isValid()) {
            throw bad("signature");
        }
    }

    /** Boolean form of {@link #verify(CallSignedObject, String, String)}. */
    public static boolean isValid(CallSignedObject obj, String expectedT, String expectedAud) {
        try {
            verify(obj, expectedT, expectedAud);
            return true;
        } catch (CallProtocolException ex) {
            return false;
        }
    }

    private static CallProtocolException bad(String reason) {
        return new CallProtocolException(CallError.BAD_SIGNATURE, reason);
    }

    static String urlBase64(byte[] b) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            UrlBase64.encode(b, baos);
            return baos.toString(java.nio.charset.StandardCharsets.US_ASCII);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static byte[] urlUnBase64(String s) {
        return UrlBase64.decode(s);
    }
}
