package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Fresh commit (spec §6.2). The commit HEADER is every field but {@code boxes}, {@code conf} and {@code sg}; it is
 * the AAD of every box and the input of the confirmation tag.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallCommitBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    /** The era HASH. */
    @JsonProperty("era")
    private String era;
    @JsonProperty("epoch")
    private Long epoch;
    /** epoch - 1; omitted on the {@code initial} commit (epoch 0). */
    @JsonProperty("prev")
    private Long prev;
    /** {@code leave} | {@code era} | {@code backstop} | {@code restart} | {@code initial}. */
    @JsonProperty("kind")
    private String kind;
    /** Sorted ann_hash list of the legs that receive the secret. */
    @JsonProperty("roster")
    private List<String> roster;
    @JsonProperty("rhash")
    private String rhash;
    @JsonProperty("boxes")
    private List<CallCommitBoxBean> boxes;
    /** confirm_e, 16 bytes hex. */
    @JsonProperty("conf")
    private String conf;

    public CallCommitBean() {
        super(CallConstants.T_COMMIT);
    }
}
