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
package io.takamaka.messages.chat.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.messages.exception.HardProtocolViolationException;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-platform snapshot regression harness (Phase 1 plan §3.5).
 *
 * <p>Loads every frozen fixture under
 * {@code src/test/resources/cross-platform-vectors/fixtures/}, decrypts the
 * inner content with the fixture's symmetric key, runs the full
 * {@link MessageActionValidator} pipeline, and asserts the outcome matches
 * {@code expected_validation.json} (a thrown hard-violation exception, or a
 * {@link ValidationResult} with the documented decorations).</p>
 *
 * <p>The Dart mirror loads the same fixtures and must assert identical
 * outcomes. Parity is by <em>outcome agreement on a fixed corpus</em>, not by
 * byte-identical regeneration (no seeded RNG — §2.11).</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class CrossPlatformVectorTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "cross-platform-vectors", "fixtures");
    private final ObjectMapper mapper = TkmTextUtils.getJacksonMapper();

    @TestFactory
    public List<DynamicTest> crossPlatformVectors() throws Exception {
        File root = FIXTURES.toFile();
        assertTrue(root.isDirectory(), "fixture directory must exist: " + root.getAbsolutePath()
                + " (run FixtureGenerator#regenerateAll to create it)");
        File[] dirs = root.listFiles(File::isDirectory);
        assertNotNull(dirs);
        Arrays.sort(dirs);
        assertEquals(37, dirs.length, "expected 19 valid + 18 invalid = 37 fixtures");

        List<DynamicTest> tests = new ArrayList<>();
        for (File dir : dirs) {
            tests.add(DynamicTest.dynamicTest(dir.getName(), () -> runFixture(dir.toPath())));
        }
        return tests;
    }

    @SuppressWarnings("unchecked")
    private void runFixture(Path dir) throws Exception {
        BasicMessageRequestBean wire = mapper.readValue(
                Files.readString(dir.resolve("wire_format.json")), BasicMessageRequestBean.class);
        Map<String, Object> input = mapper.readValue(
                Files.readString(dir.resolve("input.json")), Map.class);
        Map<String, Object> expected = mapper.readValue(
                Files.readString(dir.resolve("expected_validation.json")), Map.class);

        final String symkey = (String) input.get("symmetricKey");

        BasicMessageEncryptedContentBean inner = ChatCryptoUtils.decryptBasicMessageEncryptedContentBeanWithScope(
                wire.getBasicMessageSignedContentBean().getEncryptedContent(), symkey, CHAT_MESSAGE_TYPES.TOPIC_MESSAGE);

        ValidationContext ctx = buildContext(input, symkey);
        String type = (String) expected.get("type");

        if ("throws".equals(type)) {
            HardProtocolViolationException ex = assertThrows(HardProtocolViolationException.class,
                    () -> MessageActionValidator.validate(inner, wire, ctx),
                    dir.getFileName() + ": expected a hard-violation exception");
            assertEquals(expected.get("exception"), ex.getClass().getSimpleName(),
                    dir.getFileName() + ": exception type");
            assertEquals(expected.get("code"), ex.getCode(),
                    dir.getFileName() + ": exception code");
        } else {
            ValidationResult result = MessageActionValidator.validate(inner, wire, ctx);
            assertEquals(expected.get("overallValid"), result.overallValid(),
                    dir.getFileName() + ": overallValid");
            Set<String> actual = result.decorations().stream()
                    .map(d -> d.code() + "|" + d.severity())
                    .collect(Collectors.toSet());
            List<Map<String, Object>> expDecos = (List<Map<String, Object>>) expected.get("decorations");
            Set<String> expectedSet = expDecos.stream()
                    .map(m -> m.get("code") + "|" + m.get("severity"))
                    .collect(Collectors.toSet());
            assertEquals(expectedSet, actual, dir.getFileName() + ": decorations");
        }
    }

    private ValidationContext buildContext(Map<String, Object> input, String symkey) {
        String creator = (String) input.get("conversationCreatorPk");
        boolean inCache = Boolean.TRUE.equals(input.get("targetInCache"));
        String author = (String) input.get("targetAuthor");

        Function<String, String> resolver = sig -> inCache ? author : null;
        Function<BasicMessageRequestBean, BasicMessageEncryptedContentBean> embeddedDecryptor = env -> {
            try {
                return ChatCryptoUtils.decryptBasicMessageEncryptedContentBeanWithScope(
                        env.getBasicMessageSignedContentBean().getEncryptedContent(), symkey,
                        CHAT_MESSAGE_TYPES.TOPIC_MESSAGE);
            } catch (Exception e) {
                return null; // wrong key / undecryptable embed -> skip nested check
            }
        };
        return new ValidationContext(creator, resolver, embeddedDecryptor);
    }
}
