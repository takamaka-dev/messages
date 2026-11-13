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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.messages.chat.constant.ChatServerEndpoints;
import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.InvalidChatMessageSignatureException;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.ChatUtils;
import io.takamaka.messages.utils.SimpleRequestHelper;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.utils.TkmTextUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DR-074 §5.2 to §5.4: the VoIP-token beans, their own verifier case, the content rules and the response shape.
 */
class VoipTokenSignVerifyTest {

    static final String AUD = "rschat:rig1";
    static final String VT = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    static final String TP = "io.takamaka.tkmchat.test";
    static final String DID = "3f2a9c1e-7b4d-4e8a-9c2f-5d6e7f8a9b0c";
    static InstanceWalletKeystoreInterface iwk;

    @BeforeAll
    static void setUp() throws Exception {
        iwk = new InstanceWalletKeyStoreBCED25519("voip_token_test_wallet", "voipPwd");
    }

    static NonceResponseBean nonce() {
        return new NonceResponseBean("81b641a3-51f0-4941-9912-68d86c2b0d2b", 1_700_000_000_000L, 60_000L);
    }

    static VoipTokenRequestBean signed(String op, String aud, String vt, String env, String tp, String did) throws Exception {
        return ChatCryptoUtils.getSignedVoipTokenRequest(nonce(), op, aud, vt, env, tp, did, iwk, 0);
    }

    static String json(Object o) throws Exception {
        return TkmTextUtils.getJacksonMapper().writeValueAsString(o);
    }

    @Test
    void constants() {
        assertEquals("registervoiptoken", ChatServerEndpoints.REGISTER_VOIP_TOKEN);
        assertEquals("unregistervoiptoken", ChatServerEndpoints.UNREGISTER_VOIP_TOKEN);
        assertEquals("VOIP_TOKEN_REGISTRATION", CHAT_MESSAGE_TYPES.VOIP_TOKEN_REGISTRATION.name());
    }

    @Test
    void canonicalForm_sevenSortedKeys_noOthers() throws Exception {
        VoipTokenSignedContentBean c = new VoipTokenSignedContentBean(nonce(), "register", AUD, VT, "sandbox", TP, DID);
        assertEquals("{\"aud\":\"rschat:rig1\",\"did\":\"" + DID + "\",\"env\":\"sandbox\",\"nonce\":{\"liveness\":60000,"
                + "\"nonce\":\"81b641a3-51f0-4941-9912-68d86c2b0d2b\",\"timestamp\":1700000000000},\"op\":\"register\","
                + "\"tp\":\"" + TP + "\",\"vt\":\"" + VT + "\"}", SimpleRequestHelper.getCanonicalJson(c));
    }

    @Test
    void canonicalForm_keepsANullMember() throws Exception {
        // the mapper KEEPS nulls (Iris §7.162, pinned here): a client that signs "did":null verifies, and is then
        // refused by the field check — never by the signature
        VoipTokenSignedContentBean c = new VoipTokenSignedContentBean(nonce(), "register", AUD, VT, "sandbox", TP, null);
        assertTrue(SimpleRequestHelper.getCanonicalJson(c).contains("\"did\":null"));
    }

    @Test
    void roundTrip_verifiesThroughItsOwnCase() throws Exception {
        VoipTokenRequestBean req = signed("register", AUD, VT, "sandbox", TP, DID);
        assertEquals("VOIP_TOKEN_REGISTRATION", req.getMessageType());
        assertEquals(iwk.getPublicKeyAtIndexURL64(0), req.getFrom());
        String wire = json(req);
        assertTrue(wire.contains("\"voip_token_signed_content\":{"), wire);
        assertFalse(wire.contains("unknownKeys"), "the unknown-key set is never serialised: " + wire);
        Object verified = ChatCryptoUtils.verifySignedMessage(wire);
        assertInstanceOf(VoipTokenRequestBean.class, verified);
        assertEquals(req, ChatUtils.fromJsonToVoipTokenRequestBean(wire));
        assertNull(VoipTokenRules.verifySignature(req));
        assertNull(VoipTokenRules.check(req.getVoipTokenSignedContentBean(), "register", AUD));
    }

    @Test
    void tamperedMember_isRefusedBySignature() throws Exception {
        for (int i = 0; i < 6; i++) {
            VoipTokenRequestBean req = signed("register", AUD, VT, "sandbox", TP, DID);
            VoipTokenSignedContentBean c = req.getVoipTokenSignedContentBean();
            switch (i) {
                case 0 -> c.setOp("unregister");
                case 1 -> c.setAud("rschat:rig2");
                case 2 -> c.setVt(VT.substring(0, 63) + "0");
                case 3 -> c.setEnv("production");
                case 4 -> c.setTp(TP + "x");
                default -> c.setDid(DID + "x");
            }
            final String wire = json(req);
            assertThrows(InvalidChatMessageSignatureException.class, () -> ChatCryptoUtils.verifySignedMessage(wire), "member " + i);
            assertEquals(VoipTokenRules.SIGNATURE_ERROR, VoipTokenRules.verifySignature(req).code());
        }
    }

    @Test
    void substitutedFrom_isRefused() throws Exception {
        VoipTokenRequestBean req = signed("register", AUD, VT, "sandbox", TP, DID);
        req.setFrom(iwk.getPublicKeyAtIndexURL64(1));
        final String wire = json(req);
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(wire));
    }

    @Test
    void theTypeHasItsOwnCase_anFcmTypedEnvelopeDoesNotCarryAVoipContent() throws Exception {
        VoipTokenRequestBean req = signed("register", AUD, VT, "sandbox", TP, DID);
        // relabelled: the FCM case must not verify a VoIP content (it reads its own content key only)
        req.setMessageType(CHAT_MESSAGE_TYPES.FCM_TOKEN_REGISTRATION.name());
        final String wire = json(req);
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(wire));
        assertEquals(VoipTokenRules.SIGNATURE_ERROR, VoipTokenRules.verifySignature(req).code());
        // and a VoIP-typed envelope with no VoIP content is refused, not verified over "null"
        VoipTokenRequestBean empty = new VoipTokenRequestBean(null, req.getFrom(), req.getSignature(),
                "VOIP_TOKEN_REGISTRATION", "Ed25519BC");
        final String emptyWire = json(empty);
        assertThrows(InvalidChatMessageSignatureException.class, () -> ChatCryptoUtils.verifySignedMessage(emptyWire));
    }

    @Test
    void check_order_missingThenUnknownThenOpThenAudThenPatterns() throws Exception {
        assertEquals(VoipTokenRules.VOIP_FIELD_MISSING, VoipTokenRules.check(null, "register", AUD).code());
        VoipTokenSignedContentBean c = new VoipTokenSignedContentBean(nonce(), "bogus", "rschat:x", "ZZ", "dev", "a/b", null);
        VoipTokenRules.Refusal r = VoipTokenRules.check(c, "register", AUD);
        assertEquals(VoipTokenRules.VOIP_FIELD_MISSING, r.code());
        assertEquals("did is required", r.message());
        c.setDid("short");
        assertEquals(VoipTokenRules.OP_MISMATCH, VoipTokenRules.check(c, "register", AUD).code());
        c.setOp("register");
        assertEquals(VoipTokenRules.AUD_MISMATCH, VoipTokenRules.check(c, "register", AUD).code());
        c.setAud(AUD);
        assertEquals("vt must be lowercase hex, even length, 64 to 200 characters", VoipTokenRules.check(c, "register", AUD).message());
        c.setVt(VT);
        assertEquals("env must be sandbox or production", VoipTokenRules.check(c, "register", AUD).message());
        c.setEnv("production");
        assertEquals("tp must match [A-Za-z0-9.-]{1,155}", VoipTokenRules.check(c, "register", AUD).message());
        c.setTp(TP);
        assertEquals("did must match [A-Za-z0-9._-]{8,128}", VoipTokenRules.check(c, "register", AUD).message());
        c.setDid(DID);
        assertNull(VoipTokenRules.check(c, "register", AUD));
        assertEquals(VoipTokenRules.OP_MISMATCH, VoipTokenRules.check(c, "unregister", AUD).code());
        // each member, null in turn
        for (int i = 0; i < 7; i++) {
            VoipTokenSignedContentBean n = new VoipTokenSignedContentBean(i == 0 ? null : nonce(), i == 1 ? null : "register",
                    i == 2 ? null : AUD, i == 3 ? null : VT, i == 4 ? null : "sandbox", i == 5 ? null : TP, i == 6 ? null : DID);
            assertEquals(VoipTokenRules.VOIP_FIELD_MISSING, VoipTokenRules.check(n, "register", AUD).code(), "member " + i);
        }
        // a nonce object without its value is a missing member too
        VoipTokenSignedContentBean noValue = new VoipTokenSignedContentBean(new NonceResponseBean(null, 1L, 1L), "register",
                AUD, VT, "sandbox", TP, DID);
        assertEquals("nonce is required", VoipTokenRules.check(noValue, "register", AUD).message());
    }

    @Test
    void patterns_neverNormalise() {
        assertTrue(VoipTokenRules.isVt(VT));
        assertFalse(VoipTokenRules.isVt(VT.toUpperCase()), "uppercase is refused, not lowercased");
        assertFalse(VoipTokenRules.isVt(VT + "a"), "odd length");
        assertFalse(VoipTokenRules.isVt(" " + VT + " ".repeat(1)), "never trimmed");
        assertFalse(VoipTokenRules.isVt(VT.substring(2)), "below 64");
        assertTrue(VoipTokenRules.isVt("ab".repeat(100)));
        assertFalse(VoipTokenRules.isVt("ab".repeat(101)), "above 200");
        assertFalse(VoipTokenRules.isVt(VT + "\n"), "a trailing line feed must not pass");
        assertFalse(VoipTokenRules.isTp("io.takamaka.x\n"));
        assertFalse(VoipTokenRules.isTp(""));
        assertTrue(VoipTokenRules.isTp("a".repeat(155)));
        assertFalse(VoipTokenRules.isTp("a".repeat(156)));
        assertFalse(VoipTokenRules.isTp("io.takamaka_x"));
        assertTrue(VoipTokenRules.isDid("a".repeat(8)));
        assertFalse(VoipTokenRules.isDid("a".repeat(7)));
        assertTrue(VoipTokenRules.isDid("a".repeat(128)));
        assertFalse(VoipTokenRules.isDid("a".repeat(129)));
        assertFalse(VoipTokenRules.isEnv("Sandbox"));
        assertFalse(VoipTokenRules.isOp("REGISTER"));
    }

    @Test
    void tokenHash_dependsOnAllThree_andRefusesAnInvalidTriple() {
        String h = VoipTokenRules.tokenHash("sandbox", TP, VT);
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"));
        assertNotEquals(h, VoipTokenRules.tokenHash("production", TP, VT));
        assertNotEquals(h, VoipTokenRules.tokenHash("sandbox", TP + "x", VT));
        assertNotEquals(h, VoipTokenRules.tokenHash("sandbox", TP, VT.substring(0, 62) + "00"));
        assertThrows(IllegalArgumentException.class, () -> VoipTokenRules.tokenHash("sandbox", TP, VT.toUpperCase()));
        assertThrows(IllegalArgumentException.class, () -> VoipTokenRules.tokenHash("dev", TP, VT));
    }

    @Test
    void unknownKey_isCollected_neverWrittenBack_andRefusedByTheCheck() throws Exception {
        VoipTokenRequestBean req = signed("register", AUD, VT, "sandbox", TP, DID);
        String wire = json(req).replace("\"op\":\"register\"", "\"op\":\"register\",\"platform\":\"ios\"");
        // a strict mapper and a lenient one both parse it: the bean takes the key itself
        for (ObjectMapper m : new ObjectMapper[]{TkmTextUtils.getJacksonMapper(), VoipTokenVectorGenerator.LENIENT}) {
            VoipTokenRequestBean back = m.readValue(wire, VoipTokenRequestBean.class);
            assertTrue(back.getVoipTokenSignedContentBean().hasUnknownKeys());
            assertFalse(json(back).contains("platform"));
            assertNull(VoipTokenRules.verifySignature(back), "the seven signed members still verify");
            assertEquals(VoipTokenRules.VALIDATION_ERROR,
                    VoipTokenRules.check(back.getVoipTokenSignedContentBean(), "register", AUD).code());
        }
    }

    @Test
    void response_wireNames() throws Exception {
        String ok = json(VoipTokenResponseBean.success("registered", 1_700_000_000_123L));
        assertEquals("{\"success\":true,\"message\":\"registered\",\"error_code\":null,\"registration_time\":1700000000123}", ok);
        VoipTokenResponseBean err = VoipTokenResponseBean.error(VoipTokenRules.VOIP_DISABLED, "off");
        assertFalse(err.isSuccess());
        assertEquals(err, TkmTextUtils.getJacksonMapper().readValue(json(err), VoipTokenResponseBean.class));
        assertTrue(json(err).contains("\"error_code\":\"VOIP_DISABLED\""));
    }
}
