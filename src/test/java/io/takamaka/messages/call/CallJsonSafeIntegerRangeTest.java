package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.messages.call.beans.CallChannelBodyBean;
import io.takamaka.messages.call.beans.CallCommitBean;
import io.takamaka.messages.call.beans.CallLimitsBean;
import io.takamaka.messages.call.beans.CallParamsBean;
import io.takamaka.messages.call.beans.CallRingBean;
import io.takamaka.messages.call.beans.CallSignedObject;
import io.takamaka.messages.call.rschat.CallRingNoticeBean;
import io.takamaka.messages.call.rschat.CallRschat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N3: the strict call parser ({@link CallJson}) refuses an integer outside the IEEE-754 safe range ±(2^53−1) (the
 * I-JSON / RFC 8785 interoperable range), as the Dart port: rsclient-flutter {@code lib/src/call/strict_json.dart}
 * ({@code _fail('integer out of the safe range')} for any integer literal) and {@code lib/src/call/call_object.dart}
 * ({@code _Kind.int64}: {@code out of the safe range}). Java read such a value into a {@code long}. Every
 * {@code long}/{@code Long} read through the strict mapper is now range-checked ({@link CallSafeLongDeserializer}),
 * list elements and nested beans included, with an {@code InvalidFormatException} (a
 * {@link MismatchedInputException}, hence a {@link JsonProcessingException}).
 */
@DisplayName("CallJson STRICT: integers within ±(2^53-1) (Java = Dart)")
class CallJsonSafeIntegerRangeTest {

    static final ObjectMapper M = new ObjectMapper();
    static final String AUD = CallConstants.AUD_RSCHAT + "test";

    static final long MAX = 9007199254740991L; // 2^53 - 1
    static final String OVER = "9007199254740992"; // 2^53
    static final String UNDER = "-9007199254740992"; // -2^53

    static void refused(String json, Class<?> type) {
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(json, type), json);
    }

    @Test
    void channelBodyIntegersAtTheBoundsAreAcceptedAndBeyondRefused() throws Exception {
        for (String f : List.of("epoch", "chain_idx", "since", "prev_ts")) {
            CallChannelBodyBean hi = CallJson.parse("{\"" + f + "\":" + MAX + "}", CallChannelBodyBean.class);
            CallChannelBodyBean lo = CallJson.parse("{\"" + f + "\":-" + MAX + "}", CallChannelBodyBean.class);
            assertEquals(MAX, M.valueToTree(hi).get(f).asLong(), f);
            assertEquals(-MAX, M.valueToTree(lo).get(f).asLong(), f);
            for (String raw : List.of(OVER, UNDER, "9223372036854775807", "-9223372036854775808",
                    "9223372036854775808", "100000000000000000000")) {
                refused("{\"" + f + "\":" + raw + "}", CallChannelBodyBean.class);
            }
        }
        refused("{\"h\":\"catchup\",\"epoch\":" + OVER + "}", CallChannelBodyBean.class);
    }

    @Test
    void paramsAndLimitsIntegersAreRangeChecked() throws Exception {
        for (String f : List.of("grace", "rotation", "commit_delay", "refuse_below", "absence_window",
                "committer_timeout", "retention", "backstop", "rotation_max", "owner_timeout", "era_gate")) {
            assertDoesNotThrow(() -> CallJson.parse("{\"" + f + "\":" + MAX + "}", CallParamsBean.class), f);
            assertDoesNotThrow(() -> CallJson.parse("{\"" + f + "\":-" + MAX + "}", CallParamsBean.class), f);
            refused("{\"" + f + "\":" + OVER + "}", CallParamsBean.class);
            refused("{\"" + f + "\":" + UNDER + "}", CallParamsBean.class);
        }
        for (String f : List.of("negative_cache_ms", "positive_cache_ms")) {
            assertDoesNotThrow(() -> CallJson.parse("{\"" + f + "\":" + MAX + "}", CallLimitsBean.class), f);
            refused("{\"" + f + "\":" + OVER + "}", CallLimitsBean.class);
            refused("{\"" + f + "\":" + UNDER + "}", CallLimitsBean.class);
        }
    }

    @Test
    void aNestedBeanAndAListElementAreRangeChecked() throws Exception {
        String box = "{\"t\":\"commit\",\"epoch\":1,\"boxes\":[{\"to\":\"aa\",\"k\":%s,\"box\":\"bb\"}]}";
        assertInstanceOf(CallCommitBean.class, CallJson.parse(String.format(box, MAX)));
        assertInstanceOf(CallCommitBean.class, CallJson.parse(String.format(box, -MAX)));
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(String.format(box, OVER)));
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(String.format(box, UNDER)));
        refused(String.format(box, OVER), CallCommitBean.class);

        TypeReference<List<Long>> longs = new TypeReference<>() {
        };
        assertEquals(List.of(1L, MAX, -MAX), CallJson.mapper().readValue("[1," + MAX + ",-" + MAX + "]", longs));
        for (String raw : List.of(OVER, UNDER)) {
            assertThrows(JsonProcessingException.class,
                    () -> CallJson.mapper().readValue("[1," + raw + "]", longs), raw);
        }
        TypeReference<Map<String, Long>> map = new TypeReference<>() {
        };
        assertThrows(JsonProcessingException.class, () -> CallJson.mapper().readValue("{\"a\":" + OVER + "}", map));
        assertThrows(JsonProcessingException.class, () -> CallJson.mapper().readValue(OVER, long.class));
        assertEquals(MAX, CallJson.mapper().readValue(String.valueOf(MAX), long.class));
    }

    /** {@code json} with the top-level member {@code k} replaced by the raw JSON literal {@code raw}. */
    static String with(String json, String k, String raw) throws Exception {
        ObjectNode n = (ObjectNode) M.readTree(json);
        assertTrue(n.has(k), "no member " + k + " in " + json);
        n.set(k, M.readTree(raw));
        return M.writeValueAsString(n);
    }

    @Test
    void aSignedObjectWithAnOutOfRangeIntegerIsAParseErrorBeforeVerification() throws Exception {
        String ring = CallJsonScalarCoercionTest.signedRing();
        for (String raw : List.of(OVER, UNDER)) {
            String bad = with(ring, "ts", raw);
            JsonProcessingException ex = assertThrows(JsonProcessingException.class, () -> CallJson.parse(bad));
            assertInstanceOf(MismatchedInputException.class, ex, ex.toString());
            refused(bad, CallRingBean.class);
        }
        // a ring GENUINELY signed over ts = 2^53 (serialisation is unchanged) is still refused, at parse
        CallRingBean r = CallJson.parse(ring, CallRingBean.class);
        r.setTs(MAX + 1);
        r.setSg(null);
        CallSignatures.sign(r, CallTestKeys.identity("coercion/caller"));
        String signedOver = CallJson.toWire(r);
        assertTrue(signedOver.contains("\"ts\":" + OVER), signedOver);
        JsonProcessingException ex = assertThrows(JsonProcessingException.class,
                () -> CallJson.parse(signedOver, CallRingBean.class));
        assertInstanceOf(MismatchedInputException.class, ex, ex.toString());
        CallProtocolException pe = assertThrows(CallProtocolException.class,
                () -> CallRschat.checkPushRing(signedOver, AUD, new CallRingNoticeBean(), null, 25));
        assertEquals(CallError.BAD_SIGNATURE, pe.getError());
        assertTrue(pe.getMessage().contains("parse"), pe.getMessage());
        // at the bound it parses and verifies
        r.setTs(MAX);
        r.setSg(null);
        CallSignatures.sign(r, CallTestKeys.identity("coercion/caller"));
        CallSignedObject atMax = CallJson.parse(CallJson.toWire(r));
        assertDoesNotThrow(() -> CallSignatures.verify(atMax, CallConstants.T_RING, AUD));
    }

    @Test
    void theSerialisationSideIsUnchanged() {
        CallChannelBodyBean b = new CallChannelBodyBean();
        b.setEpoch(Long.MAX_VALUE);
        assertTrue(CallJson.toWire(b).contains("\"epoch\":" + Long.MAX_VALUE), CallJson.toWire(b));
    }
}
