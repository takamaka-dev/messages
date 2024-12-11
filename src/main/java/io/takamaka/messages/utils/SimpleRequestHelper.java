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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.takamaka.extra.beans.CompactAddressBean;
import io.takamaka.extra.beans.EncMessageBean;
import io.takamaka.extra.identicon.exceptions.AddressNotRecognizedException;
import io.takamaka.extra.identicon.exceptions.AddressTooLongException;
import io.takamaka.extra.utils.SerializerUtils;
import io.takamaka.extra.utils.TkmAddressUtils;
import static io.takamaka.extra.utils.TkmAddressUtils.TypeOfAddress.ed25519;
import static io.takamaka.extra.utils.TkmAddressUtils.TypeOfAddress.qTesla;
import static io.takamaka.extra.utils.TkmAddressUtils.TypeOfAddress.undefined;
import io.takamaka.extra.utils.TkmEncryptionUtils;
import io.takamaka.messages.beans.BaseBean;
import io.takamaka.messages.beans.MessageAction;
import io.takamaka.messages.beans.MessageAddress;
import io.takamaka.messages.beans.implementation.MessageActionClazz;
import io.takamaka.messages.chat.SignedMessageBean;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.TkmCypherProviderBCED25519;
import io.takamaka.wallet.TkmCypherProviderBCQTESLAPSSC1Round1;
import io.takamaka.wallet.TkmCypherProviderBCQTESLAPSSC1Round2;
import io.takamaka.wallet.beans.TkmCypherBean;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.KeyContexts;
import static io.takamaka.wallet.utils.KeyContexts.WalletCypher.BCQTESLA_PS_1;
import static io.takamaka.wallet.utils.KeyContexts.WalletCypher.BCQTESLA_PS_1_R2;
import static io.takamaka.wallet.utils.KeyContexts.WalletCypher.Ed25519;
import static io.takamaka.wallet.utils.KeyContexts.WalletCypher.Ed25519BC;
import static io.takamaka.wallet.utils.KeyContexts.WalletCypher.Tink;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class SimpleRequestHelper {
    
    public static final String ADDRESS_CHECK_ED25519_STRING = "^[a-zA-Z0-9-_.]{44}$";
    public static final String ADDRESS_CHECK_QTESLA_STRING = "^[a-zA-Z0-9-_.]{19840}$";
    public static final String ADDRESS_CHECK_COMPACT_STRING = "^[a-zA-Z0-9-_.]{64}$";
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
    
    public static final String getCanonicalJson(Object contentBean) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper()
                .configure(SerializationFeature.INDENT_OUTPUT, false)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .writeValueAsString(contentBean);
    }
    
    public static final String getRequestJsonCompact(MessageAction messageAction) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper()
                .configure(SerializationFeature.INDENT_OUTPUT, false)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .writeValueAsString(messageAction);
        
    }
    
    public static final MessageAction fromJsonToMessageAction(String actionJson) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(actionJson, MessageActionClazz.class);
    }
    
    public static final void signEncryptMessage(BaseBean baseBean, InstanceWalletKeystoreInterface iwk, int index, String password, String scope, String version) throws JsonProcessingException, WalletException, MessageException {
        //set type of signature
        baseBean.setTypeOfSignature(iwk.getWalletCypher().name());
        //set message action from field
        baseBean.getMessageAction().setFrom(getAddress(iwk.getPublicKeyAtIndexURL64(index)));
        //create message to be signed
        String requestJsonCompact = SimpleRequestHelper.getRequestJsonCompact(baseBean.getMessageAction());
        log.info("String to be signed " + requestJsonCompact);
        final EncMessageBean toPasswordEncryptedContent = TkmEncryptionUtils.toPasswordEncryptedContent(password, requestJsonCompact, scope, version);
        final String encSerializedAction = SerializerUtils.getJson(toPasswordEncryptedContent);
        log.info("Encrypted message to be signed: " + encSerializedAction);
        final TkmCypherBean sign;
        switch (iwk.getWalletCypher()) {
            case Ed25519BC:
                //sign using ed25519
                sign = TkmCypherProviderBCED25519.sign(iwk.getKeyPairAtIndex(index), encSerializedAction);
                break;
            
            case BCQTESLA_PS_1:
                sign = TkmCypherProviderBCQTESLAPSSC1Round1.sign(iwk.getKeyPairAtIndex(index), encSerializedAction);
                break;
            case BCQTESLA_PS_1_R2:
                sign = TkmCypherProviderBCQTESLAPSSC1Round2.sign(iwk.getKeyPairAtIndex(index), encSerializedAction);
                break;
            case Ed25519:
            case Tink:
            default:
                throw new AssertionError(iwk.getWalletCypher().name());
        }
        if (sign.isValid()) {
            //update signature in the base bean
            baseBean.setSignature(sign.getSignature());
            baseBean.setEncryptedMessageAction(encSerializedAction);
            baseBean.setMessageAction(null);
        } else {
            throw new MessageException("unable to sign transaction", sign.getEx());
        }
    }
    
    public static final void deccryptMessage(BaseBean baseBean, String password, String scope) throws WalletException, JsonProcessingException {
        EncMessageBean encMessageBeanFromJson = SerializerUtils.getEncMessageBeanFromJson(baseBean.getEncryptedMessageAction());
        String actionJson = TkmEncryptionUtils.fromPasswordEncryptedContent(password, scope, encMessageBeanFromJson);
        log.info(actionJson);
        MessageAction fromJsonToMessageAction = fromJsonToMessageAction(actionJson);
        baseBean.setMessageAction(fromJsonToMessageAction);
    }
    
    public static final boolean verifyEncryptedMessageSignature(BaseBean encrypedBaseBean, String publicKey) {
        switch (encrypedBaseBean.getTypeOfSignature()) {
            case "Ed25519BC":
                TkmCypherBean verifyEd = TkmCypherProviderBCED25519.verify(
                        publicKey,
                        encrypedBaseBean.getSignature(),
                        encrypedBaseBean.getEncryptedMessageAction()
                );
                
                if (verifyEd.isValid()) {
                    return true;
                }
                break;
            default:
                throw new AssertionError();
        }
        return false;
    }

    /**
     *
     * Use @see SimpleRequestHelper.getCanonicalJson for details for canonical
     * message
     *
     * @param canonicalJsonMessage minimized json with keys lexicographically
     * ordered
     * @param iwk
     * @param index
     * @return
     * @throws MessageException
     */
    public static final String signChatMessage(String canonicalJsonMessage, InstanceWalletKeystoreInterface iwk, int index) throws MessageException {
        TkmCypherBean sign = null;
        try {
            switch (iwk.getWalletCypher()) {
                case Ed25519BC:
                    //sign using ed25519
                    sign = TkmCypherProviderBCED25519.sign(iwk.getKeyPairAtIndex(index), canonicalJsonMessage);
                    break;
                
                case BCQTESLA_PS_1:
                    sign = TkmCypherProviderBCQTESLAPSSC1Round1.sign(iwk.getKeyPairAtIndex(index), canonicalJsonMessage);
                    break;
                case BCQTESLA_PS_1_R2:
                    sign = TkmCypherProviderBCQTESLAPSSC1Round2.sign(iwk.getKeyPairAtIndex(index), canonicalJsonMessage);
                    break;
                case Ed25519:
                case Tink:
                default:
                    throw new AssertionError(iwk.getWalletCypher().name());
            }
            if (sign.isValid()) {
                //update signature in the base bean
                return sign.getSignature();
            } else {
                throw new MessageException("unable to sign transaction", sign.getEx());
            }
        } catch (WalletException ex) {
            if (sign != null) {
                throw new MessageException("signature exception [1]", sign.getEx());
            } else {
                throw new MessageException("signature exception [2]", ex);
            }
        }
    }
    
    public static final boolean verifyChatMessageSignature(String canonicalJsonMessage, String fullPublicKey) throws JsonProcessingException, HashEncodeException, HashAlgorithmNotFoundException, HashProviderNotFoundException, AddressNotRecognizedException, AddressTooLongException, MessageException {
        SignedMessageBean fromJsonToSignedMessageBean = ChatUtils.fromJsonToSignedMessageBean(canonicalJsonMessage);
//        log.info(fromJsonToSignedMessageBean.toString());
//        log.info("message to be verified: " + requestJsonCompact);
//        switch (baseBean.getTypeOfSignature()) {
//            case "Ed25519BC":
//
//                TkmCypherBean verifyEd = TkmCypherProviderBCED25519.verify(
//                        baseBean.getMessageAction().getFrom().getAddress(),
//                        baseBean.getSignature(),
//                        requestJsonCompact
//                );
//
//                if (verifyEd.isValid()) {
//                    return true;
//                }
//
//                break;
//
//            case "BCQTESLA_PS_1":
//
//                String addressQTR1 = fullPublicKey;
//                if (fullPublicKey != null && fullPublicKey.length() != 19840) {
//                    addressQTR1 = retrieveBookmark("test", fullPublicKey);
//                }
//                String testQTR1 = TkmAddressUtils.toCompactAddress(addressQTR1).getDefaultShort();
//                if (testQTR1.equals(baseBean.getMessageAction().getFrom().getAddress())) {
//                    TkmCypherBean verifyQTR1 = TkmCypherProviderBCQTESLAPSSC1Round1.verify(
//                            addressQTR1,
//                            baseBean.getSignature(),
//                            requestJsonCompact
//                    );
//                    if (verifyQTR1.isValid()) {
//                        return true;
//                    }
//                } else {
//                    throw new MessageException("Key verification error!");
//                }
//
//                break;
//            case "BCQTESLA_PS_1_R2":
//                String addressQTR2 = fullPublicKey;
//                if (fullPublicKey != null && fullPublicKey.length() != 19840) {
//                    addressQTR2 = retrieveBookmark("test", fullPublicKey);
//                }
//                String testQTR2 = TkmAddressUtils.toCompactAddress(addressQTR2).getDefaultShort();
//                if (testQTR2.equals(baseBean.getMessageAction().getFrom().getAddress())) {
//                    TkmCypherBean verifyQTR2 = TkmCypherProviderBCQTESLAPSSC1Round2.verify(
//                            addressQTR2,
//                            baseBean.getSignature(),
//                            requestJsonCompact
//                    );
//                    if (verifyQTR2.isValid()) {
//                        return true;
//                    }
//                } else {
//                    throw new MessageException("Key verification error!");
//                }
//
//                break;
//            default:
//                throw new AssertionError("unknown cypher");
//
//        }
        return false;
    }
    
    public static final void signMessage(BaseBean baseBean, InstanceWalletKeystoreInterface iwk, int index) throws JsonProcessingException, WalletException, MessageException {
        //set type of signature
        baseBean.setTypeOfSignature(iwk.getWalletCypher().name());
        //set message action from field
        baseBean.getMessageAction().setFrom(getAddress(iwk.getPublicKeyAtIndexURL64(index)));
        //create message to be signed
        String requestJsonCompact = SimpleRequestHelper.getRequestJsonCompact(baseBean.getMessageAction());
        log.info("String to be signed " + requestJsonCompact);
        final TkmCypherBean sign;
        switch (iwk.getWalletCypher()) {
            case Ed25519BC:
                //sign using ed25519
                sign = TkmCypherProviderBCED25519.sign(iwk.getKeyPairAtIndex(index), requestJsonCompact);
                break;
            
            case BCQTESLA_PS_1:
                sign = TkmCypherProviderBCQTESLAPSSC1Round1.sign(iwk.getKeyPairAtIndex(index), requestJsonCompact);
                break;
            case BCQTESLA_PS_1_R2:
                sign = TkmCypherProviderBCQTESLAPSSC1Round2.sign(iwk.getKeyPairAtIndex(index), requestJsonCompact);
                break;
            case Ed25519:
            case Tink:
            default:
                throw new AssertionError(iwk.getWalletCypher().name());
        }
        if (sign.isValid()) {
            //update signature in the base bean
            baseBean.setSignature(sign.getSignature());
        } else {
            throw new MessageException("unable to sign transaction", sign.getEx());
        }
    }
    
    public static final boolean verifyMessageSignature(BaseBean baseBean, String fullPublicKey) throws JsonProcessingException, HashEncodeException, HashAlgorithmNotFoundException, HashProviderNotFoundException, AddressNotRecognizedException, AddressTooLongException, MessageException {
        if (TkmTextUtils.isNullOrBlank(baseBean.getTypeOfSignature())) {
            System.err.println("missing signature type");
            return false;
        }
        if (TkmTextUtils.isNullOrBlank(baseBean.getSignature())) {
            System.err.println("missing signature");
            return false;
        }
        if (baseBean.getMessageAction().getFrom().getAddress() == null) {
            System.err.println("null signing address");
            return false;
        }
        if (TkmTextUtils.isNullOrBlank(baseBean.getMessageAction().getFrom().getAddress())) {
            System.err.println("missing signature address");
            return false;
        }
        String requestJsonCompact = getRequestJsonCompact(baseBean.getMessageAction());
        log.info("message to be verified: " + requestJsonCompact);
        switch (baseBean.getTypeOfSignature()) {
            case "Ed25519BC":
                
                TkmCypherBean verifyEd = TkmCypherProviderBCED25519.verify(
                        baseBean.getMessageAction().getFrom().getAddress(),
                        baseBean.getSignature(),
                        requestJsonCompact
                );
                
                if (verifyEd.isValid()) {
                    return true;
                }
                
                break;
            
            case "BCQTESLA_PS_1":
                
                String addressQTR1 = fullPublicKey;
                if (fullPublicKey != null && fullPublicKey.length() != 19840) {
                    addressQTR1 = retrieveBookmark("test", fullPublicKey);
                }
                String testQTR1 = TkmAddressUtils.toCompactAddress(addressQTR1).getDefaultShort();
                if (testQTR1.equals(baseBean.getMessageAction().getFrom().getAddress())) {
                    TkmCypherBean verifyQTR1 = TkmCypherProviderBCQTESLAPSSC1Round1.verify(
                            addressQTR1,
                            baseBean.getSignature(),
                            requestJsonCompact
                    );
                    if (verifyQTR1.isValid()) {
                        return true;
                    }
                } else {
                    throw new MessageException("Key verification error!");
                }
                
                break;
            case "BCQTESLA_PS_1_R2":
                String addressQTR2 = fullPublicKey;
                if (fullPublicKey != null && fullPublicKey.length() != 19840) {
                    addressQTR2 = retrieveBookmark("test", fullPublicKey);
                }
                String testQTR2 = TkmAddressUtils.toCompactAddress(addressQTR2).getDefaultShort();
                if (testQTR2.equals(baseBean.getMessageAction().getFrom().getAddress())) {
                    TkmCypherBean verifyQTR2 = TkmCypherProviderBCQTESLAPSSC1Round2.verify(
                            addressQTR2,
                            baseBean.getSignature(),
                            requestJsonCompact
                    );
                    if (verifyQTR2.isValid()) {
                        return true;
                    }
                } else {
                    throw new MessageException("Key verification error!");
                }
                
                break;
            default:
                throw new AssertionError("unknown cypher");
            
        }
        return false;
    }
    
    private static String retrieveBookmark(String environment, String address384) {
        String result = null;
        try {
            if (address384 != null) {
                if (address384.length() == 96 && isHexadecimal(address384)) {
                    result = callBookmarkApi(environment, address384);
                } else {
                    String hexAddress = TkmSignUtils.fromB64UrlToHEX(address384);
                    if (hexAddress.length() == 96 && isHexadecimal(hexAddress)) {
                        result = callBookmarkApi(environment, hexAddress);
                    }
                }
            } else {
                return result;
            }
        } catch (IOException ex) {
            return result;
        }
        
        return result;
    }
    
    private static String callBookmarkApi(String environment, String address) throws ProtocolException, IOException {
        String result = null;
        String apiEndpointTest = "https://test.takamaka.org/api/v1/bookmark/retrieve/";
        String apiEndpointProd = "https://takamaka.io/api/v1/bookmark/retrieve/";
        Map<String, String> params = new LinkedHashMap<>();
        switch (environment.toLowerCase()) {
            case "test":
                result = Post.Post(apiEndpointTest + address, params);
                break;
            default:
                result = Post.Post(apiEndpointProd + address, params);
                throw new AssertionError();
        }
        return result;
    }
    
    public static boolean isHexadecimal(String text) {
        return text.matches("[0-9A-Fa-f]+");
    }
    
}
