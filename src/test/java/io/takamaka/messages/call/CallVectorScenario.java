package io.takamaka.messages.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.messages.call.beans.*;
import io.takamaka.messages.call.channel.ChannelState;
import io.takamaka.messages.call.channel.HybridChannel;
import io.takamaka.messages.call.channel.MlKem768;
import io.takamaka.messages.call.conv.ConvSeed;
import io.takamaka.messages.call.epoch.Commits;
import io.takamaka.messages.call.epoch.EpochSchedule;
import io.takamaka.messages.call.service.GrantBuilder;
import io.takamaka.messages.call.service.ServiceNonce;
import io.takamaka.messages.call.sframe.SframeCipher;
import io.takamaka.messages.call.sframe.SframeHeader;
import io.takamaka.messages.call.sframe.SframeKeys;
import io.takamaka.messages.utils.SimpleRequestHelper;
import io.takamaka.wallet.TkmCypherProviderBCED25519;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.util.Strings;

/**
 * The deterministic call scenario behind {@code call_vectors_v1.json} (spec §12). Fixed seeds, fixed timestamps;
 * building it twice gives the same bytes. Used by {@link CallVectorGenerator} (writes the file) and by the tests
 * (regenerate in memory and compare, then re-verify the file independently).
 *
 * <p>Scenario: owner O creates a standard call (inv O, B, C; moderator B) → O's initial commit (epoch 0) → B joins
 * (step, epoch 1; O opens O→B with the handover and conv_seed) → C joins (step, epoch 2; O→C, and C→B) → C says
 * goodbye → leave commit (epoch 3, chain k = 0) → era 1 adds D → O and B re-announce → era commit (epoch 4, k = 1).
 */
public final class CallVectorScenario {

    public static final long T0 = 1791331200000L; // 2026-10-07T00:00:00Z
    public static final String NET = "test";
    public static final String SVC = "london-1";
    public static final String AUD_SVC = CallConstants.AUD_SVC + SVC;
    public static final String AUD_RSCHAT = CallConstants.AUD_RSCHAT + NET;

    final ObjectMapper m = new ObjectMapper();
    final MlKem768 kem = new io.takamaka.messages.call.channel.BcMlKem768();
    final ServiceNonce nonces = new ServiceNonce(CallTestKeys.seed("k_nonce"));

    // identities
    final Map<String, AsymmetricCipherKeyPair> ids = new LinkedHashMap<>();
    // legs
    final Map<String, Leg> legs = new LinkedHashMap<>();

    /** One leg's fixed ephemeral material. */
    static final class Leg {

        String name;
        String identity;
        byte[] legId;
        int legIndex;
        byte[] xPriv;
        byte[] xPub;
        byte[] kemD;
        byte[] kemZ;
        MlKem768.KeyPair kemKeys;
    }

    // results kept for the tests
    CallCreateBean create;
    CallEraBean era1;
    String callId;
    String eraHash0;
    String eraHash1;
    CallAnnounceBean annO, annB, annC, annO1, annB1;
    HybridChannel.OpenResult chOB, chOC, chCB;
    HybridChannel.AcceptResult accOB, accOC, accCB;
    Commits.Built cInitial, cLeave, cEra;
    Commits.Accepted bAcceptLeave, bAcceptEra;
    byte[] es0, es1, es2, es3, es4;
    byte[] convSeed;
    CallGoodbyeBean goodbyeC;
    CallDeclineBean declineD;
    CallRingBean ring;
    CallGrantBean grantB;
    CallManifestBean manifest;
    CallLookupBean lookup;
    CallMuteBean mute, unmute;
    final Map<String, CallSignedObject> byType = new LinkedHashMap<>();

    public CallVectorScenario() throws Exception {
        for (String n : List.of("owner", "bob", "carol", "dave", "service")) {
            ids.put(n, CallTestKeys.identity(n));
        }
        int idx = 0;
        for (String n : List.of("owner", "bob", "carol")) {
            Leg l = new Leg();
            l.name = n;
            l.identity = CallSignatures.identityOf(ids.get(n));
            l.legId = Arrays.copyOf(CallTestKeys.seed("leg/" + n), 8);
            l.legIndex = idx++;
            l.xPriv = switch (n) {
                case "owner" -> CallBytes.unhex(RFC7748_ALICE_PRIV);
                case "bob" -> CallBytes.unhex(RFC7748_BOB_PRIV);
                default -> CallTestKeys.seed("x25519/" + n);
            };
            l.xPub = CallCrypto.x25519Public(l.xPriv);
            l.kemD = CallTestKeys.seed("mlkem/d/" + n);
            l.kemZ = CallTestKeys.seed("mlkem/z/" + n);
            l.kemKeys = kem.keyGen(l.kemD, l.kemZ);
            legs.put(n, l);
        }
        build();
    }

    static final String RFC7748_ALICE_PRIV = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a";
    static final String RFC7748_ALICE_PUB = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a";
    static final String RFC7748_BOB_PRIV = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb";
    static final String RFC7748_BOB_PUB = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f";
    static final String RFC7748_K = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742";

    String id(String n) {
        return CallSignatures.identityOf(ids.get(n));
    }

    String nonce(long t, String aud) {
        return nonces.issue(t, aud);
    }

    CallAnnounceBean announce(Leg l, String eraHash, long ts, String cap) {
        CallAnnounceBean a = new CallAnnounceBean();
        a.setAud(AUD_SVC);
        a.setN(nonce(ts, AUD_SVC));
        a.setTs(ts);
        a.setCall(callId);
        a.setEra(eraHash);
        a.setLeg(CallBytes.hex(l.legId));
        a.setRole(CallConstants.ROLE_SPEAKER);
        a.setX25519(CallBytes.hex(l.xPub));
        a.setMlkem(CallBytes.hex(l.kemKeys.ek()));
        a.setSuite(CallConstants.SUITE_HYBRID_1);
        a.setCap(cap);
        CallSignatures.sign(a, ids.get(l.name));
        return a;
    }

    private void build() throws Exception {
        convSeed = CallTestKeys.seed("conv_seed");

        // ---- creation record (era 0)
        create = new CallCreateBean();
        create.setAud(AUD_SVC);
        create.setN(nonce(T0, AUD_SVC));
        create.setTs(T0);
        create.setExp(T0 + CallConstants.CREATE_MAX_LIFETIME_MS);
        create.setNet(NET);
        create.setSvc(SVC);
        create.setRand(CallBytes.hex(CallTestKeys.seed("rand")));
        create.setMode(CallConstants.MODE_STANDARD);
        List<String> inv = new ArrayList<>(List.of(id("owner"), id("bob"), id("carol")));
        inv.sort(String::compareTo);
        create.setInv(inv);
        create.setMods(List.of(id("bob")));
        create.setCseed(CallBytes.hex(ConvSeed.commitment(convSeed)));
        create.setParams(CallParamsBean.initialValues());
        // sign through the wallet-keystore path once (iwk, index 0)
        CallSignatures.sign(create, new CallTestKeys.FixedKeystore("owner", ids.get("owner")), 0);
        callId = CallHashes.callIdHex(create);
        eraHash0 = CallHashes.eraHashHex(create);

        Leg o = legs.get("owner"), b = legs.get("bob"), c = legs.get("carol");

        // ---- announcements, era 0
        annO = announce(o, eraHash0, T0 + 1_000, CallConstants.CAP_BASIC_HQ);
        annB = announce(b, eraHash0, T0 + 5_000, CallConstants.CAP_BASIC_HQ);
        annC = announce(c, eraHash0, T0 + 10_000, CallConstants.CAP_BASIC);
        String hO = CallHashes.annHashHex(annO), hB = CallHashes.annHashHex(annB), hC = CallHashes.annHashHex(annC);

        // ---- epoch 0: initial commit, roster = owner leg
        cInitial = Commits.build(AUD_SVC, T0 + 2_000, callId, eraHash0, 0, CallConstants.KIND_INITIAL,
                List.of(hO), Map.of(), CallTestKeys.seed("commit_secret/0"), null, ids.get("owner"));
        es0 = cInitial.epochSecret();

        // ---- epoch 1: B joins (step); O opens O→B with the handover + conv_seed
        byte[] rh1 = EpochSchedule.rosterHashHex(List.of(hO, hB), eraHash0);
        es1 = EpochSchedule.step(es0, CallBytes.unhex(hB), rh1);
        CallChannelBodyBean bodyOB = Commits.epochHandover(1, eraHash0, List.of(hO, hB), es1, 0);
        bodyOB.setConvSeed(CallBytes.hex(convSeed));
        chOB = HybridChannel.openChannel(callId, annO, o.xPriv, annB, kem, CallTestKeys.seed("encaps/o-b"), null,
                bodyOB, ids.get("owner"), T0 + 6_000);
        accOB = HybridChannel.acceptChannel(chOB.channel(), rosterOf(annO, annB), annB, b.xPriv, b.kemKeys.dk(), kem);

        // ---- epoch 2: C joins (step); O→C with handover + conv_seed; C→B with conv_seed only
        byte[] rh2 = EpochSchedule.rosterHashHex(List.of(hO, hB, hC), eraHash0);
        es2 = EpochSchedule.step(es1, CallBytes.unhex(hC), rh2);
        CallChannelBodyBean bodyOC = Commits.epochHandover(2, eraHash0, List.of(hO, hB, hC), es2, 0);
        bodyOC.setConvSeed(CallBytes.hex(convSeed));
        chOC = HybridChannel.openChannel(callId, annO, o.xPriv, annC, kem, CallTestKeys.seed("encaps/o-c"), null,
                bodyOC, ids.get("owner"), T0 + 11_000);
        accOC = HybridChannel.acceptChannel(chOC.channel(), rosterOf(annO, annB, annC), annC, c.xPriv, c.kemKeys.dk(), kem);
        CallChannelBodyBean bodyCB = new CallChannelBodyBean();
        bodyCB.setConvSeed(CallBytes.hex(convSeed));
        chCB = HybridChannel.openChannel(callId, annC, c.xPriv, annB, kem, CallTestKeys.seed("encaps/c-b"), null,
                bodyCB, ids.get("carol"), T0 + 12_000);
        accCB = HybridChannel.acceptChannel(chCB.channel(), rosterOf(annO, annB, annC), annB, b.xPriv, b.kemKeys.dk(), kem);

        // ---- C says goodbye
        goodbyeC = new CallGoodbyeBean();
        goodbyeC.setAud(AUD_SVC);
        goodbyeC.setTs(T0 + 30_000);
        goodbyeC.setCall(callId);
        goodbyeC.setLeg(CallBytes.hex(c.legId));
        goodbyeC.setEpoch(2L);
        CallSignatures.sign(goodbyeC, ids.get("carol"));

        // ---- epoch 3: leave commit, roster {O, B}, box to B at k = 0
        ChannelState oToB = chOB.state();
        ChannelState bFromO = accOB.state();
        Map<String, ChannelState> rec = new HashMap<>();
        rec.put(CallBytes.hex(b.legId), oToB);
        cLeave = Commits.build(AUD_SVC, T0 + 70_000, callId, eraHash0, 3, CallConstants.KIND_LEAVE,
                List.of(hO, hB), rec, CallTestKeys.seed("commit_secret/3"), es2, ids.get("owner"));
        es3 = cLeave.epochSecret();
        bAcceptLeave = Commits.accept(cLeave.commit(), AUD_SVC, Set.of(id("owner")), eraHash0, 2, es2,
                Set.of(hO, hB, hC), hB, CallBytes.hex(b.legId), bFromO);
        oToB.advance();
        bFromO.advance();

        // ---- era 1: add D
        era1 = new CallEraBean();
        era1.setAud(AUD_SVC);
        era1.setTs(T0 + 120_000);
        era1.setCall(callId);
        era1.setEra(1);
        era1.setPrev(eraHash0);
        era1.setMode(CallConstants.MODE_STANDARD);
        List<String> inv1 = new ArrayList<>(List.of(id("owner"), id("bob"), id("carol"), id("dave")));
        inv1.sort(String::compareTo);
        era1.setInv(inv1);
        era1.setMods(List.of(id("bob")));
        era1.setReason("add");
        CallSignatures.sign(era1, ids.get("owner"));
        eraHash1 = CallHashes.eraHashHex(era1);

        // re-announcements bound to era 1 (same leg, same keys; new era and n)
        annO1 = announce(o, eraHash1, T0 + 120_500, CallConstants.CAP_BASIC_HQ);
        annB1 = announce(b, eraHash1, T0 + 120_600, CallConstants.CAP_BASIC_HQ);
        String hO1 = CallHashes.annHashHex(annO1), hB1 = CallHashes.annHashHex(annB1);

        // ---- epoch 4: era commit, roster {O', B'}, box to B at k = 1
        cEra = Commits.build(AUD_SVC, T0 + 121_000, callId, eraHash1, 4, CallConstants.KIND_ERA,
                List.of(hO1, hB1), rec, CallTestKeys.seed("commit_secret/4"), es3, ids.get("owner"));
        es4 = cEra.epochSecret();
        bAcceptEra = Commits.accept(cEra.commit(), AUD_SVC, Set.of(id("owner")), eraHash1, 3, es3,
                Set.of(hO1, hB1), hB1, CallBytes.hex(b.legId), bFromO);
        oToB.advance();
        bFromO.advance();

        // ---- the other types
        declineD = new CallDeclineBean();
        declineD.setAud(AUD_SVC);
        declineD.setN(nonce(T0 + 125_000, AUD_SVC));
        declineD.setTs(T0 + 125_000);
        declineD.setCall(callId);
        declineD.setReason("busy");
        CallSignatures.sign(declineD, ids.get("dave"));

        ring = new CallRingBean();
        ring.setAud(AUD_RSCHAT);
        ring.setN(RSCHAT_NONCE);
        ring.setTs(T0 + 121_500);
        ring.setCall(callId);
        ring.setSvc(SVC);
        ring.setTo(List.of(id("dave")));
        ring.setMode(CallConstants.MODE_STANDARD);
        CallSignatures.sign(ring, ids.get("owner"));

        grantB = GrantBuilder.build(ids.get("service"), T0 + 5_100, callId, CallBytes.hex(b.legId), b.legIndex,
                "wss://relay-lon1.example.invalid", "test-token-not-a-livekit-jwt", List.of("basic", "hq"), false,
                CallParamsBean.initialValues());

        manifest = new CallManifestBean();
        manifest.setAud(CallConstants.AUD_ANY);
        manifest.setTs(T0 - 60_000);
        manifest.setVer(new CallVersionRangeBean("1.0", "1.0"));
        manifest.setSuites(List.of(CallConstants.SUITE_HYBRID_1));
        manifest.setSframe(List.of(CallConstants.SFRAME_SUITE_NAME));
        manifest.setParams(CallParamsBean.initialValues());
        manifest.setLimits(CallLimitsBean.initialValues());
        manifest.setRegions(List.of("lon1"));
        CallSignatures.sign(manifest, ids.get("service"));

        lookup = new CallLookupBean();
        lookup.setAud(AUD_RSCHAT);
        lookup.setN(RSCHAT_NONCE_2);
        lookup.setTs(T0 + 119_000);
        lookup.setId(id("dave"));
        CallSignatures.sign(lookup, ids.get("service"));

        mute = new CallMuteBean();
        mute.setAud(AUD_SVC);
        mute.setN(nonce(T0 + 20_000, AUD_SVC));
        mute.setTs(T0 + 20_000);
        mute.setCall(callId);
        mute.setLeg(CallBytes.hex(c.legId));
        CallSignatures.sign(mute, ids.get("bob"));

        unmute = CallMuteBean.unmute();
        unmute.setAud(AUD_SVC);
        unmute.setN(nonce(T0 + 25_000, AUD_SVC));
        unmute.setTs(T0 + 25_000);
        unmute.setCall(callId);
        unmute.setLeg(CallBytes.hex(c.legId));
        CallSignatures.sign(unmute, ids.get("bob"));

        byType.put("create", create);
        byType.put("era", era1);
        byType.put("announce", annB);
        byType.put("channel", chOB.channel());
        byType.put("commit", cLeave.commit());
        byType.put("goodbye", goodbyeC);
        byType.put("decline", declineD);
        byType.put("ring", ring);
        byType.put("grant", grantB);
        byType.put("manifest", manifest);
        byType.put("lookup", lookup);
        byType.put("mute", mute);
        byType.put("unmute", unmute);
    }

    /** ACVP ML-KEM-768 encapsulationKeyCheck tcId 137: an ek with a coefficient &ge; q (from the KAT subset). */
    static final String INVALID_EK_HEX;

    static {
        try (java.io.InputStream in = CallVectorScenario.class.getResourceAsStream("/call/mlkem768_acvp_subset.json")) {
            com.fasterxml.jackson.databind.JsonNode k = new ObjectMapper().readTree(in);
            String found = null;
            for (com.fasterxml.jackson.databind.JsonNode t : k.get("encapsulationKeyCheck")) {
                if (t.get("tcId").asInt() == 137) {
                    found = t.get("ek").asText().toLowerCase();
                }
            }
            INVALID_EK_HEX = java.util.Objects.requireNonNull(found);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** rschat nonces are rschat's own format; fixed opaque values here. */
    static final String RSCHAT_NONCE = "00000000000000000000000000000000000000000000000000000000000000aa";
    static final String RSCHAT_NONCE_2 = "00000000000000000000000000000000000000000000000000000000000000bb";

    static Map<String, CallAnnounceBean> rosterOf(CallAnnounceBean... anns) {
        Map<String, CallAnnounceBean> r = new HashMap<>();
        for (CallAnnounceBean a : anns) {
            r.put(CallHashes.annHashHex(a), a);
        }
        return r;
    }

    /** The audience a verifier of this object expects. */
    static String audOf(CallSignedObject o) {
        return o.getAud();
    }

    /** Wire form used in the vectors: the JCS canonical form of the WHOLE object, sg included. */
    static String wire(Object o) {
        return CallJson.canonical(o);
    }

    // =================================================================== JSON

    public ObjectNode toJson() throws Exception {
        ObjectNode root = m.createObjectNode();
        root.put("spec", "tkm-call/v1 (rschat-docs/security/E2EE_CALLS_PROTOCOL_SPEC_v1.md, draft 0.1)");
        root.put("version", "1.0.0-draft");
        root.put("generator", "Messages io.takamaka.messages.call.CallVectorGenerator (feat/C182-calls-v1)");
        root.put("mlkem", "ML-KEM-768 FIPS 203 (KeyGen_internal(d,z), Encaps_internal(ek,m); BC 1.86 shaded)");
        root.put("about", "Java reference vectors for the call signalling/key plane. All binary values lowercase hex. "
                + "'wire' fields are the JCS (RFC 8785) form of the whole object INCLUDING sg. "
                + "Resolutions of spec ambiguities are listed in rschat-docs/analysis/C182_JAVA_REFERENCE_STATUS_2026-10-07.md.");
        root.set("fixtures", fixtures());
        root.set("group1_signed_objects", group1());
        root.set("group2_hashes", group2());
        root.set("group3_hybrid_combiner", group3());
        root.set("group4_chain_ratchet", group4());
        root.set("group5_epoch_schedule", group5());
        root.set("group6_sframe", group6());
        root.set("group7_conv_nonce_grant", group7());
        root.set("group8_negative", group8());
        return root;
    }

    ObjectNode fixtures() {
        ObjectNode f = m.createObjectNode();
        f.put("seed_rule", "seed(name) = SHA3-256(ASCII(\"tkm-call/v1/vectors/\" + name)); Ed25519 identity keys are RFC 8032 keys from seed(\"identity/<name>\")");
        f.put("t0", T0);
        f.put("net", NET);
        f.put("svc", SVC);
        f.put("k_nonce", CallBytes.hex(CallTestKeys.seed("k_nonce")));
        ObjectNode idn = f.putObject("identities");
        for (String n : ids.keySet()) {
            ObjectNode o = idn.putObject(n);
            o.put("ed25519_seed", CallBytes.hex(CallTestKeys.seed("identity/" + n)));
            o.put("identity", id(n));
        }
        ObjectNode ln = f.putObject("legs");
        for (Leg l : legs.values()) {
            ObjectNode o = ln.putObject(l.name);
            o.put("leg_id", CallBytes.hex(l.legId));
            o.put("leg_id_rule", "first 8 bytes of seed(\"leg/" + l.name + "\")");
            o.put("leg_index", l.legIndex);
            o.put("x25519_priv", CallBytes.hex(l.xPriv));
            o.put("x25519_pub", CallBytes.hex(l.xPub));
            o.put("mlkem_d", CallBytes.hex(l.kemD));
            o.put("mlkem_z", CallBytes.hex(l.kemZ));
        }
        f.put("conv_seed", CallBytes.hex(convSeed));
        return f;
    }

    String signerName(CallSignedObject o) {
        for (Map.Entry<String, AsymmetricCipherKeyPair> e : ids.entrySet()) {
            if (CallSignatures.identityOf(e.getValue()).equals(o.getF())) {
                return e.getKey();
            }
        }
        throw new IllegalStateException("unknown signer");
    }

    ArrayNode group1() {
        ArrayNode a = m.createArrayNode();
        for (Map.Entry<String, CallSignedObject> e : byType.entrySet()) {
            CallSignedObject o = e.getValue();
            ObjectNode n = a.addObject();
            n.put("t", o.getT());
            n.put("signer", signerName(o));
            n.put("f", o.getF());
            n.put("expected_aud", o.getAud());
            n.put("canonical", CallSignatures.canonical(o));
            n.put("signing_octets", CallBytes.hex(CallSignatures.signingOctets(o)));
            n.put("sg", o.getSg());
            n.put("wire", wire(o));
        }
        return a;
    }

    ObjectNode group2() {
        ObjectNode g = m.createObjectNode();
        g.put("rule", "call_id = H(\"tkm-call/v1/id\" || canonical(create)); era_hash = H(\"tkm-call/v1/era\" || canonical(record)); ann_hash = H(\"tkm-call/v1/ann\" || canonical(announce)); H = SHA3-256, canonical without sg, no separator");
        g.put("create_wire", wire(create));
        g.put("call_id", callId);
        g.put("era_hash_0", eraHash0);
        g.put("era1_wire", wire(era1));
        g.put("era_hash_1", eraHash1);
        ArrayNode anns = g.putArray("announcements");
        for (Object[] p : new Object[][]{{"owner/era0", annO}, {"bob/era0", annB}, {"carol/era0", annC},
        {"owner/era1", annO1}, {"bob/era1", annB1}}) {
            CallAnnounceBean an = (CallAnnounceBean) p[1];
            ObjectNode n = anns.addObject();
            n.put("name", (String) p[0]);
            n.put("wire", wire(an));
            n.put("ann_hash", CallHashes.annHashHex(an));
        }
        return g;
    }

    ObjectNode group3() {
        ObjectNode g = m.createObjectNode();
        g.put("mlkem", "ML-KEM-768 FIPS 203: ek/dk = KeyGen_internal(mlkem_d, mlkem_z), (ss_k, ct) = Encaps_internal(ek, encaps_m)");
        ObjectNode x = g.putObject("x25519_rfc7748");
        x.put("alice_priv", RFC7748_ALICE_PRIV);
        x.put("alice_pub", RFC7748_ALICE_PUB);
        x.put("bob_priv", RFC7748_BOB_PRIV);
        x.put("bob_pub", RFC7748_BOB_PUB);
        x.put("shared", RFC7748_K);
        ArrayNode chans = g.putArray("channels");
        chans.add(channelNode("owner>bob", "owner", "bob", chOB, annO, annB, "encaps/o-b"));
        chans.add(channelNode("owner>carol", "owner", "carol", chOC, annO, annC, "encaps/o-c"));
        chans.add(channelNode("carol>bob", "carol", "bob", chCB, annC, annB, "encaps/c-b"));
        return g;
    }

    ObjectNode channelNode(String name, String a, String b, HybridChannel.OpenResult r, CallAnnounceBean annA,
            CallAnnounceBean annB, String mSeed) {
        ObjectNode n = m.createObjectNode();
        n.put("name", name);
        n.put("A", a);
        n.put("B", b);
        n.put("call_id", callId);
        n.put("ann_hash_A", CallHashes.annHashHex(annA));
        n.put("ann_hash_B", CallHashes.annHashHex(annB));
        n.put("x25519_A", annA.getX25519());
        n.put("x25519_B", annB.getX25519());
        n.put("mlkem_ek_B", annB.getMlkem());
        n.put("mlkem_dk_B", CallBytes.hex(legs.get(b).kemKeys.dk()));
        n.put("encaps_m", CallBytes.hex(CallTestKeys.seed(mSeed)));
        HybridChannel.Combined c = r.combined();
        n.put("ss_x", CallBytes.hex(c.ssX()));
        n.put("ss_k", CallBytes.hex(c.ssK()));
        n.put("ct", CallBytes.hex(c.ct()));
        n.put("salt", CallBytes.hex(c.salt()));
        n.put("ikm", CallBytes.hex(c.ikm()));
        n.put("prk", CallBytes.hex(c.prk()));
        n.put("info_A>B", CallBytes.hex(c.infoAB()));
        n.put("info_B>A", CallBytes.hex(c.infoBA()));
        n.put("chain0_A>B", CallBytes.hex(c.chainAB()));
        n.put("chain0_B>A", CallBytes.hex(c.chainBA()));
        n.put("body", new String(r.body(), java.nio.charset.StandardCharsets.US_ASCII));
        n.put("box_aad", new String(r.aad(), java.nio.charset.StandardCharsets.US_ASCII));
        n.put("channel_wire", wire(r.channel()));
        return n;
    }

    ObjectNode group4() {
        ObjectNode g = m.createObjectNode();
        g.put("channel", "owner>bob, direction A>B");
        byte[] chain = chOB.combined().chainAB();
        ArrayNode ks = g.putArray("chain");
        for (int k = 0; k <= 3; k++) {
            ObjectNode n = ks.addObject();
            n.put("k", k);
            n.put("chain_k", CallBytes.hex(chain));
            n.put("k_msg_k", CallBytes.hex(HybridChannel.msgKey(chain)));
            chain = HybridChannel.nextChain(chain);
        }
        ObjectNode box = g.putObject("box_channel_open");
        box.put("k", 0);
        box.put("seq", 0);
        box.put("nonce", CallBytes.hex(HybridChannel.nonce(0, 0)));
        box.put("aad", new String(chOB.aad(), java.nio.charset.StandardCharsets.US_ASCII));
        box.put("plaintext", new String(chOB.body(), java.nio.charset.StandardCharsets.US_ASCII));
        box.put("box", chOB.channel().getBox());
        ObjectNode box2 = g.putObject("box_era_commit");
        box2.put("k", 1);
        box2.put("seq", 2);
        box2.put("nonce", CallBytes.hex(HybridChannel.nonce(1, 2)));
        box2.put("aad", cEra.canonicalHeader());
        box2.put("plaintext", CallBytes.hex(cEra.commitSecret()));
        box2.put("box", cEra.commit().getBoxes().get(0).getBox());
        g.put("note", "seq counts per direction over the channel's life and is not reset when k advances: "
                + "owner>bob seq 0 = channel open, 1 = leave commit (k=0), 2 = era commit (k=1)");
        return g;
    }

    ObjectNode epochNode(long e, String kind, byte[] secret, List<String> rosterHex, String eraHash,
            List<String> legNames, Commits.Built built) {
        ObjectNode n = m.createObjectNode();
        n.put("epoch", e);
        n.put("kind", kind);
        n.put("era_hash", eraHash);
        ArrayNode r = n.putArray("roster");
        EpochSchedule.sortedHex(rosterHex).forEach(r::add);
        n.put("roster_hash", CallBytes.hex(EpochSchedule.rosterHashHex(rosterHex, eraHash)));
        n.put("epoch_secret", CallBytes.hex(secret));
        byte[] bc = EpochSchedule.broadcast(secret, CallBytes.unhex(eraHash));
        n.put("broadcast", CallBytes.hex(bc));
        ObjectNode sb = n.putObject("per_leg");
        for (String ln : legNames) {
            byte[] legId = legs.get(ln).legId;
            ObjectNode p = sb.putObject(CallBytes.hex(legId));
            p.put("leg", ln);
            p.put("sender_base", CallBytes.hex(EpochSchedule.senderBaseFromBroadcast(bc, legId)));
            p.put("text_key", CallBytes.hex(EpochSchedule.textKeyFromBroadcast(bc, legId)));
        }
        n.put("code_hkdf", CallBytes.hex(EpochSchedule.codeBytes(secret)));
        n.put("code", EpochSchedule.code(secret));
        if (built != null) {
            n.put("commit_secret", CallBytes.hex(built.commitSecret()));
            if (built.saltE() != null) {
                n.put("salt_e", CallBytes.hex(built.saltE()));
            }
            n.put("commit_header", built.canonicalHeader());
            n.put("confirm", CallBytes.hex(built.confirm()));
            n.put("commit_wire", wire(built.commit()));
        }
        return n;
    }

    ArrayNode group5() {
        String hO = CallHashes.annHashHex(annO), hB = CallHashes.annHashHex(annB), hC = CallHashes.annHashHex(annC);
        String hO1 = CallHashes.annHashHex(annO1), hB1 = CallHashes.annHashHex(annB1);
        ArrayNode a = m.createArrayNode();
        a.add(epochNode(0, "initial", es0, List.of(hO), eraHash0, List.of("owner"), cInitial));
        ObjectNode s1 = epochNode(1, "step", es1, List.of(hO, hB), eraHash0, List.of("owner", "bob"), null);
        s1.put("joiner_ann_hash", hB);
        a.add(s1);
        ObjectNode s2 = epochNode(2, "step", es2, List.of(hO, hB, hC), eraHash0, List.of("owner", "bob", "carol"), null);
        s2.put("joiner_ann_hash", hC);
        a.add(s2);
        a.add(epochNode(3, "leave", es3, List.of(hO, hB), eraHash0, List.of("owner", "bob"), cLeave));
        a.add(epochNode(4, "era", es4, List.of(hO1, hB1), eraHash1, List.of("owner", "bob"), cEra));
        return a;
    }

    ObjectNode group6() throws Exception {
        ObjectNode g = m.createObjectNode();
        // RFC 9605 Appendix C.3, cipher suite 0x0005
        ObjectNode rfc = g.putObject("rfc9605_c3_suite5");
        SframeKeys rk = SframeKeys.derive(0x123L, CallBytes.unhex("000102030405060708090a0b0c0d0e0f"));
        byte[] meta = CallBytes.unhex("4945544620534672616d65205747");
        byte[] pt = CallBytes.unhex("64726166742d696574662d736672616d652d656e63");
        rfc.put("cipher_suite", 5);
        rfc.put("kid", "0000000000000123");
        rfc.put("ctr", "0000000000004567");
        rfc.put("base_key", "000102030405060708090a0b0c0d0e0f");
        rfc.put("sframe_key_label", CallBytes.hex(rk.keyLabel()));
        rfc.put("sframe_salt_label", CallBytes.hex(rk.saltLabel()));
        rfc.put("sframe_secret", CallBytes.hex(rk.secret()));
        rfc.put("sframe_key", CallBytes.hex(rk.key()));
        rfc.put("sframe_salt", CallBytes.hex(rk.salt()));
        rfc.put("metadata", CallBytes.hex(meta));
        rfc.put("nonce", CallBytes.hex(rk.nonce(0x4567L)));
        rfc.put("pt", CallBytes.hex(pt));
        rfc.put("ct", CallBytes.hex(SframeCipher.encrypt(rk, 0x4567L, meta, pt)));

        // our units: sender_base of bob's leg at epoch 4
        Leg b = legs.get("bob");
        byte[] base = EpochSchedule.senderBase(es4, CallBytes.unhex(eraHash1), b.legId);
        long kid = SframeHeader.kid(b.legIndex, 4);
        SframeKeys k = SframeKeys.derive(kid, base);
        ObjectNode ours = g.putObject("units");
        ours.put("rule", "KID = leg_index*65536 + (epoch mod 65536), RFC 9605 compact header; base_key = sender_base_e; "
                + "unit = clear prefix || header || ct || tag; AAD = header || clear prefix");
        ours.put("leg", "bob");
        ours.put("leg_index", b.legIndex);
        ours.put("epoch", 4);
        ours.put("base_key", CallBytes.hex(base));
        ours.put("kid", Long.toHexString(kid));
        ours.put("sframe_secret", CallBytes.hex(k.secret()));
        ours.put("sframe_key", CallBytes.hex(k.key()));
        ours.put("sframe_salt", CallBytes.hex(k.salt()));
        ArrayNode units = ours.putArray("cases");
        byte[] opus = CallBytes.concat(new byte[]{(byte) 0x78}, Arrays.copyOf(CallTestKeys.seed("frame/opus"), 32),
                Arrays.copyOf(CallTestKeys.seed("frame/opus2"), 8));
        byte[] vp8 = CallBytes.concat(new byte[]{(byte) 0x10}, Arrays.copyOf(CallTestKeys.seed("frame/vp8"), 32),
                Arrays.copyOf(CallTestKeys.seed("frame/vp82"), 20));
        units.add(unitNode("opus", k, 0, opus, SframeCipher.OPUS_PREFIX));
        units.add(unitNode("opus", k, 1, opus, SframeCipher.OPUS_PREFIX));
        units.add(unitNode("vp8", k, 2, vp8, SframeCipher.VP8_PREFIX));
        units.add(unitNode("vp8", k, 0x1234, vp8, SframeCipher.VP8_PREFIX));
        ObjectNode h264 = ours.putObject("h264");
        h264.put("status", "TODO");
        h264.put("note", "per-NAL units behind the 1-byte NAL header with emulation-prevention escaping are not "
                + "specified precisely enough yet; the SPS/PPS case falls to the spec's fallback (in clear) because "
                + "libwebrtc's receive depacketizer parses them before any transformer runs");
        return g;
    }

    ObjectNode unitNode(String codec, SframeKeys k, long ctr, byte[] frame, int prefixLen) {
        ObjectNode n = m.createObjectNode();
        n.put("codec", codec);
        n.put("prefix_len", prefixLen);
        n.put("ctr", ctr);
        n.put("frame", CallBytes.hex(frame));
        byte[] header = SframeHeader.encode(k.kid(), ctr);
        n.put("header", CallBytes.hex(header));
        n.put("nonce", CallBytes.hex(k.nonce(ctr)));
        n.put("aad", CallBytes.hex(CallBytes.concat(header, Arrays.copyOf(frame, prefixLen))));
        n.put("unit", CallBytes.hex(SframeCipher.seal(k, ctr, frame, prefixLen)));
        return n;
    }

    ObjectNode group7() {
        ObjectNode g = m.createObjectNode();
        ObjectNode c = g.putObject("conv_seed");
        c.put("conv_seed", CallBytes.hex(convSeed));
        c.put("cseed", CallBytes.hex(ConvSeed.commitment(convSeed)));
        c.put("title_hkdf", CallBytes.hex(ConvSeed.titleBytes(convSeed)));
        c.put("title", ConvSeed.title(convSeed));
        c.put("salt", CallBytes.hex(ConvSeed.salt(convSeed)));
        c.put("key", CallBytes.hex(ConvSeed.key(convSeed)));
        ArrayNode ns = g.putArray("nonces");
        for (Object[] p : new Object[][]{{T0, AUD_SVC}, {T0 + 5_000, AUD_SVC}, {T0 + 1, "svc:other"}}) {
            ObjectNode n = ns.addObject();
            n.put("k_nonce", CallBytes.hex(CallTestKeys.seed("k_nonce")));
            n.put("t_ms", (Long) p[0]);
            n.put("aud", (String) p[1]);
            n.put("nonce", nonces.issue((Long) p[0], (String) p[1]));
        }
        ObjectNode gr = g.putObject("grant");
        gr.put("wire", wire(grantB));
        gr.put("service_identity", id("service"));
        gr.put("canonical", CallSignatures.canonical(grantB));
        gr.put("sg", grantB.getSg());
        return g;
    }

    // =================================================================== negatives

    /** A negative case: the wire JSON (or bytes) that MUST fail, and why. */
    ObjectNode neg(String id, String what, String wire, String expectedT, String expectedAud) {
        ObjectNode n = m.createObjectNode();
        n.put("id", id);
        n.put("what", what);
        n.put("wire", wire);
        n.put("expected_t", expectedT);
        n.put("expected_aud", expectedAud);
        n.put("expect", "invalid");
        return n;
    }

    ArrayNode group8() throws Exception {
        ArrayNode a = m.createArrayNode();

        // 8.1 chat signature presented as a call object: sg made by the CHAT signer over canonical(obj)
        CallAnnounceBean chatSigned = CallJson.parse(wire(annB), CallAnnounceBean.class);
        String chatSigB64 = SimpleRequestHelper.signChatMessage(CallSignatures.canonical(chatSigned),
                new CallTestKeys.FixedKeystore("bob", ids.get("bob")), 0);
        chatSigned.setSg(CallBytes.hex(CallSignatures.urlUnBase64(chatSigB64)));
        ObjectNode n1 = neg("N1", "chat signature (Ed25519 over canonical JSON, no tkm-call prefix) presented as a call object",
                wire(chatSigned), "announce", AUD_SVC);
        n1.put("chat_signing_octets", CallBytes.hex(CallBytes.ascii(CallSignatures.canonical(chatSigned))));
        a.add(n1);

        // 8.2 call object presented to the chat verifier
        ObjectNode n2 = m.createObjectNode();
        n2.put("id", "N2");
        n2.put("what", "call signature verified as a chat signature: Ed25519.verify(f, sg, canonical(obj)) without the prefix");
        n2.put("f", annB.getF());
        n2.put("chat_message", CallSignatures.canonical(annB));
        n2.put("sg", annB.getSg());
        n2.put("wire", wire(annB));
        n2.put("expect", "invalid");
        a.add(n2);

        // 8.3 non-ASCII in a signed field, with a signature that IS valid under Java's low-8-bit conversion
        CallDeclineBean na = CallJson.parse(wire(declineD), CallDeclineBean.class);
        na.setReason("busyé");
        String msg = CallConstants.PREFIX + na.getT() + '\u0000' + CallJson.canonicalWithout(na, "sg");
        byte[] octets = Strings.toByteArray(msg); // the DR-027 conversion: low 8 bits per char
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, (Ed25519PrivateKeyParameters) ids.get("dave").getPrivate());
        s.update(octets, 0, octets.length);
        na.setSg(CallBytes.hex(s.generateSignature()));
        ObjectNode n3 = neg("N3", "non-ASCII char in a signed field; sg valid over the Java low-8-bit octets, still MUST be refused",
                CallJson.canonical(na), "decline", AUD_SVC);
        n3.put("java_low8_octets", CallBytes.hex(octets));
        a.add(n3);

        // 8.4 reordered payload: array order is signed (JCS sorts keys, never arrays)
        CallCreateBean ro = CallJson.parse(wire(create), CallCreateBean.class);
        List<String> rev = new ArrayList<>(ro.getInv());
        java.util.Collections.reverse(rev);
        ro.setInv(rev);
        a.add(neg("N4", "reordered payload: create.inv reversed, sg unchanged", CallJson.toWire(ro), "create", AUD_SVC));
        CallCommitBean rc = CallJson.parse(wire(cLeave.commit()), CallCommitBean.class);
        List<String> rr = new ArrayList<>(rc.getRoster());
        java.util.Collections.reverse(rr);
        rc.setRoster(rr);
        a.add(neg("N5", "reordered payload: commit.roster reversed, sg unchanged", CallJson.toWire(rc), "commit", AUD_SVC));

        // 8.5 type confusion: a mute's signature under t = unmute (same fields)
        CallMuteBean tc = CallJson.parse(wire(mute), CallMuteBean.class);
        tc.setT(CallConstants.T_UNMUTE);
        a.add(neg("N6", "type confusion: a mute object relabelled t=unmute keeps its sg", CallJson.canonical(tc), "unmute", AUD_SVC));

        // 8.6 wrong audience: the owner>bob channel presented to carol's leg
        a.add(neg("N7", "audience: the owner>bob channel open verified by carol's leg",
                wire(chOB.channel()), "channel", CallConstants.AUD_LEG + CallBytes.hex(legs.get("carol").legId)));

        // 8.7 expected-type mismatch: an announce presented where a goodbye is expected
        a.add(neg("N8", "context: a valid announce presented where a goodbye is expected", wire(annB), "goodbye", AUD_SVC));

        // 8.8 a validly signed announcement whose ML-KEM key fails the FIPS 203 modulus check (spec amendment 4.3)
        CallAnnounceBean badEk = CallJson.parse(wire(annC), CallAnnounceBean.class);
        badEk.setMlkem(INVALID_EK_HEX);
        badEk.setSg(null);
        CallSignatures.sign(badEk, ids.get("carol"));
        ObjectNode n9 = neg("N9", "announcement validly signed, mlkem key fails the FIPS 203 encapsulation-key (modulus) check: the announcement verifier MUST refuse it",
                wire(badEk), "announce", AUD_SVC);
        n9.put("check", "announcement");
        n9.put("ek_source", "ACVP ML-KEM-768 encapsulationKeyCheck tcId 137 (noisy linear system values too large)");
        a.add(n9);

        // positive control: key order in the wire text is irrelevant (JCS)
        ObjectNode p = m.createObjectNode();
        p.put("id", "P1");
        p.put("what", "positive control: the same announce with keys in reverse order on the wire still verifies");
        ObjectNode tree = (ObjectNode) m.readTree(wire(annB));
        ObjectNode reversed = m.createObjectNode();
        List<String> names = new ArrayList<>();
        tree.fieldNames().forEachRemaining(names::add);
        java.util.Collections.reverse(names);
        names.forEach(fn -> reversed.set(fn, tree.get(fn)));
        p.put("wire", m.writeValueAsString(reversed));
        p.put("expected_t", "announce");
        p.put("expected_aud", AUD_SVC);
        p.put("expect", "valid");
        a.add(p);
        return a;
    }

    /** Verifies a chat-style signature (no prefix), as the chat verifier's primitive does. */
    static boolean chatVerify(String f, String sgHex, String canonical) {
        return TkmCypherProviderBCED25519.verify(f, CallSignatures.urlBase64(CallBytes.unhex(sgHex)), canonical).isValid();
    }
}
