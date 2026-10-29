package io.takamaka.messages.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.messages.call.beans.CallParamsBean;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The committed {@code call_params_bounds_v1.json}: equals what the Java reference renders now (drift guard), the
 * published copy in {@code rschat-docs/security/vectors/} is byte-identical (skipped when that tree is not next to
 * this worktree), and every verdict is RE-DERIVED from the file's own {@code params} through the public API — the
 * way the Dart reader ({@code rsclient-flutter/test/call/call_params_bounds_v1_test.dart}) consumes it.
 */
@DisplayName("[0.3] S-4: call_params_bounds_v1.json — the served-params bounds vectors")
class CallParamsBoundsVectorFileTest {

    static final String RESOURCE = "/call/call_params_bounds_v1.json";
    static final ObjectMapper M = new ObjectMapper();
    static String text;
    static JsonNode root;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = CallParamsBoundsVectorFileTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "missing " + RESOURCE + " — run CallParamsBoundsVectorGenerator");
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        root = M.readTree(text);
    }

    @Test
    void committedFileEqualsTheReferenceOutput() throws Exception {
        assertEquals(CallParamsBoundsVectorGenerator.render(), text, "vectors drifted from the Java reference: regenerate deliberately");
        assertTrue(CallBytes.isAscii(text));
        assertEquals(CallParamsBoundsVectorGenerator.VERSION, root.get("version").asText());
    }

    @Test
    void publishedCopyIsIdentical() throws Exception {
        Path p = null;
        String prop = System.getProperty("call.vectors.docs.bounds");
        for (String c : new String[]{prop, "../../rschat-docs/security/vectors/call_params_bounds_v1.json",
            "../rschat-docs/security/vectors/call_params_bounds_v1.json"}) {
            if (c != null && Files.isRegularFile(Paths.get(c))) {
                p = Paths.get(c);
                break;
            }
        }
        Assumptions.assumeTrue(p != null, "rschat-docs not found next to this worktree: published copy not compared");
        assertEquals(text, Files.readString(p), "published vectors differ from the committed copy");
    }

    @Test
    void boundsConstantsArePinned() {
        JsonNode b = root.get("bounds");
        assertEquals(CallParamsBounds.ROTATION_MIN_MS, b.get("rotation_min_ms").asLong());
        assertEquals(CallParamsBounds.COMMIT_DELAY_MIN_MS, b.get("commit_delay_min_ms").asLong());
        assertEquals(CallParamsBounds.COMMIT_DELAY_MAX_MS, b.get("commit_delay_max_ms").asLong());
        assertEquals(CallParamsBounds.BACKSTOP_MAX_MS, b.get("backstop_max_ms").asLong());
        assertEquals(CallParamsBounds.RETENTION_MAX_MS, b.get("retention_max_ms").asLong());
        assertEquals(CallParamsBounds.COMMITTER_TIMEOUT_MIN_MS, b.get("committer_timeout_min_ms").asLong());
        assertEquals(CallParamsBounds.COMMITTER_TIMEOUT_MAX_MS, b.get("committer_timeout_max_ms").asLong());
        assertEquals(CallParamsBounds.GRACE_MIN_MS, b.get("grace_min_ms").asLong());
        assertEquals(CallParamsBounds.GRACE_MAX_MS, b.get("grace_max_ms").asLong());
        assertEquals(CallParamsBounds.CAP_MAX, b.get("cap_max").asInt());
        assertEquals(CallParamsBounds.DEVICES_MAX, b.get("devices_max").asInt());
        assertEquals(11, root.get("rule_order").size());
    }

    @Test
    @DisplayName("every case re-derived from its own params: violation() = reason, inBounds() = expect, check() throws bad_params naming `where: reason`")
    void everyCaseRederived() throws Exception {
        JsonNode cases = root.get("cases");
        assertTrue(cases.size() >= 30, "cases: " + cases.size());
        Set<String> names = new HashSet<>();
        Set<String> rulesFailed = new HashSet<>();
        int pass = 0;
        for (JsonNode c : cases) {
            String name = c.get("name").asText();
            assertTrue(names.add(name), "duplicate case " + name);
            CallParamsBean p = M.treeToValue(c.get("params"), CallParamsBean.class);
            String v = CallParamsBounds.violation(p);
            if ("pass".equals(c.get("expect").asText())) {
                assertNull(v, name);
                assertTrue(c.get("reason").isNull(), name);
                assertTrue(CallParamsBounds.inBounds(p), name);
                CallParamsBounds.check(p, "manifest");
                pass++;
            } else {
                assertEquals("fail", c.get("expect").asText(), name);
                assertEquals(c.get("reason").asText(), v, name);
                assertEquals(c.get("rule").asText(), v.split(" ")[0], name);
                assertFalse(CallParamsBounds.inBounds(p), name);
                CallProtocolException ex = assertThrows(CallProtocolException.class, () -> CallParamsBounds.check(p, "manifest"), name);
                assertEquals(CallError.BAD_PARAMS, ex.getError(), name);
                assertEquals("bad_params: manifest: " + v, ex.getMessage(), name);
                rulesFailed.add(v.split(" ")[0]);
            }
        }
        assertTrue(pass >= 8, "pass cases: " + pass);
        // every rule of the order is exercised by at least one failing case
        for (JsonNode r : root.get("rule_order")) {
            assertTrue(rulesFailed.contains(r.asText()), "no failing case for rule " + r.asText());
        }
    }
}
