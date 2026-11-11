package io.takamaka.messages.legacy.wkch;

import io.takamaka.messages.chat.conversation.TopicKeyDistributionItemBean;
import io.takamaka.wallet.TkmCypherProviderBCRSA4096ENC256;
import io.takamaka.wallet.utils.TkmSignUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * LEGACY (F340, DECRYPT-ONLY — see {@code package-info}). Chooses which of this identity's OWN keys an
 * invite was wrapped to, by its {@code enc_key_hash}, over the accepted set {parity key, legacy WKCH key},
 * and unwraps a legacy-selected invite.
 *
 * <p>Order: the parity hash is compared first (no derivation). Only an invite whose hash is NOT the parity
 * hash costs a legacy derivation (once per seed and index, memoized). A hash that matches neither is
 * {@link Selection#UNKNOWN}: the caller keeps its existing path (which fails for a foreign key exactly as
 * before — nothing is weakened). No trial decryption anywhere.</p>
 *
 * <p>Sunset: remove with the package when no invite wrapped to a WKCH key remains on any server.</p>
 */
@Slf4j
public final class LegacyWkchInviteKeySelector {

    /** Which own key an invite's {@code enc_key_hash} names. */
    public enum Selection {
        /** The parity (Java-reference) key — the caller's existing decrypt. */
        PARITY,
        /** The wallet app's pre-F340 WKCH key — unwrap with {@link #unwrapLegacy}. */
        LEGACY_WKCH,
        /** Neither — the caller's existing path (older key / foreign key) applies unchanged. */
        UNKNOWN
    }

    private LegacyWkchInviteKeySelector() {
    }

    /**
     * Selection by {@code enc_key_hash}.
     *
     * @param invite this identity's invite from the topic's invitation list
     * @param parityPublicKeyUrl64 this identity's parity RSA public key (what it registers)
     * @param seed the wallet seed, or null when the caller cannot provide it (then LEGACY_WKCH is never
     * selected)
     * @param index the wallet index of the identity
     * @return the selection
     */
    public static Selection select(TopicKeyDistributionItemBean invite, String parityPublicKeyUrl64,
            String seed, int index) {
        return selectDerived(invite, parityPublicKeyUrl64, seed, index).selection;
    }

    /**
     * {@link #select} and, for a {@link Selection#LEGACY_WKCH} invite, {@link #unwrapLegacy} in ONE memo
     * lookup. Prefer it to calling the two in sequence: each of them looks the key up on its own, so a
     * {@code forgetAll()} (vault lock) landing between them makes the second one re-derive and memoize the
     * private key again after the lock.
     *
     * @param invite this identity's invite
     * @param parityPublicKeyUrl64 this identity's parity RSA public key
     * @param seed the wallet seed, or null (then LEGACY_WKCH is never selected)
     * @param index the wallet index of the identity
     * @return the selection, with the conversation key when it is {@link Selection#LEGACY_WKCH}
     * @throws IllegalStateException when a legacy-selected invite does not unwrap
     */
    public static Unwrapped selectAndUnwrapLegacy(TopicKeyDistributionItemBean invite, String parityPublicKeyUrl64,
            String seed, int index) {
        Selected selected = selectDerived(invite, parityPublicKeyUrl64, seed, index);
        if (selected.selection != Selection.LEGACY_WKCH) {
            return new Unwrapped(selected.selection, null);
        }
        return new Unwrapped(Selection.LEGACY_WKCH, unwrapWith(invite, selected.derived));
    }

    /**
     * The outcome of {@link #selectAndUnwrapLegacy}.
     *
     * @param selection which own key the invite names
     * @param conversationKey the unwrapped conversation key when {@code selection} is
     * {@link Selection#LEGACY_WKCH}; null otherwise (the caller's existing decrypt applies)
     */
    public record Unwrapped(Selection selection, String conversationKey) {

        /** Never prints the key. */
        @Override
        public String toString() {
            return "Unwrapped[" + selection + "]";
        }
    }

    /** A selection plus, when one was needed, the single memo record it was decided on. */
    private record Selected(Selection selection, LegacyWkchRsaKeyDerivation.Derived derived) {
    }

    private static Selected selectDerived(TopicKeyDistributionItemBean invite, String parityPublicKeyUrl64,
            String seed, int index) {
        String declared = invite == null ? null : invite.getEncryptionKeyHash();
        if (declared == null || declared.isEmpty()) {
            return new Selected(Selection.UNKNOWN, null);
        }
        try {
            if (parityPublicKeyUrl64 != null && declared.equals(TkmSignUtils.Hash256B64URL(parityPublicKeyUrl64))) {
                return new Selected(Selection.PARITY, null);
            }
        } catch (Exception ex) {
            log.warn("chat[invite] cannot hash the parity key: {}", ex.toString());
        }
        if (seed == null || seed.isEmpty()) {
            return new Selected(Selection.UNKNOWN, null);
        }
        LegacyWkchRsaKeyDerivation.Derived derived = lookUpOnce(seed, index);
        return declared.equals(derived.encKeyHash())
                ? new Selected(Selection.LEGACY_WKCH, derived)
                : new Selected(Selection.UNKNOWN, null);
    }

    /**
     * Unwraps an invite that {@link #select} returned {@link Selection#LEGACY_WKCH} for, with the legacy key
     * (RSA OAEP SHA-256/MGF1-SHA-256, both base64 alphabets — the same decrypt as the parity key). Logs
     * {@code chat[invite] key=legacy_wkch (regenerated|cached)}. The memo is looked up exactly once.
     *
     * @param invite the invite
     * @param seed the wallet seed
     * @param index the wallet index
     * @return the conversation symmetric key
     * @throws IllegalStateException when the invite was not selected as legacy or does not unwrap
     */
    public static String unwrapLegacy(TopicKeyDistributionItemBean invite, String seed, int index) {
        return unwrapWith(invite, lookUpOnce(seed, index));
    }

    /** Test hook: runs right after the single memo lookup of an operation (a vault lock landing mid-way). */
    static volatile Runnable afterLookupForTest;

    private static LegacyWkchRsaKeyDerivation.Derived lookUpOnce(String seed, int index) {
        LegacyWkchRsaKeyDerivation.Derived derived = LegacyWkchRsaKeyDerivation.derived(seed, index);
        Runnable hook = afterLookupForTest;
        if (hook != null) {
            hook.run();
        }
        return derived;
    }

    /** Hash check, decrypt and first-use flag all from the ONE record — no further memo lookup. */
    private static String unwrapWith(TopicKeyDistributionItemBean invite, LegacyWkchRsaKeyDerivation.Derived derived) {
        if (invite == null || !derived.encKeyHash().equals(invite.getEncryptionKeyHash())) {
            // Never trial: a legacy unwrap is only ever attempted for an invite that NAMES the legacy key.
            throw new IllegalStateException("legacy WKCH unwrap refused: the invite's enc_key_hash does not name the legacy key");
        }
        try {
            String key = TkmCypherProviderBCRSA4096ENC256.decryptToString(
                    TkmSignUtils.asymmetricKeyParameterToRSAPrivateKey(derived.keyPair().getPrivate()),
                    invite.getEncryptedTopicKey());
            log.info("chat[invite] key=legacy_wkch ({})",
                    derived.firstUse().compareAndSet(false, true) ? "regenerated" : "cached");
            return key;
        } catch (Exception ex) {
            throw new IllegalStateException("legacy WKCH unwrap failed: " + ex, ex);
        }
    }
}
