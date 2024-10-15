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
    /**
     * notBefore field of the transaction if required
     */
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
