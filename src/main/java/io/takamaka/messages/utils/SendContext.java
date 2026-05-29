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
package io.takamaka.messages.utils;

import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import java.util.function.Function;

/**
 * The five recurring infrastructure parameters every
 * {@code ChatCryptoUtils.get*MessageBean} helper needs. Passed as the first
 * argument to each helper; action-specific parameters follow.
 *
 * @param signingWallet the Ed25519 signing keystore
 * @param keyIndex the key index within the keystore
 * @param conversationHashName the conversation this message belongs to
 * @param conversationEncryptionKey base64url of the AES-256 symmetric key
 * @param keyDerivationFn optional custom key-derivation function; {@code null}
 *        selects the default derivation
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public record SendContext(
        InstanceWalletKeystoreInterface signingWallet,
        int keyIndex,
        String conversationHashName,
        String conversationEncryptionKey,
        Function<String, byte[]> keyDerivationFn) {

    /**
     * Convenience constructor for the common case of the default key
     * derivation.
     */
    public SendContext(InstanceWalletKeystoreInterface signingWallet, int keyIndex,
            String conversationHashName, String conversationEncryptionKey) {
        this(signingWallet, keyIndex, conversationHashName, conversationEncryptionKey, null);
    }
}
