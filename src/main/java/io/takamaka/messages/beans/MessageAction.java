/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package io.takamaka.messages.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("g")
    @EqualsAndHashCode.Include
    private BigInteger green;
    @JsonProperty("r")
    @EqualsAndHashCode.Include
    private BigInteger red;
    @JsonProperty("tm")
    @EqualsAndHashCode.Include
    private String textMessage;
    @JsonProperty("ew")
    private EncKeyBean encodedWallet;

}
