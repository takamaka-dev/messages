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
package io.takamaka.messages.utils;

import io.takamaka.messages.beans.BaseBean;
import io.takamaka.messages.beans.MessageAddress;
import io.takamaka.messages.beans.implementation.PayRequestAction;
import io.takamaka.messages.exception.MessageException;
import java.math.BigInteger;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class SimpleRequestModels {

    public static final BaseBean getSimplePayRequest_V_1_0(String to, BigInteger greenValueNanoTkg, BigInteger redValueNanoTkr, String message) throws MessageException {
        return new BaseBean("1.0",
                new PayRequestAction(
                        SimpleRequestHelper.getAddress(to),
                        greenValueNanoTkg,
                        redValueNanoTkr,
                        message),
                ActionType.REQUEST_PAY.name());
    }
}
