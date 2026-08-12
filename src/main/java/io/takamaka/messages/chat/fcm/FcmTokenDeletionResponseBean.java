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
package io.takamaka.messages.chat.fcm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response bean for the FCM token deletion routes
 * ({@code deletefcmtoken}, {@code deleteallfcmtokens}).
 *
 * <p>Deletion is idempotent: removing a token that is not registered is a
 * success carrying {@code deleted_count = 0}, not an error. Clients that need
 * to distinguish "removed" from "was not there" read the count rather than the
 * success flag. This differs deliberately from {@code unregisterfcmtoken},
 * which answers {@code TOKEN_NOT_FOUND}.</p>
 *
 * <p>Kept separate from {@link FcmTokenRegistrationResponseBean} because that
 * bean's {@code registration_time} has no meaning for a delete, while the row
 * count does.</p>
 *
 * @author Iris Dimni info@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FcmTokenDeletionResponseBean {

    /**
     * Whether the request was accepted and applied. True even when nothing
     * matched — see the class note on idempotency.
     */
    private boolean success;

    /**
     * Human-readable description of the result.
     */
    private String message;

    /**
     * Error code when {@link #success} is false, null otherwise.
     */
    @JsonProperty("error_code")
    private String errorCode;

    /**
     * Number of token rows actually removed (0 when nothing matched).
     * Null on failure.
     */
    @JsonProperty("deleted_count")
    private Long deletedCount;

    /**
     * Create a success response carrying the number of rows removed.
     */
    public static FcmTokenDeletionResponseBean success(long deletedCount) {
        return new FcmTokenDeletionResponseBean(
                true,
                deletedCount == 0
                        ? "No matching FCM token to delete"
                        : "FCM token(s) deleted successfully",
                null,
                deletedCount
        );
    }

    /**
     * Create an error response.
     */
    public static FcmTokenDeletionResponseBean error(String errorCode, String message) {
        return new FcmTokenDeletionResponseBean(
                false,
                message,
                errorCode,
                null
        );
    }
}
