package io.takamaka.messages.call.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload of the {@code call.text} route: the call the text belongs to (routing only — the §10.2 object names its leg,
 * not its call) and the §10.2 {@code text} object, which the service fans out verbatim. Like
 * {@link CallLegRefBean}, an unsigned wrapper bound to the connection that holds the leg's stream (S-2).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallTextRequestBean {

    @JsonProperty("call")
    private String call;
    @JsonProperty("text")
    private CallTextBean text;
}
