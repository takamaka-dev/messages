package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.messages.call.beans.CallChannelBodyBean;
import io.takamaka.messages.call.beans.CallRingBean;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N1: the strict call parser ({@link CallJson}) refuses content after the top-level value, as the Dart port's
 * {@code StrictJson.decode} (rsclient-flutter {@code lib/src/call/strict_json.dart}: after the value and trailing
 * whitespace, {@code if (p._i != p._s.length) p._fail('trailing content')}). Jackson accepted
 * {@code {"h":"future"} x} and {@code {}{}} because {@code FAIL_ON_TRAILING_TOKENS} was off. The refusal applies to
 * {@code readValue} and to {@code CallJson.mapper().readTree} (the tree path of the unknown-kind channel body).
 * Leading and trailing whitespace is JSON and stays accepted.
 */
@DisplayName("CallJson STRICT: no trailing content (Java = Dart)")
class CallJsonTrailingTokensTest {

    static void refused(String json, Class<?> type) {
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(json, type), json);
    }

    static final List<String> TRAILING = List.of(
            "{\"h\":\"future\"} x",
            "{}{}",
            "{\"h\":\"media\",\"media\":\"audio\"} 1",
            "{\"h\":\"catchup\",\"epoch\":1}{\"h\":\"catchup\",\"epoch\":2}",
            "{\"h\":\"future\"} ]",
            "{\"h\":\"future\"} {");

    @Test
    void trailingContentIsRefusedByTheBeanParser() {
        for (String json : TRAILING) {
            refused(json, CallChannelBodyBean.class);
        }
    }

    @Test
    void trailingContentIsRefusedByReadTree() {
        for (String json : TRAILING) {
            assertThrows(JsonProcessingException.class, () -> CallJson.mapper().readTree(json), json);
        }
    }

    @Test
    void trailingContentIsRefusedAfterASignedObject() {
        String ring = CallJsonScalarCoercionTest.signedRing();
        assertDoesNotThrow(() -> CallJson.parse(ring));
        for (String tail : List.of(" x", "{}", " 1", ring)) {
            assertThrows(JsonProcessingException.class, () -> CallJson.parse(ring + tail), tail);
            refused(ring + tail, CallRingBean.class);
        }
    }

    @Test
    void aPlainObjectAndSurroundingWhitespaceAreStillAccepted() throws Exception {
        for (String json : List.of(
                "{\"h\":\"future\"}",
                "  {\"h\":\"future\"}",
                "{\"h\":\"future\"}  ",
                "\n\t {\"h\":\"future\"}\r\n\t ",
                "{\"h\":\"media\",\"media\":\"audio\"}\n")) {
            assertNotNull(CallJson.parse(json, CallChannelBodyBean.class), json);
            assertTrue(CallJson.mapper().readTree(json).isObject(), json);
        }
        assertEquals("audio",
                CallJson.parse(" {\"h\":\"media\",\"media\":\"audio\"} ", CallChannelBodyBean.class).getMedia());
        String ring = CallJsonScalarCoercionTest.signedRing();
        assertInstanceOf(CallRingBean.class, CallJson.parse("\n " + ring + " \n"));
    }
}
