package io.takamaka.messages.legacy.wkch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.messages.chat.conversation.TopicKeyDistributionItemBean;
import io.takamaka.wallet.TkmCypherProviderBCRSA4096ENC256;
import io.takamaka.wallet.utils.TkmSignUtils;
import java.io.InputStream;
import java.math.BigInteger;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F340 — the load-bearing proof that the Java port of the wallet app's legacy WKCH derivation is
 * BYTE-EXACT: modulus and public key of indexes 0 and 1 of the public test seed MN-001 must equal what the
 * UNCHANGED SDK code (takamaka-sdk-wrap 1a1b245) produced ({@code legacy_wkch_rsa_vectors.json}, copied
 * from {@code rschat-docs/security/vectors/chat_rsa_invite_key_vectors.json}).
 */
class LegacyWkchRsaKeyDerivationVectorTest {

    private static String seed;
    private static JsonNode vectors;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = LegacyWkchRsaKeyDerivationVectorTest.class
                .getResourceAsStream("/legacy_wkch_rsa_vectors.json")) {
            assertNotNull(in, "legacy_wkch_rsa_vectors.json fixture missing");
            JsonNode root = new ObjectMapper().readTree(in);
            seed = root.get("seed").asText();
            vectors = root.get("vectors");
        }
        LegacyWkchRsaKeyDerivation.clearCache();
    }

    private static JsonNode vector(String scheme, int index) {
        for (JsonNode v : vectors) {
            if (scheme.equals(v.get("scheme").asText()) && v.get("index").asInt() == index) {
                return v;
            }
        }
        throw new AssertionError("no vector " + scheme + "/" + index);
    }

    private static void assertVector(int index) throws Exception {
        JsonNode v = vector("legacy_wkch", index);
        AsymmetricCipherKeyPair kp = LegacyWkchRsaKeyDerivation.deriveKeyPair(seed, index);
        RSAKeyParameters pub = (RSAKeyParameters) kp.getPublic();
        assertEquals(v.get("modulus_hex").asText(), pub.getModulus().toString(16),
                "legacy WKCH modulus at index " + index);
        assertEquals(4096, pub.getModulus().bitLength());
        assertEquals(BigInteger.valueOf(v.get("public_exponent").asLong()), pub.getExponent());
        String url64 = LegacyWkchRsaKeyDerivation.publicKeyUrl64(pub);
        assertEquals(v.get("public_key_url64").asText(), url64, "legacy WKCH public key at index " + index);
        assertEquals(TkmSignUtils.Hash256B64URL(url64), LegacyWkchRsaKeyDerivation.derivePublicKeyHash(seed, index));

        // The private half is a consistent CRT key (d*e = 1 mod (p-1)(q-1), n = p*q).
        RSAPrivateCrtKeyParameters priv = (RSAPrivateCrtKeyParameters) kp.getPrivate();
        assertEquals(pub.getModulus(), priv.getP().multiply(priv.getQ()));
        assertTrue(priv.getP().compareTo(priv.getQ()) > 0, "PointyCastle orders p > q");
        BigInteger phi = priv.getP().subtract(BigInteger.ONE).multiply(priv.getQ().subtract(BigInteger.ONE));
        assertEquals(BigInteger.ONE, priv.getExponent().multiply(pub.getExponent()).mod(phi));
    }

    @Test
    @DisplayName("F340 legacy WKCH vector index 0 (modulus f10fb6f7…) — byte-exact")
    void vectorIndex0() throws Exception {
        assertVector(0);
    }

    @Test
    @DisplayName("F340 legacy WKCH vector index 1 (modulus b1a213f9…) — byte-exact")
    void vectorIndex1() throws Exception {
        assertVector(1);
    }

    @Test
    @DisplayName("the seeded random is the wallet-core SeededRandom stream on scope __WKCH__")
    void seededRandomMatchesWalletCore() {
        LegacyWkchSeededRandom legacy = new LegacyWkchSeededRandom(seed, "__WKCH__", 1);
        io.takamaka.wallet.utils.SeededRandom core = new io.takamaka.wallet.utils.SeededRandom(seed, "__WKCH__", 1);
        for (int call = 0; call < 3; call++) {
            byte[] expected = new byte[256];
            core.nextBytes(expected);
            org.junit.jupiter.api.Assertions.assertArrayEquals(expected, legacy.nextBytes(256), "call " + call);
        }
    }

    @Test
    @DisplayName("selection by enc_key_hash: parity first, legacy second, a foreign key is UNKNOWN")
    void selectionByHash() throws Exception {
        String parity0 = vector("java_parity", 0).get("public_key_url64").asText();
        String legacy0 = vector("legacy_wkch", 0).get("public_key_url64").asText();
        String parity1 = vector("java_parity", 1).get("public_key_url64").asText();

        assertEquals(LegacyWkchInviteKeySelector.Selection.PARITY,
                LegacyWkchInviteKeySelector.select(invite(parity0, "x"), parity0, seed, 0));
        assertEquals(LegacyWkchInviteKeySelector.Selection.LEGACY_WKCH,
                LegacyWkchInviteKeySelector.select(invite(legacy0, "x"), parity0, seed, 0));
        // a key that is neither (another identity's parity key) — the existing path applies unchanged
        assertEquals(LegacyWkchInviteKeySelector.Selection.UNKNOWN,
                LegacyWkchInviteKeySelector.select(invite(parity1, "x"), parity0, seed, 0));
        // index 1's legacy key is not index 0's
        assertEquals(LegacyWkchInviteKeySelector.Selection.UNKNOWN,
                LegacyWkchInviteKeySelector.select(invite(legacy0, "x"), parity1, seed, 1));
        // without a seed the legacy key is never selected
        assertEquals(LegacyWkchInviteKeySelector.Selection.UNKNOWN,
                LegacyWkchInviteKeySelector.select(invite(legacy0, "x"), parity0, null, 0));
        // an invite without a hash is never guessed at
        TopicKeyDistributionItemBean noHash = new TopicKeyDistributionItemBean();
        noHash.setEncryptedTopicKey("x");
        assertEquals(LegacyWkchInviteKeySelector.Selection.UNKNOWN,
                LegacyWkchInviteKeySelector.select(noHash, parity0, seed, 0));
    }

    @Test
    @DisplayName("unwrap: a legacy-wrapped invite opens (regenerated, then cached); a foreign one is refused")
    void unwrapLegacyInvite() throws Exception {
        String legacy0 = vector("legacy_wkch", 0).get("public_key_url64").asText();
        String parity0 = vector("java_parity", 0).get("public_key_url64").asText();
        String conversationKey = "F340-legacy-conversation-key-0123456789";
        TopicKeyDistributionItemBean legacyInvite = invite(legacy0,
                TkmCypherProviderBCRSA4096ENC256.encrypt(legacy0, conversationKey));

        LegacyWkchRsaKeyDerivation.clearCache();
        assertFalse(LegacyWkchRsaKeyDerivation.isCached(seed, 0));
        assertEquals(conversationKey, LegacyWkchInviteKeySelector.unwrapLegacy(legacyInvite, seed, 0));
        assertTrue(LegacyWkchRsaKeyDerivation.isCached(seed, 0), "second invite must not re-derive");
        assertEquals(conversationKey, LegacyWkchInviteKeySelector.unwrapLegacy(legacyInvite, seed, 0));

        // never trial: an invite naming the parity key is refused by the legacy unwrap
        TopicKeyDistributionItemBean parityInvite = invite(parity0,
                TkmCypherProviderBCRSA4096ENC256.encrypt(parity0, conversationKey));
        assertThrows(IllegalStateException.class,
                () -> LegacyWkchInviteKeySelector.unwrapLegacy(parityInvite, seed, 0));
        // a butchered invite (legacy hash, bytes wrapped to another key) still fails
        TopicKeyDistributionItemBean butchered = invite(legacy0,
                TkmCypherProviderBCRSA4096ENC256.encrypt(parity0, conversationKey));
        assertThrows(IllegalStateException.class,
                () -> LegacyWkchInviteKeySelector.unwrapLegacy(butchered, seed, 0));
        assertNotEquals(TkmSignUtils.Hash256B64URL(parity0), TkmSignUtils.Hash256B64URL(legacy0));
    }

    private static TopicKeyDistributionItemBean invite(String publicKeyUrl64, String encKey) throws Exception {
        TopicKeyDistributionItemBean b = new TopicKeyDistributionItemBean();
        b.setEncryptionKeyHash(TkmSignUtils.Hash256B64URL(publicKeyUrl64));
        b.setEncryptedTopicKey(encKey);
        return b;
    }
}
