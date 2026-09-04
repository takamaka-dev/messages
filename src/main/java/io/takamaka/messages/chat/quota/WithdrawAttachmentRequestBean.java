/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.chat.quota;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.core.SignedMessageBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * {@code withdrawattachment} envelope (DR-037) — owner-signed, nonce-free, idempotent. Built by
 * {@code ChatCryptoUtils.getSignedWithdrawAttachmentRequest}; verified by
 * {@code ChatCryptoUtils.verifySignedMessage} (message type {@code WITHDRAW_ATTACHMENT}). The server
 * acts only on blobs whose {@code sender_pk} is {@code from}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WithdrawAttachmentRequestBean extends SignedMessageBean {

    public WithdrawAttachmentRequestBean(
            WithdrawAttachmentSignedContentBean pl,
            String from,
            String signature,
            String messageType,
            String signatureType) {
        super(from, signature, messageType, signatureType);
        this.pl = pl;
    }

    @JsonProperty("pl")
    private WithdrawAttachmentSignedContentBean pl;
}
