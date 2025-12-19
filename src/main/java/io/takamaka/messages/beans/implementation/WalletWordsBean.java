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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.wallet.utils.KeyContexts;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bean for unencrypted wallet words export (cloud backup).
 *
 * <p>This bean contains the 25-word mnemonic phrase and cypher type
 * needed to recover a wallet. It is used for cloud backup scenarios
 * where users accept the privacy tradeoff for convenience.</p>
 *
 * <p><b>WARNING:</b> This exports sensitive wallet data in plaintext.
 * Users must explicitly accept privacy disclaimer before use.</p>
 *
 * <p>JSON format (v1.0):</p>
 * <pre>{@code
 * {
 *   "version": "1.0",
 *   "cypher": "Ed25519BC",
 *   "words": "word1 word2 ... word25"
 * }
 * }</pre>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class WalletWordsBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Bean version for backward compatibility tracking.
     */
    @JsonProperty("version")
    private String version = "1.0";

    /**
     * The wallet cypher type (e.g., Ed25519BC, BCQTESLA_PS_1).
     * Required to know which key derivation algorithm to use on recovery.
     */
    @JsonProperty("cypher")
    private KeyContexts.WalletCypher cypher;

    /**
     * The 25-word mnemonic phrase, space-separated.
     * This is the secret that allows full wallet recovery.
     */
    @JsonProperty("words")
    private String words;

    /**
     * Creates a WalletWordsBean with the specified cypher and words.
     * Uses default version "1.0".
     *
     * @param cypher the wallet cypher type
     * @param words the 25-word mnemonic phrase (space-separated)
     */
    public WalletWordsBean(KeyContexts.WalletCypher cypher, String words) {
        this.cypher = cypher;
        this.words = words;
    }
}
