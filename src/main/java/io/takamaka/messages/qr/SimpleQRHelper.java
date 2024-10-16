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
package io.takamaka.messages.qr;

import java.awt.image.BufferedImage;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import io.takamaka.messages.beans.BaseBean;
import io.takamaka.messages.exception.QrException;
import io.takamaka.messages.utils.SimpleRequestHelper;
import io.takamaka.wallet.utils.FileHelper;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class SimpleQRHelper {

    public static final BufferedImage getQRbyString(String message, int w, int h) throws QrException {
        BufferedImage qrBufferedImage;
        try {
            QRCodeWriter qcw = new QRCodeWriter();
            BitMatrix qrBitMatrix;
            qrBitMatrix = qcw.encode(message, BarcodeFormat.QR_CODE, w, h);
            qrBufferedImage = MatrixToImageWriter.toBufferedImage(qrBitMatrix);
        } catch (WriterException ex) {
            throw new QrException(ex);
        }
        return qrBufferedImage;
    }

    public static final void getJsonAsQRPNGAndTXT(BaseBean simpleRequest) throws QrException {
        try {
            String destFolder = Paths.get(FileHelper.getDefaultApplicationDirectoryPath().toString(), "test_qr").toString();
            FileHelper.createFolderAtPathIfNoneExist(Path.of(destFolder));
            String requestJsonPretty = SimpleRequestHelper.getRequestJsonPretty(simpleRequest);
            String filename = getNameFromText(requestJsonPretty);
            String destFile = Paths.get(destFolder, filename).toString();
            System.out.println(SimpleQRGenerator.writeQRToPNG(Path.of(destFile), 256, 256, requestJsonPretty));
            System.out.println("Json File: " + filename + ".txt");
            System.out.println(requestJsonPretty);
            SimpleQRGenerator.writeJsonToTXT(Path.of(destFolder), requestJsonPretty, filename);
            System.out.println("-------------------------------------------------------------------------------------------");

        } catch (IOException ex) {
            throw new QrException(ex);
        }
    }

    public static final String getNameFromText(String value) {
        String myHash = DigestUtils
                .md5Hex(value).toUpperCase();
        return myHash.substring(0, 4);

    }

}
