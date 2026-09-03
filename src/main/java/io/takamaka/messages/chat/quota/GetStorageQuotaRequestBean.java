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
 * {@code getstoragequota} envelope — signed, nonce-free self-read of the caller's storage quota.
 *
 * <p>Built by {@code ChatCryptoUtils.getSignedGetStorageQuotaRequest}; verified by
 * {@code ChatCryptoUtils.verifySignedMessage} (message type {@code GET_STORAGE_QUOTA}). The server
 * answers for {@code from} and nobody else: a quota is readable only by its assignee.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetStorageQuotaRequestBean extends SignedMessageBean {

    public GetStorageQuotaRequestBean(
            GetStorageQuotaSignedContentBean pl,
            String from,
            String signature,
            String messageType,
            String signatureType) {
        super(from, signature, messageType, signatureType);
        this.pl = pl;
    }

    @JsonProperty("pl")
    private GetStorageQuotaSignedContentBean pl;
}
