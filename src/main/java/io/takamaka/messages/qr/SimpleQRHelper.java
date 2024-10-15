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
import io.takamaka.messages.exception.QrException;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class SimpleQRHelper {

    public static BufferedImage getQRbyString(String message, int w, int h) throws QrException {
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
    
}
