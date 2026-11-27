package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes {@code call_vectors_v1.json} (spec §12). NOT a test on purpose (no {@code *Test} suffix): a generator that
 * runs in the suite would regenerate the corpus it is checked against. {@link CallVectorFileTest} regenerates in
 * memory and fails on any drift, then re-verifies the file independently.
 *
 * <pre>
 * mvn -o -q test-compile
 * java -cp "target/test-classes:target/classes:$(cat cp.txt)" io.takamaka.messages.call.CallVectorGenerator \
 *     src/test/resources/call/call_vectors_v1.json \
 *     ../../rschat-docs/security/vectors/call_vectors_v1.json
 * </pre>
 */
public class CallVectorGenerator {

    /** The exact file text: pretty JSON, ASCII only (non-ASCII escaped), trailing newline. */
    public static String render() throws Exception {
        ObjectMapper m = new ObjectMapper();
        m.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, true);
        // LF on every platform: Jackson's default object indenter uses System.lineSeparator() (CRLF on Windows),
        // which made the vectors differ by platform (found 2026-10-09 on the Windows workstation).
        com.fasterxml.jackson.core.util.DefaultPrettyPrinter pp = new com.fasterxml.jackson.core.util.DefaultPrettyPrinter()
                .withObjectIndenter(new com.fasterxml.jackson.core.util.DefaultIndenter("  ", "\n"));
        return m.writer(pp).writeValueAsString(new CallVectorScenario().toJson()) + "\n";
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
