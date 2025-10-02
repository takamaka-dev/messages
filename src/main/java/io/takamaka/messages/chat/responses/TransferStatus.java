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
package io.takamaka.messages.chat.responses;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public enum TransferStatus {

    /**
     * Indicates that a chunk of the file transfer has been completed
     * successfully.
     */
    CHUNK_COMPLETED,
    /**
     * Indicates that the entire file transfer operation has been completed
     * successfully.
     */
    TRANSFER_COMPLETED,
    /**
     * Indicates that the file transfer operation has failed.
     */
    TRANSFER_FAIL,
    /**
     * Indicates that the system is currently verifying the signature of the
     * transferred file.
     */
    VERIFING_SIGNATURE,
    /**
     * Indicates that the system is currently verifying the hash of the
     * transferred file.
     */
    VERIFING_HASH,
    /**
     * Indicates that the file transfer operation is being executed.
     */
    BATCH_MOVE,
    /**
     * Indicates that the file transfer operation is being prepared.
     */
    PREPARING,
    /**
     * Indicates that the transferred file is ready for download.
     */
    READY_FOR_DOWNLOAD,
    /**
     * Indicates that the signature verification of the transferred file has
     * failed.
     */
    SIGNATURE_REJECTED,
    /**
     * Indicates that a concurrent upload operation is already running,
     * preventing a new transfer.
     */
    CONCURRENT_UPLOAD_ALREADY_RUNNING,
    CONTENT_NOT_FOUND

}
