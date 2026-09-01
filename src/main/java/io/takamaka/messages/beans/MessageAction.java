/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package io.takamaka.messages.beans;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.beans.implementation.WalletWordsBean;
import io.takamaka.wallet.beans.EncKeyBean;
import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 *
 * <ul>
 * <li><b>Pay Request</b> request a pay <i> (v1.0)</i></li>
 * <li><b>Re</b> blob <i> (v1.0)</i></li>
 * <li><b>rp</b> request pay <i> (v1.0)</i></li>
 * <li><b>st</b> stake to node <i> (v1.0)</i></li>
 * <li><b>su</b> stake undo <i> (v1.0)</i></li>
 * <li><b>we</b> wallet encrypted <i> (v1.0)</i></li>
 * <li><b>ww</b> wallet words (unencrypted mnemonic for cloud backup) <i> (v1.0)</i></li>
 * </ul>
 *
 *
 * @author giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@EqualsAndHashCode
public abstract class MessageAction {

    @JsonProperty("fr")
    @EqualsAndHashCode.Include
    private MessageAddress from;
    @JsonProperty("to")
    @EqualsAndHashCode.Include
    private MessageAddress to;
    @JsonProperty("dt")
    @EqualsAndHashCode.Include
    private Long date;
    /**
     * Green token amount in nano TKG.
     *
     * <p><b>Serialised as a JSON STRING, not a bare number</b> (DR-034,
     * 2026-09-01). JSON's number type is underspecified and, in practice,
     * bounded by the consumer's native numeric type: a bare
     * {@code BigInteger} is exact here only because Jackson's parser retains
     * the token's literal text. Consumers whose JSON parser discards that text
     * cannot recover a large value at all &mdash; Dart's {@code jsonDecode}
     * yields a lossy {@code double} above 2^63 on the VM and above 2^53 on the
     * web, silently. A decimal string is exact on every platform, which is why
     * this is the wire form.
     *
     * <p><b>Readers must accept BOTH forms, permanently.</b> Jackson coerces a
     * bare number into this field without configuration, so no reader change
     * was needed when this flipped; payloads minted before DR-034 still parse.
     * Producers emit the string form only.
     *
     * <p>⚠️ This field is <b>signature-covered</b>: the signed bytes are
     * {@code SimpleRequestHelper.getRequestJsonCompact(action)}, so changing
     * its rendering changes what verifies. Do not change it again without a
     * decision record.
     */
    @JsonProperty("g")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @EqualsAndHashCode.Include
    private BigInteger green;
    /**
     * Red token amount in nano TKR. Serialised as a JSON string on the same
     * terms as {@link #green} &mdash; see that field for the reasoning.
     */
    @JsonProperty("r")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @EqualsAndHashCode.Include
    private BigInteger red;
    @JsonProperty("tm")
    @EqualsAndHashCode.Include
    private String textMessage;
    @JsonProperty("ew")
    private EncKeyBean encodedWallet;
    @JsonProperty("ww")
    private WalletWordsBean walletWords;

}
