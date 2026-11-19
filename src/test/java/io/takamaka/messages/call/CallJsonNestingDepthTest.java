package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import io.takamaka.messages.call.beans.CallChannelBodyBean;
import io.takamaka.messages.call.beans.CallParamsBean;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N4: the strict call parser ({@link CallJson}) caps nesting at {@value CallJson#MAX_NESTING_DEPTH} containers, a
 * RESOURCE CAP (Jackson's default is 1000), through {@code readValue} and {@code CallJson.mapper().readTree} alike,
 * with a {@link StreamConstraintsException} (a {@link JsonProcessingException}).
 *
 * <p>Dart's exact rule (rsclient-flutter 18e8901 {@code strict_json.dart}, {@code _maxDepth = 64}: a VALUE enclosed
 * by more than 64 containers is refused, so 65 containers with an EMPTY innermost pass) is NOT the mapper's: Jackson
 * counts containers, and a cap of 64 would refuse that shape, which a6's shared vector
 * {@code unknown_dropped_depth_max} accepts. Dart's rule is enforced on the unknown-kind channel-body path by
 * {@code CallChannelBodyBean.parseChannelBody}'s whole-tree walk (pinned here too); a known kind is refused by its
 * bean schema long before the cap.
 *
 * <p>Also pinned: the rest of Dart's global refusal list that the strict mapper already refuses (syntax extensions,
 * nested duplicate keys, control characters, bad escapes, a 17-digit integer in a bean).
 */
@DisplayName("CallJson STRICT: nesting resource cap 66, Dart's depth rule on the body walk, Dart's global refusals")
class CallJsonNestingDepthTest {

    static String arrays(int n, String inner) {
        return "[".repeat(n) + inner + "]".repeat(n);
    }

    static String objects(int n, String inner) {
        String s = inner;
        for (int i = 0; i < n; i++) {
            s = "{\"a\":" + s + "}";
        }
        return s;
    }

    static String mixed(int n, String inner) {
        String s = inner;
        for (int i = 0; i < n; i++) {
            s = (i % 2 == 0) ? "[" + s + "]" : "{\"a\":" + s + "}";
        }
        return s;
    }

    static void accepted(String json) throws Exception {
        assertNotNull(CallJson.mapper().readTree(json));
        assertNotNull(CallJson.parse(json, Object.class));
    }

    static void refusedForDepth(String json) {
        StreamConstraintsException t = assertThrows(StreamConstraintsException.class,
                () -> CallJson.mapper().readTree(json));
        assertInstanceOf(JsonProcessingException.class, t);
        assertThrows(StreamConstraintsException.class, () -> CallJson.parse(json, Object.class));
    }

    @Test
    void theMapperCapsAt66ContainersAccepts66Refuses67() throws Exception {
        for (String leaf : List.of("1", "\"x\"", "null", "true", "")) {
            if (!leaf.isEmpty()) {
                accepted(objects(66, leaf));
                accepted(mixed(66, leaf));
                refusedForDepth(objects(67, leaf));
                refusedForDepth(mixed(67, leaf));
            }
            accepted(arrays(66, leaf));
            refusedForDepth(arrays(67, leaf));
        }
        accepted(objects(65, "{}"));
        refusedForDepth(objects(66, "{}"));
        refusedForDepth(arrays(1000, ""));
        assertEquals(66, CallJson.MAX_NESTING_DEPTH);
    }

    @Test
    void dartsExactDepthRuleIsTheChannelBodyWalk() throws Exception {
        // Dart: a VALUE enclosed by more than 64 containers is refused; 65 containers with an EMPTY innermost pass.
        // The mapper's cap is looser (66); CallChannelBodyBean.parseChannelBody's whole-tree walk applies Dart's rule.
        String depth64 = "{\"h\":\"future\",\"x\":" + arrays(63, "1") + "}"; // the 1 is at depth 64
        String depth65 = "{\"h\":\"future\",\"x\":" + arrays(64, "1") + "}"; // the 1 is at depth 65
        String emptyAt64 = "{\"h\":\"future\",\"x\":" + arrays(64, "") + "}"; // 65 containers, innermost empty
        String emptyAt65 = "{\"h\":\"future\",\"x\":" + arrays(65, "") + "}"; // the innermost [] is at depth 65
        assertNotNull(CallJson.mapper().readTree(depth65)); // under the cap
        assertEquals("future", CallChannelBodyBean.parseChannelBody(depth64).getH());
        assertEquals("future", CallChannelBodyBean.parseChannelBody(emptyAt64).getH());
        assertThrows(JsonProcessingException.class, () -> CallChannelBodyBean.parseChannelBody(depth65));
        assertThrows(JsonProcessingException.class, () -> CallChannelBodyBean.parseChannelBody(emptyAt65));
    }

    @Test
    void dartGlobalRefusalsAreRefused() {
        for (String json : List.of(
                "{\"a\":01}", "{\"a\":+1}", "{\"a\":NaN}", "{\"a\":1}/*x*/", "{'a':1}", "{\"a\":1,}",
                "{\"a\":\"x\u0001\"}", "{\"a\":\"x\ty\"}", "{\"a\":\"\\x\"}", "{\"a\":\"\\u12G4\"}",
                "\ufeff{\"a\":1}", "{\"a\":1}\u000b", "\u00a0{\"a\":1}", "{\"a\":{\"b\":1,\"b\":2}}")) {
            assertThrows(JsonProcessingException.class, () -> CallJson.mapper().readTree(json), json);
            assertThrows(JsonProcessingException.class, () -> CallJson.parse(json, Object.class), json);
        }
    }

    @Test
    void aSeventeenDigitIntegerIsRefusedInABean() {
        // 2^53-1 has 16 digits; Dart refuses any longer literal ('integer out of the safe range'). In a bean the
        // N3 range check refuses it (Long), and Jackson's 32-bit check (Integer).
        for (String raw : List.of("10000000000000000", "-10000000000000000", "99999999999999999")) {
            assertThrows(JsonProcessingException.class,
                    () -> CallJson.parse("{\"epoch\":" + raw + "}", CallChannelBodyBean.class), raw);
            assertThrows(JsonProcessingException.class,
                    () -> CallJson.parse("{\"grace\":" + raw + "}", CallParamsBean.class), raw);
            assertThrows(JsonProcessingException.class,
                    () -> CallJson.parse("{\"hq_kbps\":" + raw + "}", io.takamaka.messages.call.beans.CallCapsBean.class),
                    raw);
        }
    }
}
