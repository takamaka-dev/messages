package io.takamaka.messages.legacy.wkch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.messages.legacy.wkch.LegacyWkchRsaKeyDerivation.Derived;
import io.takamaka.messages.legacy.wkch.LegacyWkchRsaKeyDerivation.Memo;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The legacy WKCH memo holds PRIVATE keys: it must be forgettable by the client (vault lock / wallet
 * removal — the Dart selector's {@code clearMemory()}), bounded, and a forget during a derivation must not
 * be undone by that derivation finishing.
 */
class LegacyWkchForgetTest {

    private static Derived fake(String tag) {
        return new Derived(null, tag, new AtomicBoolean(false));
    }

    @Test
    @DisplayName("public API: forgetAll() and forget(seed) are public static; the bound is small")
    void publicSurface() throws Exception {
        assertTrue(Modifier.isPublic(LegacyWkchRsaKeyDerivation.class.getMethod("forgetAll").getModifiers()));
        assertTrue(Modifier.isPublic(
                LegacyWkchRsaKeyDerivation.class.getMethod("forget", String.class).getModifiers()));
        assertTrue(LegacyWkchRsaKeyDerivation.MAX_CACHED_KEYS >= 1 && LegacyWkchRsaKeyDerivation.MAX_CACHED_KEYS <= 64);
    }

    @Test
    @DisplayName("real key: forgetAll() and forget(seed) drop the keypair; another seed's forget does not")
    void forgetRealDerivation() throws Exception {
        String seed;
        try (InputStream in = LegacyWkchForgetTest.class.getResourceAsStream("/legacy_wkch_rsa_vectors.json")) {
            assertNotNull(in);
            seed = new ObjectMapper().readTree(in).get("seed").asText();
        }
        String hash = LegacyWkchRsaKeyDerivation.derivePublicKeyHash(seed, 0);
        AsymmetricCipherKeyPair first = LegacyWkchRsaKeyDerivation.deriveKeyPair(seed, 0);
        assertTrue(LegacyWkchRsaKeyDerivation.isCached(seed, 0));

        // no-ops: null / empty / another wallet's seed
        LegacyWkchRsaKeyDerivation.forget(null);
        LegacyWkchRsaKeyDerivation.forget("");
        LegacyWkchRsaKeyDerivation.forget(seed + "-another-wallet");
        assertTrue(LegacyWkchRsaKeyDerivation.isCached(seed, 0));
        assertSame(first, LegacyWkchRsaKeyDerivation.deriveKeyPair(seed, 0));

        LegacyWkchRsaKeyDerivation.forget(seed);
        assertFalse(LegacyWkchRsaKeyDerivation.isCached(seed, 0));

        // re-derivation after a forget: a NEW object, the same key, and "regenerated" is reported again
        AsymmetricCipherKeyPair second = LegacyWkchRsaKeyDerivation.deriveKeyPair(seed, 0);
        assertNotSame(first, second);
        assertEquals(LegacyWkchRsaKeyDerivation.modulusHex(first), LegacyWkchRsaKeyDerivation.modulusHex(second));
        assertEquals(hash, LegacyWkchRsaKeyDerivation.derivePublicKeyHash(seed, 0));
        assertTrue(LegacyWkchRsaKeyDerivation.claimFirstUse(seed, 0));

        LegacyWkchRsaKeyDerivation.forgetAll();
        assertFalse(LegacyWkchRsaKeyDerivation.isCached(seed, 0));
        assertEquals(0, LegacyWkchRsaKeyDerivation.cachedKeys());
        LegacyWkchRsaKeyDerivation.forgetAll(); // idempotent
    }

    @Test
    @DisplayName("memo: bounded LRU — the least recently USED entry is the one forgotten")
    void boundedLru() {
        Memo memo = new Memo(3);
        AtomicInteger derivations = new AtomicInteger();
        for (String k : new String[]{"a:0", "b:0", "c:0"}) {
            memo.get(k, () -> {
                derivations.incrementAndGet();
                return fake(k);
            });
        }
        assertEquals(3, memo.size());
        memo.get("a:0", () -> {
            throw new AssertionError("a:0 is memoized");
        }); // touch a → b is now the eldest
        memo.get("d:0", () -> fake("d"));
        assertEquals(3, memo.size());
        assertTrue(memo.contains("a:0"));
        assertFalse(memo.contains("b:0"), "least recently used entry evicted");
        assertTrue(memo.contains("c:0"));
        assertTrue(memo.contains("d:0"));
        for (int i = 0; i < 100; i++) {
            String k = "x" + i + ":0";
            memo.get(k, () -> fake(k));
        }
        assertEquals(3, memo.size(), "never grows past the bound");
        assertEquals(3, derivations.get());
        assertThrows(IllegalArgumentException.class, () -> new Memo(0));
    }

    @Test
    @DisplayName("memo: forgetPrefix drops one wallet (all indexes) and leaves the others")
    void forgetOneWallet() {
        Memo memo = new Memo(8);
        for (String k : new String[]{"aa:0", "aa:1", "aab:0", "bb:0"}) {
            memo.get(k, () -> fake(k));
        }
        memo.forgetPrefix("aa:");
        assertFalse(memo.contains("aa:0"));
        assertFalse(memo.contains("aa:1"));
        assertTrue(memo.contains("aab:0"), "prefix includes the ':' separator");
        assertTrue(memo.contains("bb:0"));
        memo.forgetAll();
        assertEquals(0, memo.size());
    }

    @Test
    @DisplayName("memo: a forget DURING a derivation wins — the result is returned but not memoized")
    void forgetDuringDerivationIsNotUndone() {
        Memo memo = new Memo(8);
        Derived d = memo.get("a:0", () -> {
            memo.forgetAll(); // the vault locks while the key is being derived
            return fake("late");
        });
        assertEquals("late", d.encKeyHash(), "the caller still gets its key");
        assertFalse(memo.contains("a:0"), "a key derived across a lock must not be held while locked");

        Derived e = memo.get("b:0", () -> {
            memo.forgetPrefix("zz:"); // a wallet removal: any forget voids derivations in flight
            return fake("late2");
        });
        assertEquals("late2", e.encKeyHash());
        assertFalse(memo.contains("b:0"));

        // the next derivation, in the new epoch, is memoized normally
        memo.get("a:0", () -> fake("again"));
        assertTrue(memo.contains("a:0"));
    }

    @Test
    @DisplayName("memo: concurrent callers of one key derive once; a failed derivation is not memoized")
    void oneDerivationPerKey() throws Exception {
        Memo memo = new Memo(8);
        AtomicInteger derivations = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Future<Derived> first = pool.submit(() -> memo.get("a:0", () -> {
                derivations.incrementAndGet();
                started.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    throw new IllegalStateException(ex);
                }
                return fake("one");
            }));
            assertTrue(started.await(30, TimeUnit.SECONDS));
            Future<Derived> second = pool.submit(() -> memo.get("a:0", () -> {
                derivations.incrementAndGet();
                return fake("two");
            }));
            Future<Derived> third = pool.submit(() -> memo.get("a:0", () -> {
                derivations.incrementAndGet();
                return fake("three");
            }));
            Thread.sleep(200);
            release.countDown();
            Derived a = first.get(30, TimeUnit.SECONDS);
            assertSame(a, second.get(30, TimeUnit.SECONDS));
            assertSame(a, third.get(30, TimeUnit.SECONDS));
            assertEquals(1, derivations.get());
        } finally {
            pool.shutdownNow();
        }

        assertThrows(IllegalStateException.class, () -> memo.get("bad:0", () -> {
            throw new IllegalStateException("derivation failed");
        }));
        assertFalse(memo.contains("bad:0"));
        assertEquals("ok", memo.get("bad:0", () -> fake("ok")).encKeyHash(), "a failure is retried, not cached");
    }
}
