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
package io.takamaka.messages.utils;

import io.takamaka.wallet.TkmCypherProviderBCED25519;
import io.takamaka.wallet.beans.TkmCypherBean;

/**
 * The ONE door for verifying an Ed25519 signature on the CHAT path: the
 * signer key and the signature must be spelled in the canonical house form
 * before the wallet-core provider is asked anything.
 *
 * <p><b>Canonical form</b> — exactly what BouncyCastle {@code UrlBase64.encode}
 * emits and nothing else:
 * <ul>
 * <li>URL alphabet only ({@code A–Z a–z 0–9 - _});</li>
 * <li>whole 4-character blocks, {@code .} padding in the last block only
 * ({@code xx..} or {@code xxx.});</li>
 * <li>no whitespace anywhere (leading, trailing, embedded);</li>
 * <li>zero unused bits in the last data character;</li>
 * <li>decoded length exactly {@value #ED25519_PUBLIC_KEY_BYTES} bytes for a
 * key, {@value #ED25519_SIGNATURE_BYTES} for a signature.</li>
 * </ul>
 *
 * <p><b>Why.</b> {@code TkmCypherProviderBCED25519.verify(String,String,String)}
 * decodes with BouncyCastle {@code UrlBase64.decode}, which skips whitespace,
 * drops the unused bits of the last character, and builds the key from the
 * first 32 decoded bytes ignoring the rest. One key or signature could
 * therefore be spelled several ways and still verify — while the Dart port
 * ({@code Base64UrlUtils.decodeStrict} in wallet-core-flutter) refuses all of
 * them. Identities and signatures are used as identifiers (dedup keys,
 * first-write-wins, conversation membership), so "same bytes, different
 * text" must not verify. Operator ruling 2026-11-11, after a read-only scan
 * of the full TEST and PROD chat history found zero non-canonical signer
 * keys or signatures.
 *
 * <p><b>Scope: chat only.</b> Blockchain transaction verification
 * ({@code TkmWallet.verifySign} and the wallet-core provider itself) is NOT
 * changed and stays lenient — transaction formats are backward compatible and
 * that path has its own open ruling. Do not call this class from transaction
 * code and do not move the check down into the provider.
 *
 * <p>A non-canonical input is reported exactly like a bad signature: a
 * {@link TkmCypherBean} with {@code valid == false}. Nothing is thrown.
 *
 * <p>Vectors: {@code src/test/resources/java_strict_signature_base64_vectors.json}
 * (shared with wallet-core-flutter, {@code StrictChatSignatureEncodingTest}).
 */
public final class ChatSignatureVerifier {

    public static final int ED25519_PUBLIC_KEY_BYTES = 32;
    public static final int ED25519_SIGNATURE_BYTES = 64;

    private static final char PADDING = '.';

    private ChatSignatureVerifier() {
    }

    /**
     * Verify an Ed25519 chat signature, refusing any non-canonical spelling
     * of {@code publicKey} or {@code signature} before the provider runs.
     *
     * @return the provider's bean for canonical input; a bean with
     * {@code valid == false} (and the reason in {@code ex}) otherwise. Never
     * {@code null}, never throws.
     */
    public static TkmCypherBean verifyEd25519(String publicKey, String signature, String message) {
        try {
            if (!isCanonicalEd25519PublicKey(publicKey)) {
                return refused("non-canonical Ed25519 public key encoding");
            }
            if (!isCanonicalEd25519Signature(signature)) {
                return refused("non-canonical Ed25519 signature encoding");
            }
            if (message == null) {
                return refused("null message");
            }
            final TkmCypherBean result = TkmCypherProviderBCED25519.verify(publicKey, signature, message);
            return result == null ? refused("provider returned no result") : result;
        } catch (RuntimeException ex) {
            final TkmCypherBean bean = new TkmCypherBean();
            bean.setValid(false);
            bean.setEx(ex);
            return bean;
        }
    }

    /** {@code true} iff {@code text} is the canonical spelling of exactly 32 bytes. */
    public static boolean isCanonicalEd25519PublicKey(String text) {
        return isCanonical(text, ED25519_PUBLIC_KEY_BYTES);
    }

    /** {@code true} iff {@code text} is the canonical spelling of exactly 64 bytes. */
    public static boolean isCanonicalEd25519Signature(String text) {
        return isCanonical(text, ED25519_SIGNATURE_BYTES);
    }

    /** {@code true} iff {@code text} is the canonical spelling of exactly {@code expectedBytes} bytes. */
    public static boolean isCanonical(String text, int expectedBytes) {
        final byte[] decoded = decodeStrictOrNull(text);
        return decoded != null && decoded.length == expectedBytes;
    }

    /**
     * Strict decoder for the canonical house form (see the class comment).
     *
     * @return the decoded bytes, or {@code null} when {@code text} is
     * {@code null} or is not canonical. The empty string decodes to zero
     * bytes.
     */
    public static byte[] decodeStrictOrNull(String text) {
        if (text == null) {
            return null;
        }
        final int len = text.length();
        if (len % 4 != 0) {
            return null;
        }
        if (len == 0) {
            return new byte[0];
        }
        int pad = 0;
        if (text.charAt(len - 1) == PADDING) {
            pad = text.charAt(len - 2) == PADDING ? 2 : 1;
        }
        final int dataChars = len - pad;
        final byte[] out = new byte[len / 4 * 3 - pad];
        int o = 0;
        int acc = 0;
        int bits = 0;
        for (int i = 0; i < dataChars; i++) {
            final int v = sextet(text.charAt(i));
            if (v < 0) {
                // any other character: whitespace, '+', '/', '=', a '.' before
                // the last block, non-ASCII, ...
                return null;
            }
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[o++] = (byte) (acc >>> bits);
                acc &= (1 << bits) - 1;
            }
        }
        // What is left in acc are the unused bits of the last data character
        // (4 bits for "xx..", 2 for "xxx."): they must be zero.
        if (acc != 0) {
            return null;
        }
        return out;
    }

    private static int sextet(char c) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 26;
        }
        if (c >= '0' && c <= '9') {
            return c - '0' + 52;
        }
        if (c == '-') {
            return 62;
        }
        if (c == '_') {
            return 63;
        }
        return -1;
    }

    private static TkmCypherBean refused(String reason) {
        final TkmCypherBean bean = new TkmCypherBean();
        bean.setValid(false);
        bean.setEx(new IllegalArgumentException(reason));
        return bean;
    }
}
