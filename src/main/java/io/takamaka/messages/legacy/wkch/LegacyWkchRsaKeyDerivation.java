package io.takamaka.messages.legacy.wkch;

import io.takamaka.wallet.utils.TkmSignUtils;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.util.encoders.UrlBase64;

/**
 * LEGACY (F340, DECRYPT-ONLY — see {@code package-info}). Regenerates the RSA-4096 invite key the wallet
 * app SDK (takamaka-sdk-wrap {@code 1a1b245}, {@code lib/crypto/tkm_chat_rsa.dart:249-263}) derived from a
 * wallet seed: scope {@code "__WKCH__"}, keyNumber {@code index + 1}, PointyCastle 3.9.1
 * {@code RSAKeyGenerator(65537, 4096, certainty 64)}.
 *
 * <p>The key API is exactly two methods — {@link #derivePublicKeyHash} (for selection by
 * {@code enc_key_hash}) and {@link #deriveKeyPair} (for the unwrap). There is deliberately no method that
 * returns an encodable public key for registration, encrypts, or signs.</p>
 *
 * <p>Derivations are memoized in memory per (SHA-256 of the seed, index) — never persisted, never keyed by
 * the seed itself — in a process-wide LRU of at most {@value #MAX_CACHED_KEYS} keypairs.</p>
 *
 * <h2>The memo holds PRIVATE keys — callers must forget them</h2>
 * <p>The memo is static, so without help it outlives the vault session that produced it. Every client that
 * calls this class (directly or through {@link LegacyWkchInviteKeySelector}) MUST call:</p>
 * <ul>
 *   <li>{@link #forgetAll()} on <b>vault lock</b> (and on logout / shutdown of the key-holding session);</li>
 *   <li>{@link #forget(String)} — or {@link #forgetAll()} — on <b>wallet removal</b>.</li>
 * </ul>
 * <p>This mirrors the Dart selector's {@code clearMemory()} (rsclient-flutter
 * {@code legacy_wkch_invite_key_selector.dart}), including its epoch rule: a derivation still running when
 * a forget happens returns its result to its caller but is NOT memoized, so a lock during a derivation
 * cannot leave the key behind. Forgetting drops the references; the JVM offers no way to zero the
 * {@code BigInteger}s, so the bytes live until garbage-collected. The only cost of forgetting is one
 * re-derivation (seconds) on the next legacy invite.</p>
 *
 * <p>Sunset: remove with the package when no invite wrapped to a WKCH key remains on any server.</p>
 */
public final class LegacyWkchRsaKeyDerivation {

    /** The (wrong) scope the SDK used: the Ed25519 signing key chain's. */
    static final String SCOPE = "__WKCH__";
    static final int STRENGTH = 4096;
    static final int CERTAINTY = 64;
    static final BigInteger PUBLIC_EXPONENT = BigInteger.valueOf(65537);

    /**
     * Upper bound of memoized keypairs (LRU). An identity costs one entry and only identities that actually
     * hold a legacy-wrapped invite ever derive; past the bound the least recently used pair is forgotten and
     * re-derived on demand.
     */
    public static final int MAX_CACHED_KEYS = 16;

    private static final Memo CACHE = new Memo(MAX_CACHED_KEYS);

    private LegacyWkchRsaKeyDerivation() {
    }

    /**
     * The {@code enc_key_hash} an invite wrapped to this seed's legacy WKCH key at {@code index} carries:
     * SHA3-256 Base64URL of the key's X.509 SubjectPublicKeyInfo in Base64URL ('.' padding), the same form
     * {@code ChatCryptoUtils} hashes for every invite.
     *
     * @param seed the wallet seed (the {@code KeyBean} seed string)
     * @param index the wallet index (keyNumber = index + 1)
     * @return the legacy key's enc_key_hash
     */
    public static String derivePublicKeyHash(String seed, int index) {
        return derived(seed, index).encKeyHash;
    }

    /**
     * The legacy keypair itself — for UNWRAPPING an invite selected by {@link #derivePublicKeyHash}. Never
     * register, encrypt to, or promote it.
     *
     * @param seed the wallet seed
     * @param index the wallet index
     * @return public = {@link RSAKeyParameters}, private = {@link RSAPrivateCrtKeyParameters}
     */
    public static AsymmetricCipherKeyPair deriveKeyPair(String seed, int index) {
        return derived(seed, index).keyPair;
    }

    /**
     * Forgets every memoized legacy keypair. <b>Call on vault lock</b> (and logout / shutdown); see the
     * class comment. Derivations in flight are not cancelled — their result is returned to their caller
     * and not memoized. Idempotent, never throws.
     */
    public static void forgetAll() {
        CACHE.forgetAll();
    }

    /**
     * Forgets every memoized legacy keypair of one wallet seed (all indexes). <b>Call on wallet
     * removal</b> when the vault stays unlocked. Derivations in flight (of any seed) are not memoized.
     * A null or empty seed forgets nothing. Never throws.
     *
     * @param seed the wallet seed (the {@code KeyBean} seed string)
     */
    public static void forget(String seed) {
        if (seed == null || seed.isEmpty()) {
            return;
        }
        CACHE.forgetPrefix(seedPrefix(seed));
    }

    /** Whether {@code (seed, index)} is already memoized. */
    static boolean isCached(String seed, int index) {
        return CACHE.contains(cacheKey(seed, index));
    }

    /** How many keypairs are memoized right now. */
    static int cachedKeys() {
        return CACHE.size();
    }

    /**
     * True exactly once per memoized derivation: the first UNWRAP that uses a freshly regenerated keypair
     * ("regenerated" in the log line); every later unwrap reuses it ("cached"). Selection alone (which also
     * derives) does not consume it, so select-then-unwrap still reports "regenerated".
     */
    static boolean claimFirstUse(String seed, int index) {
        return derived(seed, index).firstUse.compareAndSet(false, true);
    }

    /** The public key in the Takamaka wire form (X.509, Base64URL, '.' padding). Package-private. */
    static String publicKeyUrl64(RSAKeyParameters pub) {
        try {
            PublicKey key = KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(pub.getModulus(), pub.getExponent()));
            return new String(UrlBase64.encode(key.getEncoded()), StandardCharsets.US_ASCII);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("legacy WKCH: cannot encode the public key", ex);
        }
    }

    /**
     * The ONE memo lookup of an operation. A caller that needs more than one of {hash, key pair, first-use
     * flag} takes the record once and uses it throughout: a second lookup after a {@link #forgetAll()}
     * would miss, re-derive in the NEW epoch and put the private key back in the memo after the lock.
     */
    static Derived derived(String seed, int index) {
        if (seed == null || seed.isEmpty()) {
            throw new IllegalArgumentException("legacy WKCH: empty seed");
        }
        if (index < 0) {
            throw new IllegalArgumentException("legacy WKCH: negative index " + index);
        }
        return CACHE.get(cacheKey(seed, index), () -> derive(seed, index));
    }

    private static Derived derive(String seed, int index) {
        AsymmetricCipherKeyPair kp = generateKeyPair(new LegacyWkchSeededRandom(seed, SCOPE, index + 1));
        String url64 = publicKeyUrl64((RSAKeyParameters) kp.getPublic());
        try {
            return new Derived(kp, TkmSignUtils.Hash256B64URL(url64), new AtomicBoolean(false));
        } catch (Exception ex) {
            throw new IllegalStateException("legacy WKCH: cannot hash the public key", ex);
        }
    }

    private static String cacheKey(String seed, int index) {
        return seedPrefix(seed) + index;
    }

    /** {@code hex(SHA-256(seed)) + ":"} — the part of the memo key that names the wallet. */
    private static String seedPrefix(String seed) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h) + ":";
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    record Derived(AsymmetricCipherKeyPair keyPair, String encKeyHash, AtomicBoolean firstUse) {
    }

    /**
     * The bounded memo: an access-ordered LRU of at most {@code maxEntries} derivations, with one
     * derivation per key at a time (concurrent callers of the same key wait for the one running, as the
     * former {@code computeIfAbsent} did) and the forget epoch described in the class comment. The
     * derivation itself runs OUTSIDE the lock: it takes seconds and must not stall lookups or a forget.
     */
    static final class Memo {

        private final int maxEntries;
        private final Object lock = new Object();
        private final LinkedHashMap<String, Derived> entries;
        private final Map<String, FutureTask<Derived>> inFlight = new ConcurrentHashMap<>();
        /** Bumped by every forget. A derivation that started in an earlier epoch is not memoized. */
        private long epoch;

        Memo(int maxEntries) {
            if (maxEntries < 1) {
                throw new IllegalArgumentException("maxEntries " + maxEntries);
            }
            this.maxEntries = maxEntries;
            this.entries = new LinkedHashMap<>(16, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Derived> eldest) {
                    return size() > Memo.this.maxEntries;
                }
            };
        }

        Derived get(String key, Supplier<Derived> deriver) {
            final long startedIn;
            synchronized (lock) {
                Derived hit = entries.get(key);
                if (hit != null) {
                    return hit;
                }
                startedIn = epoch;
            }
            FutureTask<Derived> mine = new FutureTask<>(deriver::get);
            FutureTask<Derived> running = inFlight.putIfAbsent(key, mine);
            if (running != null) {
                // Someone else is deriving this key: share the result, memoization is theirs to do.
                return await(running);
            }
            try {
                synchronized (lock) {
                    // A derivation of this key may have finished between the miss and the claim.
                    Derived hit = entries.get(key);
                    if (hit != null && epoch == startedIn) {
                        return hit;
                    }
                }
                mine.run();
                Derived derived = await(mine);
                synchronized (lock) {
                    if (epoch == startedIn) {
                        entries.put(key, derived);
                    }
                }
                return derived;
            } finally {
                inFlight.remove(key, mine);
            }
        }

        boolean contains(String key) {
            synchronized (lock) {
                return entries.containsKey(key);
            }
        }

        int size() {
            synchronized (lock) {
                return entries.size();
            }
        }

        void forgetAll() {
            synchronized (lock) {
                epoch++;
                entries.clear();
            }
        }

        void forgetPrefix(String prefix) {
            synchronized (lock) {
                epoch++;
                entries.keySet().removeIf(k -> k.startsWith(prefix));
            }
        }

        private static Derived await(FutureTask<Derived> task) {
            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        return task.get();
                    } catch (InterruptedException ex) {
                        interrupted = true;
                    } catch (ExecutionException ex) {
                        if (ex.getCause() instanceof RuntimeException re) {
                            throw re;
                        }
                        throw new IllegalStateException(ex.getCause());
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // PointyCastle 3.9.1 lib/key_generators/rsa_key_generator.dart — ported line for line.
    // ------------------------------------------------------------------------------------------------

    /** {@code RSAKeyGenerator.generateKeyPair()} with bitStrength 4096, certainty 64, e = 65537. */
    static AsymmetricCipherKeyPair generateKeyPair(LegacyWkchSeededRandom random) {
        BigInteger p;
        BigInteger q;
        BigInteger n;
        final BigInteger e = PUBLIC_EXPONENT;
        final int strength = STRENGTH;
        final int pbitlength = (strength + 1) / 2;
        final int qbitlength = strength - pbitlength;
        final int mindiffbits = strength / 3;

        // generate p, prime and (p-1) relatively prime to e
        while (true) {
            p = generateProbablePrime(pbitlength, 1, random);
            if (p.mod(e).equals(BigInteger.ONE)) {
                continue;
            }
            if (!isProbablePrime(p, CERTAINTY)) {
                continue;
            }
            if (e.gcd(p.subtract(BigInteger.ONE)).equals(BigInteger.ONE)) {
                break;
            }
        }

        // generate a modulus of the required length
        while (true) {
            // generate q, prime and (q-1) relatively prime to e, and not equal to p
            while (true) {
                q = generateProbablePrime(qbitlength, 1, random);
                if (q.subtract(p).abs().bitLength() < mindiffbits) {
                    continue;
                }
                if (q.mod(e).equals(BigInteger.ONE)) {
                    continue;
                }
                if (!isProbablePrime(q, CERTAINTY)) {
                    continue;
                }
                if (e.gcd(q.subtract(BigInteger.ONE)).equals(BigInteger.ONE)) {
                    break;
                }
            }
            n = p.multiply(q);
            if (n.bitLength() == strength) {
                break;
            }
            // our primes aren't big enough, make the largest of the two p and try again
            p = (p.compareTo(q) > 0) ? p : q;
        }

        // Swap p and q if necessary
        if (p.compareTo(q) < 0) {
            BigInteger swap = p;
            p = q;
            q = swap;
        }

        // PointyCastle: d = e^-1 mod (p-1)(q-1)  (NOT lcm — differs from BouncyCastle; kept as is)
        BigInteger pSub1 = p.subtract(BigInteger.ONE);
        BigInteger qSub1 = q.subtract(BigInteger.ONE);
        BigInteger phi = pSub1.multiply(qSub1);
        BigInteger d = e.modInverse(phi);

        RSAKeyParameters pub = new RSAKeyParameters(false, n, e);
        RSAPrivateCrtKeyParameters priv = new RSAPrivateCrtKeyParameters(n, e, d, p, q,
                d.mod(pSub1), d.mod(qSub1), q.modInverse(p));
        return new AsymmetricCipherKeyPair(pub, priv);
    }

    /** PointyCastle's 509-bounded low-prime table (the first 97 primes). */
    private static final int[] LOW_PRIMES = {
        2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97,
        101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173, 179, 181, 191, 193, 197,
        199, 211, 223, 227, 229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281, 283, 293, 307, 311, 313,
        317, 331, 337, 347, 349, 353, 359, 367, 373, 379, 383, 389, 397, 401, 409, 419, 421, 431, 433, 439,
        443, 449, 457, 461, 463, 467, 479, 487, 491, 499, 503, 509
    };
    private static final BigInteger LAST_LOW_PRIME = BigInteger.valueOf(509);
    private static final BigInteger LPLIM = BigInteger.ONE.shiftLeft(26).divide(LAST_LOW_PRIME);
    private static final BigInteger TWO = BigInteger.valueOf(2);

    /** {@code generateProbablePrime(bitLength, certainty, rnd)}. */
    static BigInteger generateProbablePrime(int bitLength, int certainty, LegacyWkchSeededRandom rnd) {
        if (bitLength < 2) {
            return BigInteger.ONE;
        }
        BigInteger candidate = rnd.nextBigInteger(bitLength);
        // force MSB set
        if (!candidate.testBit(bitLength - 1)) {
            candidate = candidate.or(BigInteger.ONE.shiftLeft(bitLength - 1));
        }
        // force odd
        if (!candidate.testBit(0)) {
            candidate = candidate.add(BigInteger.ONE);
        }
        while (!isProbablePrime(candidate, certainty)) {
            candidate = candidate.add(TWO);
            if (candidate.bitLength() > bitLength) {
                candidate = candidate.subtract(BigInteger.ONE.shiftLeft(bitLength - 1));
            }
        }
        return candidate;
    }

    /** {@code _isProbablePrime(b, t)}: low-prime table, trial division by 3..509, then {@link #millerRabin}. */
    static boolean isProbablePrime(BigInteger b, int t) {
        BigInteger x = b.abs();
        if (b.compareTo(LAST_LOW_PRIME) <= 0) {
            for (int lp : LOW_PRIMES) {
                if (b.equals(BigInteger.valueOf(lp))) {
                    return true;
                }
            }
            return false;
        }
        if (!x.testBit(0)) {
            return false;
        }
        int i = 1;
        while (i < LOW_PRIMES.length) {
            BigInteger m = BigInteger.valueOf(LOW_PRIMES[i]);
            int j = i + 1;
            while (j < LOW_PRIMES.length && m.compareTo(LPLIM) < 0) {
                m = m.multiply(BigInteger.valueOf(LOW_PRIMES[j++]));
            }
            m = x.mod(m);
            while (i < j) {
                if (m.mod(BigInteger.valueOf(LOW_PRIMES[i++])).signum() == 0) {
                    return false;
                }
            }
        }
        return millerRabin(x, t);
    }

    /** {@code _millerRabin(b, t)}: HAC 4.24 with the FIXED bases LOW_PRIMES[0 .. (t+1)/2 - 1]; no randomness. */
    static boolean millerRabin(BigInteger b, int t) {
        BigInteger n1 = b.subtract(BigInteger.ONE);
        int k = n1.signum() == 0 ? -1 : n1.getLowestSetBit();
        if (k <= 0) {
            return false;
        }
        BigInteger r = n1.shiftRight(k);
        t = (t + 1) >> 1;
        if (t > LOW_PRIMES.length) {
            t = LOW_PRIMES.length;
        }
        for (int i = 0; i < t; ++i) {
            BigInteger a = BigInteger.valueOf(LOW_PRIMES[i]);
            BigInteger y = a.modPow(r, b);
            if (y.compareTo(BigInteger.ONE) != 0 && y.compareTo(n1) != 0) {
                int j = 1;
                while (j++ < k && y.compareTo(n1) != 0) {
                    y = y.modPow(TWO, b);
                    if (y.compareTo(BigInteger.ONE) == 0) {
                        return false;
                    }
                }
                if (y.compareTo(n1) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Hex of the modulus — test convenience. */
    static String modulusHex(AsymmetricCipherKeyPair kp) {
        return ((RSAKeyParameters) kp.getPublic()).getModulus().toString(16);
    }
}
