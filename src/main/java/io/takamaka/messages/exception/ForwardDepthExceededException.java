/*
 * Copyright 2026 AiliA SA.
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
package io.takamaka.messages.exception;

/**
 * Sender-side: an attempt to construct a {@code forward} whose
 * {@code fw_content} chain would exceed the hard depth cap. Exposes the
 * actual and maximum depths so a UI can explain ("current chain is N levels,
 * max is 10").
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public class ForwardDepthExceededException extends ChatCryptoConstructionException {

    private static final long serialVersionUID = 1L;

    private final int actualDepth;
    private final int maxDepth;

    public ForwardDepthExceededException(int actualDepth, int maxDepth) {
        super(FORWARD_DEPTH_EXCEEDED,
                "forward chain depth " + actualDepth + " exceeds the maximum of " + maxDepth);
        this.actualDepth = actualDepth;
        this.maxDepth = maxDepth;
    }

    public int getActualDepth() {
        return actualDepth;
    }

    public int getMaxDepth() {
        return maxDepth;
    }
}
