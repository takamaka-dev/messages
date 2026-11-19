package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.messages.call.beans.CallCapsBean;
import io.takamaka.messages.call.beans.CallChannelBodyBean;
import io.takamaka.messages.call.beans.CallHandBean;
import io.takamaka.messages.call.beans.CallRingBean;
import io.takamaka.messages.call.beans.CallSignedObject;
import io.takamaka.messages.call.service.CallReplyBean;
import java.util.List;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The strict call parser ({@link CallJson}) refuses EVERY scalar coercion, in both directions, mapper-wide — the
 * same inputs the Dart port's schema refuses ({@code not a string} / {@code not an integer} / {@code not a boolean},
 * rsclient-flutter {@code lib/src/call/call_object.dart} {@code _value} and {@code call_service_wire.dart}).
 *
 * <p>Before this pin, Jackson's own {@code String} deserializer turned a JSON number or boolean into its text even
 * with {@code ALLOW_COERCION_OF_SCALARS} disabled ({@code {"h":true}} → {@code "true"}, {@code {"conv_seed":1}} →
 * {@code "1"}). On a SIGNED object the coerced value no longer matched the signature (the verifier rebuilds the
 * canonical form from the parsed bean), but the UNSIGNED channel body accepted what Dart refuses.
 */
@DisplayName("CallJson STRICT: no scalar coercion, either direction (Java = Dart)")
class CallJsonScalarCoercionTest {

    static final ObjectMapper M = new ObjectMapper();
    static final String AUD = CallConstants.AUD_RSCHAT + "test";

    static void refused(String json, Class<?> type) {
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(json, type), json);
    }

    static void refusedPolymorphic(String json) {
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(json), json);
    }

    /** {@code json} with the top-level member {@code k} replaced by the raw JSON literal {@code raw}. */
    static String with(String json, String k, String raw) throws Exception {
        ObjectNode n = (ObjectNode) M.readTree(json);
        assertTrue(n.has(k), "no member " + k + " in " + json);
        n.set(k, M.readTree(raw));
        return M.writeValueAsString(n);
    }

    // ---- the unsigned channel body ------------------------------------------------------------------------------

    @Test
    void channelBodyControlsParse() throws Exception {
        assertEquals("catchup", CallJson.parse("{\"h\":\"catchup\",\"epoch\":17}", CallChannelBodyBean.class).getH());
        assertEquals("00ff", CallJson.parse("{\"conv_seed\":\"00ff\"}", CallChannelBodyBean.class).getConvSeed());
        assertEquals(List.of("aa", "bb"),
                CallJson.parse("{\"roster\":[\"aa\",\"bb\"]}", CallChannelBodyBean.class).getRoster());
        assertEquals(Long.valueOf(17), CallJson.parse("{\"epoch\":17}", CallChannelBodyBean.class).getEpoch());
    }

    @Test
    void channelBodyRefusesANumberOrABooleanForAString() {
        for (String f : List.of("h", "conv_seed", "era", "secret", "req", "key", "media")) {
            for (String raw : List.of("true", "false", "1", "0", "-7", "1.5", "1e3", "-0.0")) {
                refused("{\"" + f + "\":" + raw + "}", CallChannelBodyBean.class);
            }
        }
    }

    @Test
    void channelBodyRefusesANonStringListElement() {
        for (String raw : List.of("1", "true", "1.5")) {
            refused("{\"roster\":[\"aa\"," + raw + "]}", CallChannelBodyBean.class);
        }
    }

    @Test
    void channelBodyRefusesAStringOrABooleanForAnInteger() {
        for (String f : List.of("epoch", "chain_idx", "since", "prev_ts")) {
            for (String raw : List.of("\"17\"", "\"\"", "\"0x11\"", "true", "17.0", "1e1")) {
                refused("{\"" + f + "\":" + raw + "}", CallChannelBodyBean.class);
            }
        }
    }

    // ---- service wire (unsigned): booleans and integers ---------------------------------------------------------

    @Test
    void serviceWireRefusesAStringForABooleanOrAnInteger() throws Exception {
        assertTrue(CallJson.parse("{\"ok\":true}", CallReplyBean.class).getOk());
        for (String raw : List.of("\"true\"", "\"false\"", "1", "0", "\"1\"")) {
            refused("{\"ok\":" + raw + "}", CallReplyBean.class);
        }
        refused("{\"ok\":true,\"n\":1}", CallReplyBean.class);
        refused("{\"ok\":true,\"call\":false}", CallReplyBean.class);
        assertEquals(Integer.valueOf(500), CallJson.parse("{\"hq_kbps\":500}", CallCapsBean.class).getHqKbps());
        for (String raw : List.of("\"500\"", "true", "500.0")) {
            refused("{\"hq_kbps\":" + raw + "}", CallCapsBean.class);
        }
    }

    // ---- signed objects: a ring and a hand ----------------------------------------------------------------------

    static String signedRing() {
        AsymmetricCipherKeyPair kp = CallTestKeys.identity("coercion/caller");
        CallRingBean r = new CallRingBean();
        r.setAud(AUD);
        r.setTs(1_760_000_000_000L);
        r.setN("00112233445566778899aabbccddeeff");
        r.setCall("0123456789abcdef0123456789abcdef");
        r.setSvc("london-1");
        r.setTo(List.of(CallSignatures.identityOf(CallTestKeys.identity("coercion/callee"))));
        r.setMode(CallConstants.MODE_STANDARD);
        r.setMedia(CallConstants.MEDIA_AUDIO);
        CallSignatures.sign(r, kp);
        return CallJson.toWire(r);
    }

    static String signedHand() {
        CallHandBean h = new CallHandBean();
        h.setAud(CallConstants.AUD_SVC + "london-1");
        h.setTs(1_760_000_000_000L);
        h.setN("00112233445566778899aabbccddeeff");
        h.setCall("0123456789abcdef0123456789abcdef");
        h.setLeg("0011223344556677");
        h.setUp(true);
        CallSignatures.sign(h, CallTestKeys.identity("coercion/listener"));
        return CallJson.toWire(h);
    }

    @Test
    void signedControlsParseAndVerify() throws Exception {
        CallRingBean r = CallJson.parse(signedRing(), CallRingBean.class);
        assertDoesNotThrow(() -> CallSignatures.verify(r, CallConstants.T_RING, AUD));
        CallSignedObject any = CallJson.parse(signedRing());
        assertInstanceOf(CallRingBean.class, any);
        CallHandBean h = (CallHandBean) CallJson.parse(signedHand());
        assertDoesNotThrow(() -> CallSignatures.verify(h, CallConstants.T_HAND, CallConstants.AUD_SVC + "london-1"));
    }

    @Test
    void signedRingRefusesANumberOrABooleanForAString() throws Exception {
        String ok = signedRing();
        for (String f : List.of("call", "svc", "mode", "media", "aud", "f", "n", "sg")) {
            for (String raw : List.of("true", "1", "1.5")) {
                String bad = with(ok, f, raw);
                refused(bad, CallRingBean.class);
                refusedPolymorphic(bad);
            }
        }
        for (String raw : List.of("1", "false")) {
            String bad = with(ok, "to", "[" + raw + "]");
            refused(bad, CallRingBean.class);
            refusedPolymorphic(bad);
        }
    }

    @Test
    void signedObjectsRefuseAStringForAnIntegerOrABoolean() throws Exception {
        String ring = signedRing();
        long ts = M.readTree(ring).get("ts").asLong();
        for (String[] c : new String[][]{{"v", "\"1\""}, {"ts", "\"" + ts + "\""}, {"v", "true"}, {"ts", "1.0e12"}}) {
            String bad = with(ring, c[0], c[1]);
            refused(bad, CallRingBean.class);
            refusedPolymorphic(bad);
        }
        String hand = signedHand();
        for (String raw : List.of("\"true\"", "1", "0", "\"1\"")) {
            String bad = with(hand, "up", raw);
            refused(bad, CallHandBean.class);
            refusedPolymorphic(bad);
        }
    }

    @Test
    void theRefusalIsAParseErrorNotASignatureError() throws Exception {
        // the point of the fix: a coerced value never reaches the verifier as a bean
        String bad = with(signedRing(), "svc", "1");
        JsonProcessingException ex = assertThrows(JsonProcessingException.class, () -> CallJson.parse(bad));
        assertInstanceOf(com.fasterxml.jackson.databind.exc.MismatchedInputException.class, ex, ex.toString());
        JsonNode n = M.readTree(bad);
        assertTrue(n.get("svc").isInt());
    }
}
