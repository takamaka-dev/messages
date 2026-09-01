package io.takamaka.messages.vectors;

import io.takamaka.messages.beans.*;
import io.takamaka.messages.beans.implementation.*;
import io.takamaka.messages.utils.SimpleRequestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigInteger;
import java.nio.file.*;

/**
 * Generates the C3 cross-platform vector: the exact bytes Java signs and
 * verifies for a {@code MessageAction}, so the Dart port can be pinned to them.
 *
 * <p>The signed input is {@code SimpleRequestHelper.getRequestJsonCompact(action)}
 * -- plain Jackson, declaration order, deliberately NOT the JCS canonicalisation
 * the chat protocol uses.
 *
 * <p><b>This has no JUnit annotations and no {@code *Test} suffix on purpose</b>:
 * surefire must not run it, because it WRITES the fixture. A generator that runs
 * in the suite regenerates the corpus it is supposed to be checked against, and
 * then the vector agrees with whatever the producer currently does -- which is
 * exactly how the message-action corpus drifted once already (see HANDOFF.md
 * SHELL-N, "the cross-platform fixture corpus had minted the same wrong hash").
 *
 * <p>Regenerate deliberately, and only when the Java reference genuinely changes:
 * <pre>
 * mvn -o -q compile
 * java -cp "target/classes:$(mvn -o dependency:build-classpath \
 *        -Dmdep.outputFile=/dev/stdout -q)" \
 *     io.takamaka.messages.vectors.MessageActionVectorGenerator \
 *     ../wallet-core-flutter/test/fixtures/java_message_action_vectors.json
 * </pre>
 *
 * <p>Consumed by {@code wallet-core-flutter}
 * {@code test/messages/java_message_action_parity_test.dart}. See HANDOFF.md 3.9.
 */
public class MessageActionVectorGenerator {

    static MessageAddress addr(String t, String ma) {
        return new MessageAddress(t, ma);
    }

    /**
     * One vector: the action's typed field values plus the exact compact JSON
     * Java signs for it.
     */
    static ObjectNode caseOf(ObjectMapper m, String id, String note, MessageAction a) throws Exception {
        ObjectNode n = m.createObjectNode();
        n.put("id", id);
        n.put("note", note);
        n.put("fromType", a.getFrom() == null ? null : a.getFrom().getType());
        n.put("fromAddress", a.getFrom() == null ? null : a.getFrom().getAddress());
        n.put("toType", a.getTo() == null ? null : a.getTo().getType());
        n.put("toAddress", a.getTo() == null ? null : a.getTo().getAddress());
        if (a.getDate() == null) { n.putNull("date"); } else { n.put("date", a.getDate()); }
        n.put("green", a.getGreen() == null ? null : a.getGreen().toString());
        n.put("red", a.getRed() == null ? null : a.getRed().toString());
        n.put("textMessage", a.getTextMessage());
        n.put("actionCompactJson", SimpleRequestHelper.getRequestJsonCompact(a));
        return n;
    }

    public static void main(String[] args) throws Exception {
        ObjectMapper m = new ObjectMapper();
        ObjectNode root = m.createObjectNode();
        root.put("version", "1.0.0");
        root.put("generated", "2026-09-01");
        root.put("source", "Messages (Java) SimpleRequestHelper.getRequestJsonCompact");
        root.put("about",
            "C3 / DR-034. Java emits BigInteger g/r as JSON STRINGS in declaration order "
          + "(@JsonFormat shape=STRING); dt stays a bare number. These are the exact bytes "
          + "that are signed and verified, so a Dart producer must reproduce them byte for "
          + "byte. Readers on both platforms accept the legacy bare-number form too, "
          + "permanently -- see DR-034.");
        ArrayNode cases = root.putArray("actionVectors");

        MessageAddress to = addr("c", "abcdefghijkl");
        MessageAddress from = addr("f", "AAAAsignerAAAA.");

        PayRequestAction pay = new PayRequestAction(to, new BigInteger("1000000000000"),
                new BigInteger("25"), "coffee");
        pay.setFrom(from);
        pay.setDate(1756000000000L);
        cases.add(caseOf(m, "C3-001", "pay request, both amounts, signed shape", pay));

        PayRequestAction zero = new PayRequestAction(to, BigInteger.ZERO, BigInteger.ZERO, "zero");
        zero.setFrom(from);
        zero.setDate(1756000000000L);
        cases.add(caseOf(m, "C3-002", "zero amounts are still emitted", zero));

        PayRequestAction greenOnly = new PayRequestAction(to, new BigInteger("5000000000000"), null, null);
        greenOnly.setFrom(from);
        greenOnly.setDate(1756000000000L);
        cases.add(caseOf(m, "C3-003", "null red is omitted entirely (NON_EMPTY)", greenOnly));

        PayRequestAction huge = new PayRequestAction(to,
                new BigInteger("123456789012345678901234567890"), null, "beyond int64");
        huge.setFrom(from);
        huge.setDate(1756000000000L);
        cases.add(caseOf(m, "C3-004",
                "30-digit amount: exact on BOTH platforms only because it rides as a "
              + "string. As a bare number Dart jsonDecode returns a lossy double and the "
              + "amount is unrecoverable -- the whole reason for DR-034", huge));

        PayRequestAction i64 = new PayRequestAction(to,
                new BigInteger("9223372036854775807"), null, "int64 max");
        i64.setFrom(from);
        i64.setDate(1756000000000L);
        cases.add(caseOf(m, "C3-005", "int64 max: the old bare-number ceiling on the Dart VM", i64));

        StakeRequestAction stake = new StakeRequestAction(addr("c", "node_address_hash384"),
                new BigInteger("200000000000"), "stake");
        stake.setFrom(from);
        stake.setDate(1756000000000L);
        cases.add(caseOf(m, "C3-006", "stake request", stake));

        Files.writeString(Paths.get(args[0]),
                m.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");
        System.out.println("wrote " + args[0]);
        for (var c : cases) {
            System.out.println(c.get("id").asText() + "  " + c.get("actionCompactJson").asText());
        }
    }
}
