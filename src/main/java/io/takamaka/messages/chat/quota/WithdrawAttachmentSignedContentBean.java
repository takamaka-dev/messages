/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.chat.quota;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Signed payload of {@code withdrawattachment} (DR-037): the OWNER stops hosting one or more of its
 * own blobs on the server to free its quota. A quota command, not a content command: the message
 * that references the blob is untouched, nothing is fanned out, no client is asked to delete a local
 * copy, and a forwarded copy is somebody else's blob.
 *
 * <p>{@code efh} is the list of encrypted-file hashes (64-char lowercase hex, the
 * {@code verified_attachment} key) to withdraw; the server credits the owner's quota by each row's
 * stored size and keeps the row in a withdrawn state, so the same ciphertext can be re-hosted by the
 * owner later. {@code cts} is the client clock, window-checked by the server like every self-read.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WithdrawAttachmentSignedContentBean {

    /** Encrypted-file hashes to stop hosting; each must be owned by the signer. */
    @JsonProperty("efh")
    private List<String> encryptedFileHashes;

    /** Client clock at signing time, epoch millis. */
    @JsonProperty("cts")
    private Long clientTimestamp;
}
