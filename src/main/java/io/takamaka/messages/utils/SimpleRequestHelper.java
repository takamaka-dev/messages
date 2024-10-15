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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.takamaka.extra.beans.CompactAddressBean;
import io.takamaka.extra.identicon.exceptions.AddressNotRecognizedException;
import io.takamaka.extra.identicon.exceptions.AddressTooLongException;
import io.takamaka.extra.utils.TkmAddressUtils;
import static io.takamaka.extra.utils.TkmAddressUtils.TypeOfAddress.ed25519;
import static io.takamaka.extra.utils.TkmAddressUtils.TypeOfAddress.qTesla;
import static io.takamaka.extra.utils.TkmAddressUtils.TypeOfAddress.undefined;
import io.takamaka.messages.beans.BaseBean;
import io.takamaka.messages.beans.MessageAddress;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class SimpleRequestHelper {

    public static final String ADDRESS_CHECK_ED25519_STRING = "^[a-zA-Z-_.]{44}$";
    public static final String ADDRESS_CHECK_QTESLA_STRING = "^[a-zA-Z-_.]{19840}$";
    public static final String ADDRESS_CHECK_COMPACT_STRING = "^[a-zA-Z-_.]{64}$";
    public static final Pattern ADDRESS_CHECK_ED25519_PATTERN = Pattern.compile(ADDRESS_CHECK_ED25519_STRING);
    public static final Pattern ADDRESS_CHECK_QTESLA_PATTERN = Pattern.compile(ADDRESS_CHECK_QTESLA_STRING);
    public static final Pattern ADDRESS_CHECK_COMPACT_PATTERN = Pattern.compile(ADDRESS_CHECK_COMPACT_STRING);

    /**
     * If it is an ed25519 the base64 URL encoding, if it is a qTesla the
     * sha3-384 encoded base64 URL. If it is an unrecognized object (e.g., an
     * incorrect "to" field in which a string has been entered that does not
     * fall into the previous cases) the sha3-384 encoded base64 URL of the
     * same.
     *
     * If the address is in bas64url format and is 64 characters long it is
     * considered as a compact address and used directly.
     *
     * @param address
     * @return
     * @throws MessageException
     */
    public static final MessageAddress getAddress(String address) throws MessageException {
        try {
            String typeOfAddress;
            String addressinternal;
            CompactAddressBean compactAddress = TkmAddressUtils.toCompactAddress(address);
            if (address.length() == 64) {
                if (ADDRESS_CHECK_COMPACT_PATTERN.matcher(String.valueOf(address)).find()) {
                    typeOfAddress = "c";
                    addressinternal = address;
                }
            }
            switch (compactAddress.getType()) {
                case ed25519:
                    if (ADDRESS_CHECK_ED25519_PATTERN.matcher(String.valueOf(address)).find()) {
                        typeOfAddress = "f";
                        addressinternal = compactAddress.getOriginal();
                    } else {
                        throw new MessageException("address not recognized (ed25519)" + address);
                    }
                    break;
                case qTesla:
                case undefined:
                    if (ADDRESS_CHECK_QTESLA_PATTERN.matcher(String.valueOf(address)).find()) {
                        typeOfAddress = "c";
                        addressinternal = compactAddress.getDefaultShort();
                    } else {
                        throw new MessageException("address not recognized (not qtesla)" + address);
                    }
                    break;
                default:
                    throw new MessageException("address not recognized " + address);
            }
            return new MessageAddress(typeOfAddress, addressinternal);
        } catch (AddressNotRecognizedException | AddressTooLongException ex) {
            throw new MessageException(ex);
        }
    }

    public static final String getRequestJsonPretty(BaseBean baseBean) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(baseBean);
    }

    public static final String getRequestJsonCompact(BaseBean baseBean) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper()
                .configure(SerializationFeature.INDENT_OUTPUT, false)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .writeValueAsString(baseBean);

    }

}
