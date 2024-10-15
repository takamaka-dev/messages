/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 *
 * @author giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BaseBean {

    /**
     * All jsons with the same major version (e.g., 1.X) maintain backward
     * compatibility; a parser created to read version 1.0 will be able to
     * decode version 1.1 simply by ignoring fields not defined in the version.
     * In the event that a field is introduced that breaks compatibility a new
     * major version must be created. The json version corresponds to the field
     * with the highest version number.
     */
    @JsonProperty("v")
    private String version;
    @JsonProperty("s")
    private String scope;
    @JsonProperty("a")
    private MessageAction messageAction;
    /**
     * <ul>
     * <li><b>st</b> stake to node <i> (v1.0)</i></li>
     * <li><b>b</b> blob <i> (v1.0)</i></li>
     * <li><b>rp</b> request pay <i> (v1.0)</i></li>
     * <li><b>st</b> stake to node <i> (v1.0)</i></li>
     * <li><b>su</b> stake undo <i> (v1.0)</i></li>
     * </ul>
     *
     */
    @JsonProperty("t")
    private String typeOfAction;
}
