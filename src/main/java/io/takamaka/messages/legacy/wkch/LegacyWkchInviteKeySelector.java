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
        String declared = invite == null ? null : invite.getEncryptionKeyHash();
        if (declared == null || declared.isEmpty()) {
            return Selection.UNKNOWN;
        }
        try {
            if (parityPublicKeyUrl64 != null && declared.equals(TkmSignUtils.Hash256B64URL(parityPublicKeyUrl64))) {
                return Selection.PARITY;
            }
        } catch (Exception ex) {
            log.warn("chat[invite] cannot hash the parity key: {}", ex.toString());
        }
        if (seed == null || seed.isEmpty()) {
            return Selection.UNKNOWN;
        }
        return declared.equals(LegacyWkchRsaKeyDerivation.derivePublicKeyHash(seed, index))
                ? Selection.LEGACY_WKCH
                : Selection.UNKNOWN;
    }

    /**
     * Unwraps an invite that {@link #select} returned {@link Selection#LEGACY_WKCH} for, with the legacy key
     * (RSA OAEP SHA-256/MGF1-SHA-256, both base64 alphabets — the same decrypt as the parity key). Logs
     * {@code chat[invite] key=legacy_wkch (regenerated|cached)}.
     *
     * @param invite the invite
     * @param seed the wallet seed
     * @param index the wallet index
     * @return the conversation symmetric key
     * @throws IllegalStateException when the invite was not selected as legacy or does not unwrap
     */
    public static String unwrapLegacy(TopicKeyDistributionItemBean invite, String seed, int index) {
        String legacyHash = LegacyWkchRsaKeyDerivation.derivePublicKeyHash(seed, index);
        if (invite == null || !legacyHash.equals(invite.getEncryptionKeyHash())) {
            // Never trial: a legacy unwrap is only ever attempted for an invite that NAMES the legacy key.
            throw new IllegalStateException("legacy WKCH unwrap refused: the invite's enc_key_hash does not name the legacy key");
        }
        try {
            String key = TkmCypherProviderBCRSA4096ENC256.decryptToString(
                    TkmSignUtils.asymmetricKeyParameterToRSAPrivateKey(
                            LegacyWkchRsaKeyDerivation.deriveKeyPair(seed, index).getPrivate()),
                    invite.getEncryptedTopicKey());
            log.info("chat[invite] key=legacy_wkch ({})",
                    LegacyWkchRsaKeyDerivation.claimFirstUse(seed, index) ? "regenerated" : "cached");
            return key;
        } catch (Exception ex) {
            throw new IllegalStateException("legacy WKCH unwrap failed: " + ex, ex);
        }
    }
}
