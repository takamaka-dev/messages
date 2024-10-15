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

import io.takamaka.extra.identicon.exceptions.AddressNotRecognizedException;
import io.takamaka.extra.identicon.exceptions.AddressTooLongException;
import io.takamaka.extra.utils.TkmAddressUtils;
import io.takamaka.messages.beans.MessageAction;
import io.takamaka.messages.beans.MessageAddress;
import io.takamaka.messages.exception.MessageException;
import java.math.BigInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
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
public class PayRequestAction extends MessageAction {

    /**
     * default pay request constructor
     *
     * @param to The address of the recipient of the transaction
     * @param greenValueNanoTkg the value in nano tkg or null
     * @param redValueNanoTkr the value in nano tkr or null
     * @param message a message or null
     */
    public PayRequestAction(MessageAddress to, BigInteger greenValueNanoTkg, BigInteger redValueNanoTkr, String message) {
        super.setDate(null);
        super.setFrom(null);
        super.setTextMessage(message);
        super.setTo(to);
        super.setGreen(greenValueNanoTkg);
        super.setRed(redValueNanoTkr);
        super.setEncodedWallet(null);
    }
    
}
