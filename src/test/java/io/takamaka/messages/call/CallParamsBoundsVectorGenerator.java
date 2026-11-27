package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.messages.call.beans.CallParamsBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 * Writes {@code call_params_bounds_v1.json} — the cross-platform vectors of the [0.3] S-4 served-params bounds
 * ({@link CallParamsBounds}): the real profiles PASS (default = London TEST, loop, loopadaptive, the callsvc test
 * profile, the 10-field initial values, rotation 0 = adaptive), the boundary-exact values on both sides of every
 * limit, and each HOSTILE value FAILS with its rule named — the first violated rule in the fixed check order, the
 * same string on Java and Dart. A SEPARATE file from the frozen {@code call_vectors_v1.json} (never regenerated for
 * this). NOT a test on purpose (no {@code *Test} suffix): {@link CallParamsBoundsVectorFileTest} regenerates in memory,
 * fails on drift, and re-derives every verdict from the file's own {@code params}.
 *
 * <p>Not in this file, on purpose: the manifest's {@code ver} range (min ≤ 1.0 ≤ max) is not a bounds rule on either
 * platform — the only version check is the object's {@code v} against {@code CallConstants.VERSION}; the S-5 rank cap
 * (5) is a committer rule, not a param bound; the {@code devices_per_identity} cap is 5.
 *
 * <pre>
 * mvn -o -q test-compile
 * java -cp "target/test-classes:target/classes:$(cat cp.txt)" io.takamaka.messages.call.CallParamsBoundsVectorGenerator \
 *     src/test/resources/call/call_params_bounds_v1.json \
 *     ../../rschat-docs/security/vectors/call_params_bounds_v1.json
 * </pre>
 */
public class CallParamsBoundsVectorGenerator {

    static final String VERSION = "1.0.0";

    /** The exact file text: pretty JSON, ASCII only, LF, trailing newline (as {@link CallVectorGenerator}). */
    public static String render() throws Exception {
        ObjectMapper m = new ObjectMapper();
        m.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, true);
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n"));
        return m.writer(pp).writeValueAsString(toJson(m)) + "\n";
    }

    static ObjectNode toJson(ObjectMapper m) {
        ObjectNode root = m.createObjectNode();
        root.put("spec", "tkm-call/v1 [0.3] S-4 served-params bounds (CallParamsBounds)");
        root.put("version", VERSION);
        root.put("generator", "Messages io.takamaka.messages.call.CallParamsBoundsVectorGenerator (feat/C182-calls-v1)");
        root.put("about", "Every client refuses a served parameter profile outside these bounds with bad_params at first contact "
                + "(the manifest), at create (the manifest) and at join (the creation record). For each case the client's "
                + "CallParamsBounds.violation(params) must equal `reason` (null = in bounds); check(params, where) must throw "
                + "bad_params with `where: reason`. Rules are checked in a fixed order and the FIRST violated one names the reason. "
                + "Integer division: refuse_below's lower limit is commit_delay/2 rounded down. A missing field (other than "
                + "rotation_max) is a violation. Not covered here on purpose: the manifest ver range and the S-5 rank cap are not "
                + "param bounds.");
        ObjectNode b = root.putObject("bounds");
        b.put("rotation_min_ms", CallParamsBounds.ROTATION_MIN_MS);
        b.put("commit_delay_min_ms", CallParamsBounds.COMMIT_DELAY_MIN_MS);
        b.put("commit_delay_max_ms", CallParamsBounds.COMMIT_DELAY_MAX_MS);
        b.put("backstop_max_ms", CallParamsBounds.BACKSTOP_MAX_MS);
        b.put("retention_max_ms", CallParamsBounds.RETENTION_MAX_MS);
        b.put("committer_timeout_min_ms", CallParamsBounds.COMMITTER_TIMEOUT_MIN_MS);
        b.put("committer_timeout_max_ms", CallParamsBounds.COMMITTER_TIMEOUT_MAX_MS);
        b.put("grace_min_ms", CallParamsBounds.GRACE_MIN_MS);
        b.put("grace_max_ms", CallParamsBounds.GRACE_MAX_MS);
        b.put("cap_min", 2);
        b.put("cap_max", CallParamsBounds.CAP_MAX);
        b.put("devices_min", 1);
        b.put("devices_max", CallParamsBounds.DEVICES_MAX);
        ArrayNode order = root.putArray("rule_order");
        for (String r : new String[]{"rotation", "commit_delay", "refuse_below", "backstop", "rotation_max", "retention",
            "committer_timeout", "grace", "absence_window", "cap", "devices_per_identity"}) {
            order.add(r);
        }
        ArrayNode cases = root.putArray("cases");
        // ---- the real profiles (callsvc src/main/resources/application*.yml and the §4.1 initial values)
        add(cases, m, "default profile (London TEST since C-30): the §4.1 initial values + rotation_max 300 s", dflt());
        add(cases, m, "initial values without rotation_max (optional field)", CallParamsBean.initialValues());
        add(cases, m, "loop profile (local test loop, rotation_max 6 s, backstop 30 s)", loop(6_000L, 30_000L));
        add(cases, m, "loopadaptive profile (rotation_max 60 s, backstop 60 s)", loop(60_000L, 60_000L));
        add(cases, m, "callsvc test profile (grace 1.5 s, absence 0.4 s, rotation = commit_delay 3 s, refuse_below 1.5 s = cd/2)",
                of(1_500L, 3_000L, 3_000L, 1_500L, 400L, 2_000L, 10_000L, 300_000L, 50, 3, 300_000L));
        add(cases, m, "rotation 0 = adaptive (the committer's engine chooses), rotation_max still bounded by backstop",
                with(dflt(), p -> p.setRotation(0L)));
        add(cases, m, "the clients' fast test profile (tkm_calls C-29 tests): rotation 3 s, commit_delay 3 s, refuse_below 1.5 s, backstop 5 s",
                of(1_000L, 3_000L, 3_000L, 1_500L, 200L, 500L, 2_000L, 5_000L, 50, 3, null));
        // ---- boundary-exact PASS
        add(cases, m, "boundary: commit_delay = 3000 with rotation 1000, refuse_below = 1500 (= cd/2), backstop = commit_delay",
                of(1_000L, 1_000L, 3_000L, 1_500L, 0L, 500L, 0L, 3_000L, 2, 1, 1_000L));
        add(cases, m, "boundary: commit_delay = 120000 (max), refuse_below = commit_delay, backstop = 300000 (max), rotation_max = backstop",
                of(120_000L, 120_000L, 120_000L, 120_000L, 120_000L, 10_000L, 30_000L, 300_000L, 100, 5, 300_000L));
        add(cases, m, "boundary: refuse_below = 1500 with commit_delay 3001 (integer division: 3001/2 = 1500)",
                of(1_000L, 1_000L, 3_001L, 1_500L, 0L, 500L, 0L, 3_001L, 2, 1, null));
        // ---- hostile / out of bounds: each names the FIRST violated rule
        add(cases, m, "hostile: commit_delay 3 h (the HANDOFF example)", with(dflt(), p -> p.setCommitDelay(3L * 3_600_000L)));
        add(cases, m, "hostile: rotation_max 400 s > backstop", with(dflt(), p -> p.setRotationMax(400_000L)));
        add(cases, m, "hostile: refuse_below 0 (a commit refused never)", with(dflt(), p -> p.setRefuseBelow(0L)));
        add(cases, m, "hostile: refuse_below 66001 > commit_delay", with(dflt(), p -> p.setRefuseBelow(66_001L)));
        add(cases, m, "hostile: backstop 1 h with rotation 0", with(dflt(), p -> {
            p.setRotation(0L);
            p.setBackstop(3_600_000L);
        }));
        add(cases, m, "hostile: backstop 300001 (max + 1)", with(dflt(), p -> p.setBackstop(300_001L)));
        add(cases, m, "hostile: retention 10 min", with(dflt(), p -> p.setRetention(600_000L)));
        add(cases, m, "hostile: rotation 500 ms (< 1 s and not 0)", with(dflt(), p -> p.setRotation(500L)));
        add(cases, m, "hostile: rotation 90 s > commit_delay 66 s (names commit_delay, the first rule that fails)",
                with(dflt(), p -> p.setRotation(90_000L)));
        add(cases, m, "hostile: commit_delay 2999 (min - 1)", of(1_000L, 1_000L, 2_999L, 1_500L, 0L, 500L, 0L, 3_000L, 2, 1, null));
        add(cases, m, "hostile: commit_delay 120001 (max + 1)",
                of(120_000L, 120_000L, 120_001L, 120_000L, 120_000L, 10_000L, 30_000L, 300_000L, 100, 5, null));
        add(cases, m, "hostile: refuse_below 1499 with commit_delay 3001 (below the rounded-down half)",
                of(1_000L, 1_000L, 3_001L, 1_499L, 0L, 500L, 0L, 3_001L, 2, 1, null));
        add(cases, m, "hostile: committer_timeout 100 ms", with(dflt(), p -> p.setCommitterTimeout(100L)));
        add(cases, m, "hostile: committer_timeout 10001 (max + 1)", with(dflt(), p -> p.setCommitterTimeout(10_001L)));
        add(cases, m, "hostile: grace 0", with(dflt(), p -> p.setGrace(0L)));
        add(cases, m, "hostile: grace 120001 (max + 1)", with(dflt(), p -> p.setGrace(120_001L)));
        add(cases, m, "hostile: absence_window 61 s > grace 60 s", with(dflt(), p -> p.setAbsenceWindow(61_000L)));
        add(cases, m, "hostile: retention -1", with(dflt(), p -> p.setRetention(-1L)));
        add(cases, m, "hostile: cap 1000", with(dflt(), p -> p.setCap(1000)));
        add(cases, m, "hostile: cap 1 (min - 1)", with(dflt(), p -> p.setCap(1)));
        add(cases, m, "hostile: devices_per_identity 0", with(dflt(), p -> p.setDevicesPerIdentity(0)));
        add(cases, m, "hostile: devices_per_identity 6 (max + 1)", with(dflt(), p -> p.setDevicesPerIdentity(6)));
        add(cases, m, "hostile: grace missing (a missing field is a violation)", with(dflt(), p -> p.setGrace(null)));
        add(cases, m, "hostile: two rules broken (commit_delay 3 h AND cap 1000): the FIRST in the order names the reason",
                with(dflt(), p -> {
                    p.setCommitDelay(3L * 3_600_000L);
                    p.setCap(1000);
                }));
        return root;
    }

    static void add(ArrayNode cases, ObjectMapper m, String name, CallParamsBean p) {
        ObjectNode c = cases.addObject();
        c.put("name", name);
        c.set("params", m.valueToTree(p));
        String v = CallParamsBounds.violation(p);
        c.put("expect", v == null ? "pass" : "fail");
        if (v == null) {
            c.putNull("reason");
        } else {
            c.put("reason", v);
            c.put("rule", v.split(" ")[0]);
        }
    }

    static CallParamsBean dflt() {
        CallParamsBean p = CallParamsBean.initialValues();
        p.setRotationMax(300_000L);
        return p;
    }

    static CallParamsBean loop(long rotationMax, long backstop) {
        CallParamsBean p = new CallParamsBean(6_000L, 6_000L, 6_600L, 5_400L, 500L, 2_000L, 10_000L, backstop, 50, 3);
        p.setRotationMax(rotationMax);
        return p;
    }

    static CallParamsBean of(Long grace, Long rotation, Long cd, Long rb, Long absence, Long ct, Long retention, Long backstop,
            Integer cap, Integer dev, Long rotationMax) {
        CallParamsBean p = new CallParamsBean(grace, rotation, cd, rb, absence, ct, retention, backstop, cap, dev);
        p.setRotationMax(rotationMax);
        return p;
    }

    static CallParamsBean with(CallParamsBean p, Consumer<CallParamsBean> f) {
        f.accept(p);
        return p;
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
