/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.utils;

import io.takamaka.messages.chat.TopicTitleKeyBean;
import io.takamaka.messages.exception.InvalidParameterException;
import io.takamaka.wallet.InstanceWalletKeyStoreBCRSA4096ENC;
import java.util.Arrays;
import org.apache.commons.text.RandomStringGenerator;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class ChatCryptoUtils {

    /**
     *
     * @param len default key len 400, you can specify different key len
     * @return [a-zA-Z0-9]{len} safe random key
     * @throws io.takamaka.messages.exception.InvalidParameterException
     */
    public static final String generateRandomSafeKey(int... len) throws InvalidParameterException {
        int keyLenght = 400;
        switch (len.length) {
            case 0:
                keyLenght = 400;
                break;
            case 1:
                keyLenght = len[0];
                break;
            default:
                throw new InvalidParameterException("exactly 0 or 1 argument required, given " + Arrays.toString(len));
        }

        RandomStringGenerator generator = new RandomStringGenerator.Builder()
                .withinRange('0', 'z')
                .filteredBy(Character::isLetterOrDigit)
                .get();
        String secretKey = generator.generate(keyLenght);
        return secretKey;
    }

    /**
     * generate a random key using a secure rng implementation and appen the
     * topic title
     *
     * @param topicTitle title of the topic
     * @return
     * @throws InvalidParameterException
     */
    public static final TopicTitleKeyBean generateTopicKeyBean(String topicTitle) throws InvalidParameterException {
        return new TopicTitleKeyBean(topicTitle, generateRandomSafeKey());
    }

}
