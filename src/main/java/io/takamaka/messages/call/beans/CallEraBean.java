package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Era record, era n &ge; 1 (spec §4.2). Note that here {@code era} is the era NUMBER (an integer), while in
 * {@code announce} and {@code commit} the field {@code era} is the era HASH (hex).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallEraBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    /** Era number n &ge; 1. */
    @JsonProperty("era")
    private Integer era;
    /** era_hash(n-1), hex. */
    @JsonProperty("prev")
    private String prev;
    @JsonProperty("mode")
    private String mode;
    @JsonProperty("inv")
    private List<String> inv;
    @JsonProperty("apol")
    private String apol;
    @JsonProperty("alist")
    private List<String> alist;
    @JsonProperty("mods")
    private List<String> mods;
    /** {@code add} | {@code remove} | {@code promote} | {@code demote} | {@code mode} | {@code mods}. */
    @JsonProperty("reason")
    private String reason;

    public CallEraBean() {
        super(CallConstants.T_ERA);
    }
}
