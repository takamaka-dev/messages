package io.takamaka.messages.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.messages.call.beans.CallChannelBean;
import io.takamaka.messages.call.beans.CallCommitBean;
import io.takamaka.messages.call.beans.CallSignedObject;
import io.takamaka.messages.call.channel.HybridChannel;
import io.takamaka.messages.call.channel.MlKem768;
import io.takamaka.messages.call.conv.ConvSeed;
import io.takamaka.messages.call.epoch.Commits;
import io.takamaka.messages.call.epoch.EpochSchedule;
import io.takamaka.messages.call.service.ServiceNonce;
import io.takamaka.messages.call.sframe.SframeCipher;
import io.takamaka.messages.call.sframe.SframeHeader;
import io.takamaka.messages.call.sframe.SframeKeys;
import io.takamaka.messages.utils.ChatCryptoUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The call vector file ({@code call_vectors_v1.json}, spec §12):
 * <ol>
 * <li>the committed copy equals what the Java reference generates now (drift guard);</li>
 * <li>the published copy in {@code rschat-docs/security/vectors/} is byte-identical (SKIPPED, not passed, when
 * that tree is not next to this worktree);</li>
 * <li>every value of every group is RE-DERIVED from the file's own inputs — not from the scenario objects — so the
 * file is self-consistent the way the Dart port will consume it.</li>
 * </ol>
 */
class CallVectorFileTest {

    static final String RESOURCE = "/call/call_vectors_v1.json";
    static String text;
    static JsonNode root;
    static final ObjectMapper M = new ObjectMapper();
    static final MlKem768 KEM = new io.takamaka.messages.call.channel.BcMlKem768();

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = CallVectorFileTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "missing " + RESOURCE + " — run CallVectorGenerator");
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        root = M.readTree(text);
    }

    @Test
    void committedFileEqualsTheReferenceOutput() throws Exception {
        assertEquals(CallVectorGenerator.render(), text, "vectors drifted from the Java reference: regenerate deliberately");
        assertTrue(CallBytes.isAscii(text));
        assertFalse(text.contains("test-double"), "no test-double ML-KEM value may remain in the vectors");
        assertTrue(root.get("mlkem").asText().startsWith("ML-KEM-768 FIPS 203"));
    }

    @Test
    void publishedCopyIsIdentical() throws Exception {
        Path p = null;
        String prop = System.getProperty("call.vectors.docs");
        for (String c : new String[]{prop, "../../rschat-docs/security/vectors/call_vectors_v1.json",
            "../rschat-docs/security/vectors/call_vectors_v1.json"}) {
            if (c != null && Files.isRegularFile(Paths.get(c))) {
                p = Paths.get(c);
                break;
            }
        }
        Assumptions.assumeTrue(p != null, "rschat-docs not found next to this worktree: published copy not compared");
        assertEquals(text, Files.readString(p), "published vectors differ from the committed copy");
    }

    // ------------------------------------------------------------------ helpers

    static byte[] hx(JsonNode n, String k) {
        assertNotNull(n.get(k), "missing field " + k);
        return CallBytes.unhex(n.get(k).asText());
    }

    static String s(JsonNode n, String k) {
        assertNotNull(n.get(k), "missing field " + k);
        return n.get(k).asText();
    }

    static JsonNode fixtures() {
        return root.get("fixtures");
    }

    static String identity(String name) {
        return s(fixtures().get("identities").get(name), "identity");
    }

    static JsonNode leg(String name) {
        return fixtures().get("legs").get(name);
    }

    static boolean rawEd25519Verify(String identity, byte[] octets, byte[] sig) {
        Ed25519Signer v = new Ed25519Signer();
        v.init(false, new Ed25519PublicKeyParameters(CallSignatures.urlUnBase64(identity), 0));
        v.update(octets, 0, octets.length);
        return v.verifySignature(sig);
    }

    // ------------------------------------------------------------------ fixtures

    @Test
    void fixturesFollowTheSeedRule() {
        Iterator<Map.Entry<String, JsonNode>> it = fixtures().get("identities").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            byte[] seed = CallCrypto.h(CallBytes.ascii("tkm-call/v1/vectors/identity/" + e.getKey()));
            assertEquals(CallBytes.hex(seed), s(e.getValue(), "ed25519_seed"));
            assertEquals(CallSignatures.identityOf(CallTestKeys.ed25519(seed)), s(e.getValue(), "identity"));
        }
        it = fixtures().get("legs").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            assertEquals(s(e.getValue(), "x25519_pub"), CallBytes.hex(CallCrypto.x25519Public(hx(e.getValue(), "x25519_priv"))));
            assertEquals(16, s(e.getValue(), "leg_id").length());
        }
    }

    // ------------------------------------------------------------------ group 1

    @Test
    void group1SignedObjects() throws Exception {
        Set<String> types = new HashSet<>();
        for (JsonNode n : root.get("group1_signed_objects")) {
            CallSignedObject o = CallJson.parse(s(n, "wire"));
            String t = s(n, "t");
            types.add(t);
            assertEquals(t, o.getT());
            assertEquals(identity(s(n, "signer")), o.getF());
            assertEquals(s(n, "f"), o.getF());
            assertEquals(s(n, "canonical"), CallSignatures.canonical(o), t);
            byte[] octets = CallBytes.concat(CallBytes.ascii("tkm-call/v1/" + t), new byte[]{0}, CallBytes.ascii(s(n, "canonical")));
            assertEquals(s(n, "signing_octets"), CallBytes.hex(octets), t);
            assertEquals(s(n, "sg"), o.getSg());
            assertTrue(rawEd25519Verify(o.getF(), octets, CallBytes.unhex(o.getSg())), t + " raw Ed25519");
            CallSignatures.verify(o, t, s(n, "expected_aud"));
        }
        assertEquals(Set.of("create", "era", "announce", "channel", "commit", "goodbye", "decline", "ring", "grant",
                "manifest", "lookup", "mute", "unmute", "records"), types, "every type of spec 3.3");
    }

    // ------------------------------------------------------------------ group 2

    @Test
    void group2Hashes() throws Exception {
        JsonNode g = root.get("group2_hashes");
        CallSignedObject create = CallJson.parse(s(g, "create_wire"));
        String canon = CallSignatures.canonical(create);
        assertEquals(s(g, "call_id"), CallBytes.hex(CallCrypto.h(CallBytes.ascii("tkm-call/v1/id"), CallBytes.ascii(canon))));
        assertEquals(s(g, "era_hash_0"), CallBytes.hex(CallCrypto.h(CallBytes.ascii("tkm-call/v1/era"), CallBytes.ascii(canon))));
        CallSignedObject era = CallJson.parse(s(g, "era1_wire"));
        assertEquals(s(g, "era_hash_1"), CallBytes.hex(CallCrypto.h(CallBytes.ascii("tkm-call/v1/era"),
                CallBytes.ascii(CallSignatures.canonical(era)))));
        for (JsonNode a : g.get("announcements")) {
            CallSignedObject ann = CallJson.parse(s(a, "wire"));
            assertEquals(s(a, "ann_hash"), CallBytes.hex(CallCrypto.h(CallBytes.ascii("tkm-call/v1/ann"),
                    CallBytes.ascii(CallSignatures.canonical(ann)))), s(a, "name"));
            CallSignatures.verify(ann, "announce", ann.getAud());
        }
    }

    // ------------------------------------------------------------------ group 3 + 4

    @Test
    void group3HybridCombiner() throws Exception {
        JsonNode g = root.get("group3_hybrid_combiner");
        JsonNode x = g.get("x25519_rfc7748");
        assertEquals(s(x, "shared"), CallBytes.hex(CallCrypto.x25519(hx(x, "alice_priv"), hx(x, "bob_pub"))));
        assertEquals(s(x, "alice_pub"), CallBytes.hex(CallCrypto.x25519Public(hx(x, "alice_priv"))));
        for (JsonNode c : g.get("channels")) {
            String name = s(c, "name");
            JsonNode la = leg(s(c, "A")), lb = leg(s(c, "B"));
            assertEquals(s(c, "x25519_A"), s(la, "x25519_pub"));
            assertEquals(s(c, "x25519_B"), s(lb, "x25519_pub"));
            MlKem768.KeyPair kp = KEM.keyGen(hx(lb, "mlkem_d"), hx(lb, "mlkem_z"));
            assertEquals(s(c, "mlkem_ek_B"), CallBytes.hex(kp.ek()), name);
            assertEquals(s(c, "mlkem_dk_B"), CallBytes.hex(kp.dk()), name);
            MlKem768.Encapsulation enc = KEM.encaps(kp.ek(), hx(c, "encaps_m"));
            assertEquals(s(c, "ct"), CallBytes.hex(enc.ciphertext()), name);
            assertEquals(s(c, "ss_k"), CallBytes.hex(enc.sharedSecret()), name);
            byte[] ssX = CallCrypto.x25519(hx(la, "x25519_priv"), hx(c, "x25519_B"));
            assertEquals(s(c, "ss_x"), CallBytes.hex(ssX), name);
            assertArrayEquals(ssX, CallCrypto.x25519(hx(lb, "x25519_priv"), hx(c, "x25519_A")), name + " symmetric");
            HybridChannel.Combined k = HybridChannel.combine(hx(c, "call_id"), hx(c, "ann_hash_A"), hx(c, "ann_hash_B"),
                    hx(c, "x25519_A"), hx(c, "x25519_B"), ssX, enc.sharedSecret(), enc.ciphertext());
            assertEquals(s(c, "salt"), CallBytes.hex(k.salt()), name);
            assertEquals(s(c, "ikm"), CallBytes.hex(k.ikm()), name);
            assertEquals(s(c, "prk"), CallBytes.hex(k.prk()), name);
            assertEquals(s(c, "info_A>B"), CallBytes.hex(k.infoAB()), name);
            assertEquals(s(c, "info_B>A"), CallBytes.hex(k.infoBA()), name);
            assertEquals(s(c, "chain0_A>B"), CallBytes.hex(k.chainAB()), name);
            assertEquals(s(c, "chain0_B>A"), CallBytes.hex(k.chainBA()), name);
            // the channel object: signed by A, box opens under chain0 A>B, k = 0, AAD = canonical without box/sg
            CallChannelBean ch = CallJson.parse(s(c, "channel_wire"), CallChannelBean.class);
            CallSignatures.verify(ch, "channel", "leg:" + s(lb, "leg_id"), identity(s(c, "A")));
            assertEquals(s(c, "ct"), ch.getCt());
            assertEquals(s(c, "box_aad"), CallJson.canonicalWithout(ch, "box", "sg"));
            HybridChannel.Opened op = HybridChannel.open(k.chainAB(), 0, CallBytes.ascii(s(c, "box_aad")), CallBytes.unhex(ch.getBox()));
            assertEquals(0, op.seq());
            assertEquals(s(c, "body"), new String(op.plaintext(), StandardCharsets.US_ASCII), name);
        }
    }

    @Test
    void group4ChainRatchet() throws Exception {
        JsonNode g = root.get("group4_chain_ratchet");
        JsonNode chain = g.get("chain");
        byte[] ck = hx(chain.get(0), "chain_k");
        assertEquals(s(root.get("group3_hybrid_combiner").get("channels").get(0), "chain0_A>B"), CallBytes.hex(ck));
        for (int k = 0; k < chain.size(); k++) {
            assertEquals(k, chain.get(k).get("k").asInt());
            assertEquals(s(chain.get(k), "chain_k"), CallBytes.hex(ck));
            assertEquals(s(chain.get(k), "k_msg_k"), CallBytes.hex(CallCrypto.hkdf(ck, CallBytes.ascii("tkm-call/v1/msg"))));
            ck = CallCrypto.hkdf(ck, CallBytes.ascii("tkm-call/v1/chain"));
        }
        assertEquals(4, chain.size());
        for (String b : new String[]{"box_channel_open", "box_era_commit"}) {
            JsonNode n = g.get(b);
            int k = n.get("k").asInt();
            long seq = n.get("seq").asLong();
            assertEquals(s(n, "nonce"), CallBytes.hex(CallBytes.concat(CallBytes.be32(k), CallBytes.be64(seq))));
            byte[] pt = b.equals("box_channel_open") ? CallBytes.ascii(s(n, "plaintext")) : hx(n, "plaintext");
            byte[] box = HybridChannel.seal(hx(chain.get(k), "chain_k"), k, seq, CallBytes.ascii(s(n, "aad")), pt);
            assertEquals(s(n, "box"), CallBytes.hex(box), b);
            byte[] gcm = CallCrypto.aesGcmSeal(hx(chain.get(k), "k_msg_k"), hx(n, "nonce"), CallBytes.ascii(s(n, "aad")), pt);
            assertEquals(s(n, "box"), s(n, "nonce") + CallBytes.hex(gcm), b + " = nonce || AES-256-GCM");
        }
    }

    // ------------------------------------------------------------------ group 5

    @Test
    void group5EpochSchedule() throws Exception {
        JsonNode epochs = root.get("group5_epoch_schedule");
        assertEquals(5, epochs.size());
        byte[] prev = null;
        for (JsonNode e : epochs) {
            long n = e.get("epoch").asLong();
            String kind = s(e, "kind");
            byte[] era = hx(e, "era_hash");
            List<byte[]> roster = new ArrayList<>();
            List<String> rosterHex = new ArrayList<>();
            e.get("roster").forEach(r -> {
                roster.add(CallBytes.unhex(r.asText()));
                rosterHex.add(r.asText());
            });
            assertEquals(EpochSchedule.sortedHex(rosterHex), rosterHex, "roster sorted");
            byte[] cat = new byte[0];
            for (byte[] r : roster) {
                cat = CallBytes.concat(cat, r);
            }
            byte[] rh = CallCrypto.h(cat, era);
            assertEquals(s(e, "roster_hash"), CallBytes.hex(rh), "epoch " + n);
            byte[] secret;
            switch (kind) {
                case "initial" ->
                    secret = hx(e, "commit_secret");
                case "step" ->
                    secret = CallCrypto.hkdf(prev, CallBytes.concat(CallBytes.ascii("tkm-call/v1/step"), hx(e, "joiner_ann_hash"), rh));
                default -> {
                    byte[] salt = CallCrypto.h(rh, era, CallBytes.be32(n));
                    assertEquals(s(e, "salt_e"), CallBytes.hex(salt), "epoch " + n);
                    secret = CallCrypto.hkdfExpand(CallCrypto.hkdfExtract(salt, CallBytes.concat(prev, hx(e, "commit_secret"))),
                            CallBytes.ascii("tkm-call/v1/epoch"), 32);
                }
            }
            assertEquals(s(e, "epoch_secret"), CallBytes.hex(secret), "epoch " + n);
            byte[] bc = CallCrypto.hkdf(secret, CallBytes.concat(CallBytes.ascii("tkm-call/v1/broadcast"), era));
            assertEquals(s(e, "broadcast"), CallBytes.hex(bc));
            assertFalse(e.has("listener_text_key"));
            Iterator<Map.Entry<String, JsonNode>> it = e.get("per_leg").fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> p = it.next();
                byte[] legId = CallBytes.unhex(p.getKey());
                // A-15 RULED: frame and text keys come from broadcast_e
                assertEquals(s(p.getValue(), "sender_base"), CallBytes.hex(CallCrypto.hkdf(bc,
                        CallBytes.concat(CallBytes.ascii("tkm-call/v1/sender"), legId))));
                assertEquals(s(p.getValue(), "text_key"), CallBytes.hex(CallCrypto.hkdf(bc,
                        CallBytes.concat(CallBytes.ascii("tkm-call/v1/text"), legId))));
            }
            byte[] code = CallCrypto.hkdf(secret, CallBytes.ascii("tkm-call/v1/code"));
            assertEquals(s(e, "code_hkdf"), CallBytes.hex(code));
            long x = 0;
            for (int i = 0; i < 5; i++) {
                x = (x << 8) | (code[i] & 0xff);
            }
            assertEquals(s(e, "code"), String.format("%012d", x % 1_000_000_000_000L));
            if (e.has("commit_wire")) {
                CallCommitBean c = CallJson.parse(s(e, "commit_wire"), CallCommitBean.class);
                CallSignatures.verify(c, "commit", c.getAud(), identity("owner"));
                assertEquals(kind, c.getKind());
                assertEquals(n, c.getEpoch());
                assertEquals(rosterHex, c.getRoster());
                assertEquals(s(e, "roster_hash"), c.getRhash());
                assertFalse(s(e, "commit_header").contains("chain_idx"), "A-8: no top-level chain_idx");
                c.getBoxes().forEach(b -> assertNotNull(b.getK(), "A-8: every box carries k"));
                assertEquals(s(e, "commit_header"), Commits.canonicalHeader(c));
                byte[] mac = CallCrypto.hmacSha512(secret, CallBytes.ascii("tkm-call/v1/confirm"),
                        CallCrypto.h(CallBytes.ascii(s(e, "commit_header"))));
                assertEquals(s(e, "confirm"), CallBytes.hex(java.util.Arrays.copyOf(mac, 16)));
                assertEquals(s(e, "confirm"), c.getConf());
            }
            prev = secret;
        }
    }

    // ------------------------------------------------------------------ group 6

    @Test
    void group6Sframe() throws Exception {
        JsonNode g = root.get("group6_sframe");
        JsonNode r = g.get("rfc9605_c3_suite5");
        assertEquals("990123456794f509d36e9beacb0e261d99c7d1e972f1fed787d4049f17ca21353c1cc24d56ceabced279", s(r, "ct"),
                "the RFC's own suite-5 ciphertext");
        SframeKeys rk = SframeKeys.derive(Long.parseUnsignedLong(s(r, "kid"), 16), hx(r, "base_key"));
        assertEquals(s(r, "sframe_key"), CallBytes.hex(rk.key()));
        assertEquals(s(r, "sframe_salt"), CallBytes.hex(rk.salt()));
        assertEquals(s(r, "ct"), CallBytes.hex(SframeCipher.encrypt(rk, Long.parseUnsignedLong(s(r, "ctr"), 16), hx(r, "metadata"), hx(r, "pt"))));

        JsonNode u = g.get("units");
        int legIndex = u.get("leg_index").asInt();
        long epoch = u.get("epoch").asLong();
        long kid = legIndex * 65536L + (epoch % 65536);
        assertEquals(s(u, "kid"), Long.toHexString(kid));
        // base key = sender_base of that leg at that epoch (group 5)
        JsonNode e4 = root.get("group5_epoch_schedule").get((int) epoch);
        assertEquals(s(u, "base_key"), s(e4.get("per_leg").get(s(leg(s(u, "leg")), "leg_id")), "sender_base"));
        SframeKeys k = SframeKeys.derive(kid, hx(u, "base_key"));
        assertEquals(s(u, "sframe_key"), CallBytes.hex(k.key()));
        assertEquals(s(u, "sframe_salt"), CallBytes.hex(k.salt()));
        Set<String> codecs = new HashSet<>();
        for (JsonNode c : u.get("cases")) {
            codecs.add(s(c, "codec"));
            long ctr = c.get("ctr").asLong();
            int pl = c.get("prefix_len").asInt();
            byte[] frame = hx(c, "frame");
            assertEquals(s(c, "header"), CallBytes.hex(SframeHeader.encode(kid, ctr)));
            assertEquals(s(c, "aad"), s(c, "header") + CallBytes.hex(java.util.Arrays.copyOf(frame, pl)));
            assertEquals(s(c, "unit"), CallBytes.hex(SframeCipher.seal(k, ctr, frame, pl)));
            assertArrayEquals(frame, SframeCipher.open(k, hx(c, "unit"), pl));
        }
        assertEquals(Set.of("opus", "vp8"), codecs);
        assertEquals("TODO", s(u.get("h264"), "status"));
    }

    // ------------------------------------------------------------------ group 7

    @Test
    void group7ConvNonceGrant() throws Exception {
        JsonNode g = root.get("group7_conv_nonce_grant");
        JsonNode c = g.get("conv_seed");
        byte[] seed = hx(c, "conv_seed");
        assertEquals(s(c, "cseed"), CallBytes.hex(CallCrypto.h(seed)));
        assertEquals(s(c, "title_hkdf"), CallBytes.hex(CallCrypto.hkdf(seed, CallBytes.ascii("tkm-call/v1/conv/title"))));
        assertEquals(s(c, "title"), "call-" + s(c, "title_hkdf").substring(0, 16));
        assertEquals(s(c, "salt"), CallBytes.hex(ConvSeed.salt(seed)));
        assertEquals(s(c, "key"), CallBytes.hex(CallCrypto.hkdf(seed, CallBytes.ascii("tkm-call/v1/conv/key"))));
        // the commitment is the one in the creation record
        CallSignedObject create = CallJson.parse(s(root.get("group2_hashes"), "create_wire"));
        assertEquals(s(c, "cseed"), ((io.takamaka.messages.call.beans.CallCreateBean) create).getCseed());
        for (JsonNode n : g.get("nonces")) {
            byte[] k = hx(n, "k_nonce");
            long t = n.get("t_ms").asLong();
            byte[] r = hx(n, "r");
            assertEquals(8, r.length);
            byte[] mac = CallCrypto.hmacSha512(k, CallBytes.be64(t), r, CallBytes.ascii(s(n, "aud")));
            assertEquals(s(n, "nonce"), CallBytes.hex(CallBytes.be64(t)) + CallBytes.hex(r)
                    + CallBytes.hex(java.util.Arrays.copyOf(mac, 16)));
            assertEquals(ServiceNonce.Check.OK, new ServiceNonce(k).peek(s(n, "nonce"), s(n, "aud"), t));
        }
        JsonNode gr = g.get("grant");
        CallSignedObject grant = CallJson.parse(s(gr, "wire"));
        CallSignatures.verify(grant, "grant", grant.getAud(), s(gr, "service_identity"));
        assertEquals(s(gr, "canonical"), CallSignatures.canonical(grant));
    }

    // ------------------------------------------------------------------ group 8

    @Test
    void group8NegativesAllFailAndTheControlPasses() throws Exception {
        Set<String> seen = new HashSet<>();
        for (JsonNode n : root.get("group8_negative")) {
            String id = s(n, "id");
            seen.add(id);
            if (id.equals("N2")) {
                assertFalse(CallVectorScenario.chatVerify(s(n, "f"), s(n, "sg"), s(n, "chat_message")), id);
                assertThrows(Exception.class, () -> ChatCryptoUtils.verifySignedMessage(s(n, "wire")), id);
                continue;
            }
            CallSignedObject o;
            try {
                o = CallJson.parse(s(n, "wire"));
            } catch (Exception ex) {
                fail(id + " must parse (the refusal must come from verification): " + ex);
                return;
            }
            if (n.has("check") && "announcement".equals(s(n, "check"))) {
                // signature valid; the announcement verifier refuses it on the FIPS 203 ek check
                CallSignatures.verify(o, s(n, "expected_t"), s(n, "expected_aud"));
                io.takamaka.messages.call.beans.CallAnnounceBean an = (io.takamaka.messages.call.beans.CallAnnounceBean) o;
                assertFalse(KEM.checkEncapsulationKey(CallBytes.unhex(an.getMlkem())), id);
                CallProtocolException ex = assertThrows(CallProtocolException.class, () -> CallAnnouncements.verify(an,
                        s(n, "expected_aud"), an.getCall(), an.getEra(), Set.of(an.getF()), Set.of(), Map.of(), KEM), id);
                assertEquals("ML-KEM encapsulation key check failed", ex.getReason(), id);
                continue;
            }
            if ("valid".equals(s(n, "expect"))) {
                CallSignatures.verify(o, s(n, "expected_t"), s(n, "expected_aud"));
                continue;
            }
            CallProtocolException ex = assertThrows(CallProtocolException.class,
                    () -> CallSignatures.verify(o, s(n, "expected_t"), s(n, "expected_aud")), id);
            assertEquals(CallError.BAD_SIGNATURE, ex.getError(), id);
            if (id.equals("N1")) {
                // the chat signature is genuinely valid in the chat system
                assertTrue(rawEd25519Verify(o.getF(), hx(n, "chat_signing_octets"), CallBytes.unhex(o.getSg())), id);
            }
            if (id.equals("N3")) {
                // the signature is genuinely valid over Java's low-8-bit octets; refused for the non-ASCII char
                assertTrue(rawEd25519Verify(o.getF(), hx(n, "java_low8_octets"), CallBytes.unhex(o.getSg())), id);
                assertEquals("non-ASCII", ex.getReason());
            }
        }
        assertEquals(Set.of("N1", "N2", "N3", "N4", "N5", "N6", "N7", "N8", "N9", "P1"), seen);
    }
}
