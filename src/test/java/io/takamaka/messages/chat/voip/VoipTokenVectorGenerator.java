/*
 * Copyright 2026 AiliA SA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.takamaka.messages.chat.voip;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.messages.chat.constant.ChatServerEndpoints;
import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.chat.core.ServerInfoResponseBean;
import io.takamaka.messages.chat.fcm.FcmTokenRegistrationRequestBean;
import io.takamaka.messages.chat.fcm.FcmTokenRegistrationSignedContentBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.SimpleRequestHelper;
import io.takamaka.wallet.TkmCypherProviderBCED25519;
import io.takamaka.wallet.beans.TkmCypherBean;
import io.takamaka.wallet.utils.KeyContexts;
import io.takamaka.wallet.utils.SeedGenerator;
import io.takamaka.wallet.utils.SeededRandom;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.util.encoders.UrlBase64;
import org.erdtman.jcs.JsonCanonicalizer;

/**
 * Generates {@code java_voip_token_vectors.json} (DR-074 §11): the Java reference output a Dart (or any other) port
 * of the VoIP-token request must reproduce and judge identically. Deterministic: a mnemonic-derived Ed25519 key (the
 * shell_user fixture identity of the other parity generators, index 4), a fixed nonce, a fixed audience; Ed25519
 * signatures are deterministic, so a port must reproduce every signature byte for byte.
 *
 * <p>Each case is a FULL signed envelope plus the verdict the Java reference gives it on a named route:
 * {@code OK}, or the {@code error_code} of the refusal. The verdict is computed by {@link #verdict}, which is the
 * server's order (§5.5 steps 3 and 5 to 8): the signature through {@code VoipTokenRules.verifySignature}, then
 * {@code VoipTokenRules.check}. {@link VoipTokenVectorFileTest} asserts the committed file equals {@link #render()}.</p>
 *
 * <p>Regenerate deliberately: run {@link #main} with the target path(s), e.g.
 * {@code src/test/resources/voip/java_voip_token_vectors.json} and the rsclient-flutter fixtures copy.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class VoipTokenVectorGenerator {

    private VoipTokenVectorGenerator() {
    }

    static final List<String> MNEMONIC = Arrays.asList(
            "sample", "half", "mammal", "radar", "hold", "fit", "era", "dilemma",
            "manage", "movie", "razor", "saddle", "point", "dial", "sadness", "north",
            "item", "naive", "gate", "hockey", "sample", "script", "embark", "purse", "myth");
    static final int KEY_INDEX = 4;
    static final String AUDIENCE = "rschat:rig1";
    static final String OTHER_AUDIENCE = "rschat:rig2";
    static final String TOPIC = "io.takamaka.tkmchat.test";
    static final String DID = "3f2a9c1e-7b4d-4e8a-9c2f-5d6e7f8a9b0c";
    static final String VT_64 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    static final String VT_200 = "a1b2c3d4e5f60718293a4b5c6d7e8f90".repeat(7).substring(0, 200);
    static final String ED25519 = KeyContexts.WalletCypher.Ed25519BC.name();
    static final String VOIP_TYPE = CHAT_MESSAGE_TYPES.VOIP_TOKEN_REGISTRATION.name();
    static final String CONTENT_KEY = "voip_token_signed_content";

    /** The decoder of a server route: unknown members do not fail the parse (Spring's Jackson decoder). */
    static final ObjectMapper LENIENT = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final ObjectMapper M = new ObjectMapper();

    private static AsymmetricCipherKeyPair keyPair;
    private static String signer;

    static NonceResponseBean nonce() {
        return new NonceResponseBean("11111111-2222-3333-4444-555555555555", 1_700_000_000_000L, 60_000L);
    }

    static synchronized void initKey() throws Exception {
        if (keyPair == null) {
            SeedGenerator.init();
            String seed = SeedGenerator.generateSeedPWH(MNEMONIC);
            SeededRandom rnd = new SeededRandom(seed, KeyContexts.WALLET_KEY_CHAIN, KEY_INDEX + 1);
            Ed25519KeyPairGenerator gen = new Ed25519KeyPairGenerator();
            gen.init(new Ed25519KeyGenerationParameters(rnd));
            keyPair = gen.generateKeyPair();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            UrlBase64.encode(((Ed25519PublicKeyParameters) keyPair.getPublic()).getEncoded(), baos);
            signer = baos.toString(StandardCharsets.US_ASCII);
        }
    }

    static String sign(String canonical) {
        TkmCypherBean s = TkmCypherProviderBCED25519.sign(keyPair, canonical);
        if (!s.isValid()) {
            throw new IllegalStateException("sign failed", s.getEx());
        }
        return s.getSignature();
    }

    /**
     * The Java reference verdict of one envelope on a VoIP route: {@code OK} or the refusal's {@code error_code}.
     * Parsed as a server route parses it (unknown members tolerated by the decoder), then the signature, then the
     * content check.
     */
    static String verdict(JsonNode envelope, String routeOp, String serverAudience) throws Exception {
        VoipTokenRequestBean req = LENIENT.treeToValue(envelope, VoipTokenRequestBean.class);
        VoipTokenRules.Refusal r = VoipTokenRules.verifySignature(req);
        if (r == null) {
            r = VoipTokenRules.check(req.getVoipTokenSignedContentBean(), routeOp, serverAudience);
        }
        return r == null ? "OK" : r.code();
    }

    /** The verdict of the FCM verifier case for an envelope sent to an FCM token route: {@code OK} or SIGNATURE_ERROR. */
    static String fcmVerdict(JsonNode envelope) throws Exception {
        FcmTokenRegistrationRequestBean req = LENIENT.treeToValue(envelope, FcmTokenRegistrationRequestBean.class);
        try {
            ChatCryptoUtils.verifySignedMessage(TkmTextUtils.getJacksonMapper().writeValueAsString(req));
            return req.getFcmTokenRegistrationSignedContentBean() == null ? VoipTokenRules.VALIDATION_ERROR : "OK";
        } catch (ChatMessageException ex) {
            return VoipTokenRules.SIGNATURE_ERROR;
        }
    }

    private static ObjectNode envelope(JsonNode content, String signature, String messageType, String contentKey) {
        ObjectNode e = M.createObjectNode();
        e.put("from", signer);
        e.put("signature", signature);
        e.put("message_type", messageType);
        e.put("signature_type", ED25519);
        e.set(contentKey, content);
        return e;
    }

    private static ObjectNode contentNode(String op, String aud, String vt, String env, String tp, String did) {
        return M.valueToTree(new VoipTokenSignedContentBean(nonce(), op, aud, vt, env, tp, did));
    }

    private static String canonical(JsonNode n) throws Exception {
        return new JsonCanonicalizer(M.writeValueAsString(n)).getEncodedString();
    }

    private static ObjectNode caseNode(ArrayNode out, String name, String desc, String route, String routeOp,
            String signedCanonical, ObjectNode envelope, String stage) throws Exception {
        ObjectNode c = out.addObject();
        c.put("name", name);
        c.put("desc", desc);
        c.put("route", route);
        c.put("signed_canonical_jcs", signedCanonical);
        c.put("signed_canonical_utf8_hex", HexFormat.of().formatHex(signedCanonical.getBytes(StandardCharsets.UTF_8)));
        c.put("signature_b64url", envelope.get("signature").asText());
        c.set("envelope_json", envelope);
        String v = verdict(envelope, routeOp, AUDIENCE);
        c.put("expected", v);
        if (!"OK".equals(v)) {
            c.put("refused_at", stage);
            c.put("nonce_consumed", false);
        }
        return c;
    }

    /** A content signed exactly as built: the ordinary client path. */
    private static ObjectNode signedCase(ArrayNode out, String name, String desc, String route, String routeOp,
            ObjectNode content, String stage) throws Exception {
        String canon = canonical(content);
        return caseNode(out, name, desc, route, routeOp, canon, envelope(content, sign(canon), VOIP_TYPE, CONTENT_KEY), stage);
    }

    public static String render() throws Exception {
        initKey();
        final String reg = ChatServerEndpoints.REGISTER_VOIP_TOKEN;
        final String unreg = ChatServerEndpoints.UNREGISTER_VOIP_TOKEN;
        final String R = VoipTokenRules.OP_REGISTER;
        final String U = VoipTokenRules.OP_UNREGISTER;
        final String SB = VoipTokenRules.ENV_SANDBOX;
        final String PR = VoipTokenRules.ENV_PRODUCTION;

        ObjectNode root = M.createObjectNode();
        root.put("version", "1.0.0");
        root.put("generated_by", "VoipTokenVectorGenerator.java");
        root.put("source", "Messages (Java reference)");
        root.put("decision", "DR-074 revision 3: registervoiptoken / unregistervoiptoken, serverinfo.audience (manifest 1.7)");

        ObjectNode k = root.putObject("voip_token_constants");
        k.put("endpoint_register", reg);
        k.put("endpoint_unregister", unreg);
        k.put("message_type", VOIP_TYPE);
        k.put("signature_type", ED25519);
        k.put("content_key", CONTENT_KEY);
        k.put("op_register", R);
        k.put("op_unregister", U);
        k.put("env_sandbox", SB);
        k.put("env_production", PR);
        k.put("vt_pattern", "^" + VoipTokenRules.VT.pattern() + "$ and an even length");
        k.put("tp_pattern", "^" + VoipTokenRules.TP.pattern() + "$");
        k.put("did_pattern", "^" + VoipTokenRules.DID.pattern() + "$");
        k.put("token_hash_domain", VoipTokenRules.TOKEN_HASH_DOMAIN);
        k.put("server_audience", AUDIENCE);
        k.put("note", "All seven content members are required and non-null. The server normalises nothing. "
                + "'expected' is the Java reference verdict on 'route' for a server whose audience is server_audience: "
                + "OK or the error_code. Order: signature, then missing member, unknown key, op, aud, vt, env, tp, did. "
                + "No refusal listed here consumes the nonce.");
        ArrayNode codes = k.putArray("error_codes");
        for (String c : new String[]{VoipTokenRules.RATE_LIMIT_EXCEEDED, VoipTokenRules.VALIDATION_ERROR,
            VoipTokenRules.SIGNATURE_ERROR, VoipTokenRules.NONCE_INVALID, VoipTokenRules.VOIP_FIELD_MISSING,
            VoipTokenRules.OP_MISMATCH, VoipTokenRules.AUD_MISMATCH, VoipTokenRules.VOIP_DISABLED,
            VoipTokenRules.VOIP_TOPIC_REFUSED, VoipTokenRules.VOIP_ENV_REFUSED, VoipTokenRules.VOIP_TOKEN_IDENTITY_CAP,
            VoipTokenRules.USER_NOT_REGISTERED, VoipTokenRules.VOIP_TOKEN_ERROR}) {
            codes.add(c);
        }

        ObjectNode ident = root.putObject("signer_identity");
        ident.put("mnemonic", String.join(" ", MNEMONIC));
        ident.put("key_index", KEY_INDEX);
        ident.put("signer_public_key", signer);
        root.set("nonce", M.valueToTree(nonce()));

        // ---- positive ----
        ArrayNode pos = root.putArray("positive_vectors");
        signedCase(pos, "register_sandbox", "register, a sandbox token of 64 hex characters", reg, R,
                contentNode(R, AUDIENCE, VT_64, SB, TOPIC, DID), null);
        signedCase(pos, "register_production", "register, a production token", reg, R,
                contentNode(R, AUDIENCE, VT_64, PR, TOPIC, DID), null);
        signedCase(pos, "unregister", "unregister of the same token", unreg, U,
                contentNode(U, AUDIENCE, VT_64, SB, TOPIC, DID), null);
        signedCase(pos, "register_token_200", "register, a token of 200 hex characters (the upper bound)", reg, R,
                contentNode(R, AUDIENCE, VT_200, PR, TOPIC, DID), null);
        signedCase(pos, "register_did_sha256_hex", "register, did = a 64-character hex id (the wallet's Android-id form)",
                reg, R, contentNode(R, AUDIENCE, VT_64, PR, "io.takamaka.wallet",
                        "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"), null);
        for (JsonNode c : pos) {
            JsonNode content = c.get("envelope_json").get(CONTENT_KEY);
            ((ObjectNode) c).put("token_hash", VoipTokenRules.tokenHash(content.get("env").asText(),
                    content.get("tp").asText(), content.get("vt").asText()));
        }

        // ---- negative ----
        ArrayNode neg = root.putArray("negative_vectors");
        final String check = "content_check";
        final String sig = "signature";
        signedCase(neg, "vt_uppercase_hex", "uppercase hex in vt: refused, never lowercased", reg, R,
                contentNode(R, AUDIENCE, VT_64.toUpperCase(), SB, TOPIC, DID), check);
        signedCase(neg, "vt_odd_length", "vt of 65 characters", reg, R,
                contentNode(R, AUDIENCE, VT_64 + "a", SB, TOPIC, DID), check);
        signedCase(neg, "vt_too_short", "vt of 62 characters", reg, R,
                contentNode(R, AUDIENCE, VT_64.substring(2), SB, TOPIC, DID), check);
        signedCase(neg, "op_not_the_routes", "a validly signed unregister sent to registervoiptoken", reg, R,
                contentNode(U, AUDIENCE, VT_64, SB, TOPIC, DID), check);
        signedCase(neg, "op_not_the_routes_reverse", "a validly signed register sent to unregistervoiptoken", unreg, U,
                contentNode(R, AUDIENCE, VT_64, SB, TOPIC, DID), check);
        signedCase(neg, "op_unknown", "op = delete", reg, R,
                contentNode("delete", AUDIENCE, VT_64, SB, TOPIC, DID), check);
        signedCase(neg, "env_unknown", "env = development", reg, R,
                contentNode(R, AUDIENCE, VT_64, "development", TOPIC, DID), check);
        signedCase(neg, "tp_forbidden_character", "a topic with a slash", reg, R,
                contentNode(R, AUDIENCE, VT_64, SB, "io.takamaka/tkmchat", DID), check);
        signedCase(neg, "did_too_short", "did of 7 characters", reg, R,
                contentNode(R, AUDIENCE, VT_64, SB, TOPIC, "abcdefg"), check);
        signedCase(neg, "aud_wrong", "aud of another network, validly signed: the nonce is NOT consumed", reg, R,
                contentNode(R, OTHER_AUDIENCE, VT_64, SB, TOPIC, DID), check);
        signedCase(neg, "did_null", "the client signed a canonical form containing \"did\":null: the signature VERIFIES "
                + "(the Java mapper keeps nulls), the refusal comes from the field check after it", reg, R,
                contentNode(R, AUDIENCE, VT_64, SB, TOPIC, null), check);

        // did absent: the client signed a canonical form WITHOUT the key; the server re-derives "did":null
        ObjectNode absent = contentNode(R, AUDIENCE, VT_64, SB, TOPIC, DID);
        absent.remove("did");
        String absentCanon = canonical(absent);
        caseNode(neg, "did_absent", "the client signed a canonical form without the did key: the server re-derives "
                + "\"did\":null from the parsed bean, so the signature fails", reg, R, absentCanon,
                envelope(absent, sign(absentCanon), VOIP_TYPE, CONTENT_KEY), sig);

        // an eighth key, signed
        ObjectNode extra = contentNode(R, AUDIENCE, VT_64, SB, TOPIC, DID);
        extra.put("platform", "ios");
        String extraCanon = canonical(extra);
        caseNode(neg, "extra_key_signed", "an eighth key inside the signed canonical form: the server's re-derived form "
                + "lacks it, so the signature fails", reg, R, extraCanon,
                envelope(extra, sign(extraCanon), VOIP_TYPE, CONTENT_KEY), sig);

        // an eighth key, added after signing
        ObjectNode good = contentNode(R, AUDIENCE, VT_64, SB, TOPIC, DID);
        String goodCanon = canonical(good);
        String goodSig = sign(goodCanon);
        ObjectNode unsignedExtra = good.deepCopy();
        unsignedExtra.put("platform", "ios");
        caseNode(neg, "extra_key_unsigned", "an eighth key added to the wire object but not signed: the signature "
                + "verifies over the seven, and the content check refuses the unknown key", reg, R, goodCanon,
                envelope(unsignedExtra, goodSig, VOIP_TYPE, CONTENT_KEY), check);

        // one byte of the content changed after signing
        ObjectNode tampered = good.deepCopy();
        tampered.put("vt", VT_64.substring(0, 63) + "0");
        caseNode(neg, "content_changed_after_signing", "the last character of vt changed after signing", reg, R, goodCanon,
                envelope(tampered, goodSig, VOIP_TYPE, CONTENT_KEY), sig);

        // the envelope type is outside the signature: a valid content under another message_type
        caseNode(neg, "voip_content_under_fcm_type", "a valid VoIP content with message_type = FCM_TOKEN_REGISTRATION sent "
                + "to registervoiptoken: refused (the route accepts its own type only)", reg, R, goodCanon,
                envelope(good, goodSig, CHAT_MESSAGE_TYPES.FCM_TOKEN_REGISTRATION.name(), CONTENT_KEY), sig);

        // a valid FCM content under the VoIP type: the type's own case reads voip_token_signed_content only
        FcmTokenRegistrationSignedContentBean fcm = new FcmTokenRegistrationSignedContentBean(nonce(),
                "fixed-fcm-token-XYZ", "ios", DID);
        String fcmCanon = SimpleRequestHelper.getCanonicalJson(fcm);
        String fcmSig = sign(fcmCanon);
        caseNode(neg, "fcm_content_under_voip_type", "a valid FCM registration content with message_type = "
                + "VOIP_TOKEN_REGISTRATION sent to registervoiptoken", reg, R, fcmCanon,
                envelope(M.valueToTree(fcm), fcmSig, VOIP_TYPE, "fcm_token_registration_signed_content"), sig);

        // the reverse direction, judged by the FCM verifier case
        ArrayNode cross = root.putArray("cross_type_vectors");
        ObjectNode x1 = cross.addObject();
        x1.put("name", "voip_envelope_on_an_fcm_route");
        x1.put("desc", "the valid register_sandbox envelope, as is, sent to registerfcmtoken: its type has no FCM content");
        x1.put("route", ChatServerEndpoints.REGISTER_FCM_TOKEN);
        ObjectNode x1e = envelope(good, goodSig, VOIP_TYPE, CONTENT_KEY);
        x1.set("envelope_json", x1e);
        x1.put("expected", fcmVerdict(x1e));
        ObjectNode x2 = cross.addObject();
        x2.put("name", "voip_content_relabelled_fcm_on_an_fcm_route");
        x2.put("desc", "the same content relabelled FCM_TOKEN_REGISTRATION, sent to registerfcmtoken: the FCM case "
                + "reads fcm_token_registration_signed_content only");
        x2.put("route", ChatServerEndpoints.REGISTER_FCM_TOKEN);
        ObjectNode x2e = envelope(good, goodSig, CHAT_MESSAGE_TYPES.FCM_TOKEN_REGISTRATION.name(), CONTENT_KEY);
        x2.set("envelope_json", x2e);
        x2.put("expected", fcmVerdict(x2e));

        // ---- token_hash ----
        ArrayNode th = root.putArray("token_hash_vectors");
        String[][] triples = {
            {SB, TOPIC, VT_64},
            {PR, TOPIC, VT_64},
            {PR, "io.takamaka.wallet", VT_200}
        };
        for (String[] t : triples) {
            ObjectNode n = th.addObject();
            n.put("env", t[0]);
            n.put("tp", t[1]);
            n.put("vt", t[2]);
            n.put("preimage_utf8_hex", HexFormat.of().formatHex(VoipTokenRules.tokenHashPreimage(t[0], t[1], t[2])));
            n.put("token_hash", VoipTokenRules.tokenHash(t[0], t[1], t[2]));
        }

        // ---- serverinfo (manifest 1.7) ----
        ObjectNode si = root.putObject("serverinfo_vectors");
        ServerInfoResponseBean s17 = new ServerInfoResponseBean();
        s17.setServerVersion("0.11.0-SNAPSHOT");
        // the members a real server always sends (a port's bean may require them)
        s17.setProtocolCurrent("1.2");
        s17.setProtocolMajor(1);
        s17.setProtocolMinMinor(0);
        s17.setProtocolMaxMinor(2);
        s17.setManifestVersion(ServerInfoResponseBean.MANIFEST_VERSION_CURRENT);
        s17.setAudience(AUDIENCE);
        si.put("manifest_version_current", ServerInfoResponseBean.MANIFEST_VERSION_CURRENT);
        si.put("audience_member", "audience");
        si.set("manifest_1_7_json", M.valueToTree(s17));
        si.put("manifest_1_7_expected_audience", AUDIENCE);
        ObjectNode s16 = M.valueToTree(s17);
        s16.remove("audience");
        s16.put("manifestVersion", "1.6");
        si.set("manifest_1_6_json", s16);
        si.put("manifest_1_6_expected_audience", (String) null);
        si.put("note", "A 1.6 server has no 'audience' member: a port must read it as no audience (null), never as an "
                + "error and never as an empty string.");

        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentObjectsWith(new DefaultIndenter("  ", "\n"));
        pp.indentArraysWith(new DefaultIndenter("  ", "\n"));
        return M.writer(pp).writeValueAsString(root) + "\n";
    }

    public static void main(String[] args) throws Exception {
        String text = render();
        for (String a : args) {
            Path p = Paths.get(a);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, text);
            System.out.println("wrote " + p.toAbsolutePath() + " (" + text.length() + " chars)");
        }
    }
}
