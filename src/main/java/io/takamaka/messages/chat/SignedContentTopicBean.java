/*
 * Copyright 2024 AiliA SA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.takamaka.messages.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.extra.beans.EncMessageBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SignedContentTopicBean {

    /**
     * sha3-256(topic title) b64url
     */
    @JsonProperty("topic_title_hash")
    private String topicTitleHash;
    /**
     * sha3-256(symmetricKey) b64url
     */
    @JsonProperty("topic_symmetric_key_signature")
    private String topicSymmetricKeySignature;
    /**
     * password encrypted TopicTitleKeyBean
     */
    @JsonProperty("topic_description")
    private EncMessageBean topicDescription;
    /**
     * list of members with encrypted symmetricTopicKey
     */
    @JsonProperty("topic_members_map")
    private TopicKeyDistributionMapBean topicMembersMap;
}
