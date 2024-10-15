/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package io.takamaka.messages.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * @author giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class MessageAction {

    @JsonProperty("fr")
    private MessageAddress from;
    @JsonProperty("to")
    private MessageAddress to;
    /**
     * notBefore field of the transaction if required
     */
    @JsonProperty("dt")
    private Long date;
    @JsonProperty("g")
    private BigInteger green;
    @JsonProperty("r")
    private BigInteger red;
    @JsonProperty("tm")
    private String textMessage;

}
