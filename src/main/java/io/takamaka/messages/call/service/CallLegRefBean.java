package io.takamaka.messages.call.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Names one leg of one call, unsigned: the payload of the {@code presence} route. It needs no signature because the
 * service answers it only on the connection that holds that leg's event stream (connection-bound, as rschat's
 * typing stream binds its identity, DR-007).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallLegRefBean {

    @JsonProperty("call")
    private String call;
    @JsonProperty("leg")
    private String leg;
}
