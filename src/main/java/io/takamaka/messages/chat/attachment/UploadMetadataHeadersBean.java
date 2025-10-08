/*
 * Copyright 2025 AiliA SA.
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
package io.takamaka.messages.chat.attachment;

import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class UploadMetadataHeadersBean {

    public static final String PRE = "message/x.io.takamaka.rschat.upload.";

    public static final String FROM = "from";
    public static final String SIGNATURE = "signature";
    public static final String MESSAGE_TYPE = "message-type";
    public static final String SIGNATURE_TYPE = "signature-type";
    public static final String TOPIC_TITLE = "topic-title";
    public static final String UPLOAD_CONTENT_SIGNATURE = "upload-conent-signature";
    public static final String SIZE = "size";
    public static final String SIGNED_UPLOAD = "signed-upload";

    public static final String mtd(final String meta) {
        String metadata = String.format("%1$s%2$s", PRE, meta);
        log.info("metadata: {}", metadata);
        return metadata;
    }

}
