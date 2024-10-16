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
package io.takamaka.messages.beans.implementation;

import io.takamaka.messages.beans.MessageAction;
import io.takamaka.messages.beans.MessageAddress;
import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class BlobRequestAction extends MessageAction {

    /**
     * default pay request constructor
     *
     * @param message a message not null
     */
    public BlobRequestAction(String message) {
        super.setDate(null);
        super.setFrom(null);
        super.setTextMessage(message);
        super.setTo(null);
        super.setGreen(null);
        super.setRed(null);
        super.setEncodedWallet(null);
    }

}
