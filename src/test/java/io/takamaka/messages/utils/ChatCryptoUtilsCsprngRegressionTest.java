package io.takamaka.messages.utils;

import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.text.RandomStringGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * VB-29 regression — the conversation master key must NOT be reproducible from
 * {@link ThreadLocalRandom} state.
 *
 * <p><b>The defect this pins.</b> {@code ChatCryptoUtils.generateRandomSafeKey} builds a commons-text
 * {@link RandomStringGenerator}. If no random provider is supplied, commons-text falls back to
 * {@code ThreadLocalRandom}, whose per-thread seed is derived from ONE process-wide 64-bit clock-seeded
 * {@code AtomicLong}. The generated string is then a deterministic function of that state — so the E2E
 * conversation master key had at most 64 bits of entropy regardless of its 400-character length. Full
 * analysis: {@code rschat-docs/security/PRNG_ENTROPY_AUDIT.md}.
 *
 * <p><b>Why the seed is restored on the CURRENT thread rather than resetting the static seeder.</b>
 * {@code ThreadLocalRandom.nextSeed()} advances by {@code (t.threadId() << 1) + GOLDEN_GAMMA}, so two
 * *different* threads starting from an identical seed still diverge. A test that reset the static
 * {@code seeder} and generated on fresh threads would pass even against the defective code — a false
 * negative. Restoring {@code Thread.threadLocalRandomSeed} on one thread replays the exact draw sequence,
 * which is the only arrangement that actually detects the regression.
 *
 * <p><b>Requires</b> {@code --add-opens java.base/java.lang=ALL-UNNAMED} (set in the surefire argLine).
 * If the JDK internals move, these tests FAIL loudly rather than skipping: a silently-skipped security
 * regression test is worse than none, and this estate has been bitten by exactly that (see the
 * {@code capability-regression-test-principle} note).
 */
class ChatCryptoUtilsCsprngRegressionTest {

    /** Force {@code localInit()} so the thread has a seed, then hand back the field for save/restore. */
    private static Field primedSeedField() throws Exception {
        ThreadLocalRandom.current().nextInt(); // ensures threadLocalRandomSeed is initialised
        Field f;
        try {
            f = Thread.class.getDeclaredField("threadLocalRandomSeed");
        } catch (NoSuchFieldException e) {
            throw new AssertionError(
                    "Thread.threadLocalRandomSeed is gone — this JDK changed ThreadLocalRandom's internals. "
                    + "UPDATE THIS TEST, do not delete it: it is the only guard on VB-29.", e);
        }
        try {
            f.setAccessible(true);
        } catch (RuntimeException e) {
            throw new AssertionError(
                    "Cannot reflect into java.lang.Thread. The surefire argLine must carry "
                    + "--add-opens java.base/java.lang=ALL-UNNAMED.", e);
        }
        return f;
    }

    @Test
    @DisplayName("the conversation key is NOT reproducible when the ThreadLocalRandom seed is restored")
    void conversationKeyIsNotReproducibleFromThreadLocalRandomState() throws Exception {
        final Field seed = primedSeedField();
        final Thread self = Thread.currentThread();

        final long checkpoint = seed.getLong(self);
        final String first = ChatCryptoUtils.generateRandomSafeKey(400);

        seed.setLong(self, checkpoint);                       // rewind the PRNG to the exact prior state
        final String second = ChatCryptoUtils.generateRandomSafeKey(400);

        Assertions.assertNotEquals(first, second,
                "generateRandomSafeKey reproduced its output after the ThreadLocalRandom seed was rewound. "
                + "That means it is drawing from ThreadLocalRandom again — the whole JVM's randomness would "
                + "collapse to one 64-bit clock-seeded root, and the E2E conversation master key with it. "
                + "Restore .usingRandom(TKM_CSPRNG::nextInt) on the RandomStringGenerator builder.");

        Assertions.assertEquals(400, first.length(), "key length must not change");
        Assertions.assertTrue(first.chars().allMatch(Character::isLetterOrDigit),
                "alphabet must stay [a-zA-Z0-9] — the fix must not alter the wire form");
    }

    @Test
    @DisplayName("control: the harness DOES detect a provider-less generator (proves this test can fail)")
    void controlAProviderLessGeneratorIsReproducible() throws Exception {
        final Field seed = primedSeedField();
        final Thread self = Thread.currentThread();

        // Deliberately the DEFECTIVE configuration — no .usingRandom(...).
        final RandomStringGenerator defective = new RandomStringGenerator.Builder()
                .withinRange('0', 'z')
                .filteredBy(Character::isLetterOrDigit)
                .get();

        final long checkpoint = seed.getLong(self);
        final String first = defective.generate(400);

        seed.setLong(self, checkpoint);
        final String second = defective.generate(400);

        Assertions.assertEquals(first, second,
                "A provider-less RandomStringGenerator did NOT reproduce after the seed was rewound. "
                + "Either commons-text stopped defaulting to ThreadLocalRandom (good — but then the primary "
                + "test above no longer proves anything and must be re-derived), or this harness no longer "
                + "controls the PRNG. Investigate before trusting the primary test.");
    }
}
