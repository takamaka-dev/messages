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
import io.takamaka.messages.beans.implementation.BlobRequestAction;
import io.takamaka.messages.beans.implementation.PayRequestAction;
import io.takamaka.messages.beans.implementation.StakeRequestAction;
import io.takamaka.messages.beans.implementation.StakeUndoRequestAction;
import io.takamaka.messages.beans.implementation.WalletEncryptedAction;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.beans.EncKeyBean;
import io.takamaka.wallet.beans.KeyBean;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.exceptions.UnlockWalletException;
import io.takamaka.wallet.utils.DefaultInitParameters;
import io.takamaka.wallet.utils.FileHelper;
import io.takamaka.wallet.utils.FixedParameters;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class SimpleRequestModels {

    public static final String SUPER_SAFE_PASSWORD = "super_safe_password";
    public static final String EXAMPLE_WALLET_ED25519_QR_EXPORT = "example_wallet_ed25519_qr_export";

    public static final BaseBean getSimplePayRequest_V_1_0(String to, BigInteger greenValueNanoTkg, BigInteger redValueNanoTkr, String message) throws MessageException {
        return new BaseBean("1.0",
                new PayRequestAction(
                        SimpleRequestHelper.getAddress(to),
                        greenValueNanoTkg,
                        redValueNanoTkr,
                        message),
                ActionType.REQUEST_PAY.getShortCode(),
                null,
                null
        );
    }

    public static final BaseBean getSimpleStakeRequest_V_1_0(String to, BigInteger greenValueNanoTkg, String message) throws MessageException {
        return new BaseBean("1.0",
                new StakeRequestAction(
                        SimpleRequestHelper.getAddress(to),
                        greenValueNanoTkg,
                        message),
                ActionType.REQUEST_PAY.getShortCode(),
                null,
                null);
    }

    public static final BaseBean getSimpleStakeUndoRequest_V_1_0(Long notBefore, String message) throws MessageException {
        return new BaseBean("1.0",
                new StakeUndoRequestAction(
                        notBefore,
                        message),
                ActionType.REQUEST_PAY.getShortCode(),
                null,
                null);
    }

    public static final BaseBean getSimpleBlobRequest_V_1_0(String message) throws MessageException {
        return new BaseBean("1.0",
                new BlobRequestAction(
                        message
                ),
                ActionType.BLOB.getShortCode(),
                null,
                null);
    }

    public static final BaseBean getWalletEncrypted_V_1_0() throws MessageException {
        try {
            //create a test wallet
            InstanceWalletKeystoreInterface iwk = new InstanceWalletKeyStoreBCED25519(EXAMPLE_WALLET_ED25519_QR_EXPORT, SUPER_SAFE_PASSWORD);
            //get wallet by name
            Path currentWalletPath = Paths.get(FileHelper.getDefaultWalletDirectoryPath().toString(), EXAMPLE_WALLET_ED25519_QR_EXPORT + DefaultInitParameters.WALLET_EXTENSION);
            //encoded wallet json
            String encJson = FileHelper.readStringFromFile(currentWalletPath);
            //decode to bean
            EncKeyBean enckeyBeanFromJson = TkmTextUtils.enckeyBeanFromJson(encJson);
            //read encoded wallet bytes
            byte[][] wallet = enckeyBeanFromJson.getWallet();
            //try to decypt wallet for test --- BEGIN ---
            String json;
            byte[] passwordDigest = TkmSignUtils.PWHash(SUPER_SAFE_PASSWORD, "TakamakaWallet", 1, 256);
            SecretKey sk = new SecretKeySpec(passwordDigest, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, sk, new IvParameterSpec(wallet[0]));
            json = new String(cipher.doFinal(wallet[1]));
            KeyBean keyBeanFromJson = TkmTextUtils.keyBeanFromJson(json);
            //try to decypt wallet for test --- END ---
            return new BaseBean("1.0",
                    new WalletEncryptedAction(enckeyBeanFromJson),
                    ActionType.EXPORT_WALLET_ENCRYPTED.getShortCode(),
                    null,
                    null
            );
//        Paths.get(FileHelper.getDefaultWalletDirectoryPath().toString(), internalName + DefaultInitParameters.WALLET_EXTENSION);
//            return new BaseBean("1.0",
//                    new PayRequestAction(
//                            SimpleRequestHelper.getAddress(to),
//                            greenValueNanoTkg,
//                            redValueNanoTkr,
//                            message),
//                    ActionType.REQUEST_PAY.name());
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | UnlockWalletException | IOException | HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException | InvalidKeySpecException | NoSuchAlgorithmException | NoSuchPaddingException ex) {
            throw new MessageException(ex);
        }
    }
}
