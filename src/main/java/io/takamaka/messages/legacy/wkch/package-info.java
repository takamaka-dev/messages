/**
 * LEGACY — bug-compatible reproduction of the wallet app's pre-F340 RSA-4096 chat invite key ("WKCH" key).
 *
 * <h2>What the variant is</h2>
 * <p>Until build 46 the wallet app SDK ({@code takamaka-sdk-wrap}, commit {@code 1a1b245},
 * {@code lib/crypto/tkm_chat_rsa.dart} {@code _generateFromWalletSeedBlocking} +
 * {@code lib/crypto/tkm_seeded_random.dart}) derived the RSA-4096 encryption key it registered on rschat
 * from the wallet seed with:</p>
 * <ul>
 *   <li>the SAME seeded PRNG as {@code wallet-core} {@code SeededRandom} (each {@code nextBytes(n)} call =
 *       PBKDF2-HMAC-SHA512(password = seed, then seed + decimal call counter from the 2nd call on;
 *       salt = scope; iterations = index + 1; n bytes)),</li>
 *   <li>but the WRONG scope: {@code "__WKCH__"} (the Ed25519 signing key chain) instead of
 *       {@code "__RSA_PK_ENCRYPTION__"},</li>
 *   <li>and PointyCastle 3.9.1 {@code RSAKeyGenerator} (65537, 4096 bits, certainty 64) instead of the
 *       BouncyCastle {@code RSAKeyPairGenerator} with certainty 1 that {@code wallet-core}
 *       {@code InstanceWalletKeyStoreBCRSA4096ENC(256)} uses.</li>
 * </ul>
 * <p>So the same 25 words gave the same Ed25519 identity but a DIFFERENT encryption key in the wallet app
 * than in every other client (F340 = Iris F220; evidence {@code rschat-docs/analysis/SDK_WRAP_RSCHAT_PARITY_2026-09-25.md}
 * §4.1, vectors {@code rschat-docs/security/vectors/chat_rsa_invite_key_vectors.json} scheme {@code legacy_wkch}).
 * Conversations created while such a key was the registered one carry an invite ({@code enc_key}) wrapped to it.</p>
 *
 * <h2>What this package is for — DECRYPT-ONLY</h2>
 * <p>It regenerates that legacy keypair deterministically from the seed, so a Java client (shell,
 * chat-web-gui, anything over rsclient) can UNWRAP an invite whose {@code enc_key_hash} equals the legacy
 * key's hash. Selection is by {@code enc_key_hash}, never by trial decryption. The legacy key is never
 * registered, never used to encrypt, never used to sign, never promoted to the parity key: the public API
 * ({@link io.takamaka.messages.legacy.wkch.LegacyWkchRsaKeyDerivation#derivePublicKeyHash} and
 * {@link io.takamaka.messages.legacy.wkch.LegacyWkchRsaKeyDerivation#deriveKeyPair}, plus the selector
 * {@link io.takamaka.messages.legacy.wkch.LegacyWkchInviteKeySelector}) offers no path to any of those.
 * New code must not use it for anything else.</p>
 *
 * <p>The prime search is a byte-exact port of PointyCastle 3.9.1 {@code lib/key_generators/rsa_key_generator.dart}
 * (candidate = {@code nextBigInteger(2048)} big-endian from ONE {@code nextBytes(256)} call, MSB forced,
 * made odd, +2 walk with fixed-base Miller-Rabin over the first ⌈t/2⌉ small primes, which draws NO
 * randomness). It is pinned by the known-answer test {@code LegacyWkchRsaKeyDerivationVectorTest}.</p>
 *
 * <h2>Sunset</h2>
 * <p>Remove this package when no invite wrapped to a WKCH key remains on any server (every conversation
 * created before the wallet app's build 47 has been deleted or retention-swept on TEST and PROD).</p>
 *
 * @since Messages 1.12.0 (F340, 2026-09-25)
 */
package io.takamaka.messages.legacy.wkch;
