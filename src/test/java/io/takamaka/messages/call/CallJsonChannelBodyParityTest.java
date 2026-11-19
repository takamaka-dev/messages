package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.messages.call.beans.CallChannelBodyBean;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The strict mapper rules (N1 trailing content, N3 the safe integer range, N4 nesting) seen through THE parser of a
 * received channel body, {@link CallChannelBodyBean#parseChannelBody(String)} (a6, audio-only S1), which reads the
 * tree with {@code CallJson.mapper()} and binds a known kind with {@code CallJson.parse}: the mapper rules and the
 * helper's own whole-tree walk use the same bound ±(2^53−1) and refuse in the same {@link JsonProcessingException}
 * family, for a known kind and an unknown kind alike.
 */
@DisplayName("parseChannelBody: trailing content, ±(2^53-1), depth through the strict mapper")
class CallJsonChannelBodyParityTest {

    static final long MAX = 9007199254740991L;

    static void refused(String json) {
        assertThrows(JsonProcessingException.class, () -> CallChannelBodyBean.parseChannelBody(json), json);
    }

    @Test
    void trailingContentIsRefusedForKnownAndUnknownKinds() throws Exception {
        for (String json : List.of("{\"h\":\"future\"} x", "{}{}", "{\"h\":\"media\",\"media\":\"audio\"} 1",
                "{\"h\":\"future\",\"epoch\":1}{\"h\":\"future\"}", "{\"h\":\"catchup\",\"epoch\":1} ]")) {
            refused(json);
        }
        assertEquals("future", CallChannelBodyBean.parseChannelBody(" {\"h\":\"future\"}\n").getH());
        assertEquals("audio",
                CallChannelBodyBean.parseChannelBody("{\"h\":\"media\",\"media\":\"audio\"} ").declaredMedia());
    }

    @Test
    void theSafeRangeIsTheSameOnBothPaths() throws Exception {
        for (String h : List.of("catchup", "future")) { // known kind (bean, N3) / unknown kind (a6's walk)
            assertEquals(MAX, CallChannelBodyBean.parseChannelBody("{\"h\":\"" + h + "\",\"epoch\":" + MAX + "}")
                    .getEpoch());
            assertEquals(-MAX, CallChannelBodyBean.parseChannelBody("{\"h\":\"" + h + "\",\"epoch\":-" + MAX + "}")
                    .getEpoch());
            refused("{\"h\":\"" + h + "\",\"epoch\":" + (MAX + 1) + "}");
            refused("{\"h\":\"" + h + "\",\"epoch\":-" + (MAX + 1) + "}");
            refused("{\"h\":\"" + h + "\",\"epoch\":10000000000000000}");
        }
        for (String f : List.of("chain_idx", "since", "prev_ts")) {
            refused("{\"h\":\"epoch\",\"" + f + "\":" + (MAX + 1) + "}");
        }
        refused("{\"h\":\"future\",\"x\":[1," + (MAX + 1) + "]}");
    }

    @Test
    void aValueBeyondDepth64IsRefusedOnBothPaths() throws Exception {
        // innermost VALUE at depth 64 (root object + 63 arrays): accepted; at depth 65: refused
        assertEquals("future",
                CallChannelBodyBean.parseChannelBody("{\"h\":\"future\",\"x\":" + "[".repeat(63) + "1"
                        + "]".repeat(63) + "}").getH());
        refused("{\"h\":\"future\",\"x\":" + "[".repeat(64) + "1" + "]".repeat(64) + "}");
        refused("{\"h\":\"catchup\",\"x\":" + "[".repeat(64) + "1" + "]".repeat(64) + "}");
        refused("{\"h\":\"future\",\"x\":" + "[".repeat(66) + "]".repeat(66) + "}");
    }
}
