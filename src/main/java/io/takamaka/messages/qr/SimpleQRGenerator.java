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

import io.takamaka.messages.exception.QrException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class SimpleQRGenerator {

    public static final void writeQRToPNG(Path path, int w, int h, String message) throws QrException {
        try {
            BufferedImage qRbyString = SimpleQRHelper.getQRbyString(message, w, h);
            File outputfile = new File(path.toString());
            boolean write = ImageIO.write(qRbyString, "png", outputfile);
            System.err.println(write);
        } catch (IOException ex) {
            throw new QrException(ex);
        }
    }

}
