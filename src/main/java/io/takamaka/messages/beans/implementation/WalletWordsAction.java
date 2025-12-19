/*
 * Copyright 2024 AiliA SA.
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
package io.takamaka.messages.beans.implementation;

import io.takamaka.messages.beans.MessageAction;

/**
 * Action for exporting wallet words (unencrypted mnemonic for cloud backup).
 *
 * <p>This action exports the 25-word mnemonic phrase and cypher type
 * needed to fully recover a wallet. Unlike {@link WalletEncryptedAction},
 * this exports the wallet in plaintext without password protection.</p>
 *
 * <p><b>SECURITY WARNING:</b> This action exposes sensitive wallet data.
 * It should only be used when the user has explicitly accepted a privacy
 * disclaimer acknowledging the risks of unencrypted cloud storage.</p>
 *
 * <p>Use cases:</p>
 * <ul>
 *   <li>Cloud backup to linked account (Google Drive, iCloud)</li>
 *   <li>Testing cloud support functionality</li>
 *   <li>Users who prioritize convenience over privacy</li>
 * </ul>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @see WalletWordsBean
 * @see WalletEncryptedAction
 */
public class WalletWordsAction extends MessageAction {

    /**
     * Creates a wallet words export action.
     *
     * @param walletWords the wallet words bean containing cypher and mnemonic
     */
    public WalletWordsAction(WalletWordsBean walletWords) {
        super.setDate(null);
        super.setFrom(null);
        super.setTextMessage(null);
        super.setTo(null);
        super.setGreen(null);
        super.setRed(null);
        super.setEncodedWallet(null);
        super.setWalletWords(walletWords);
    }

}
