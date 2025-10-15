package io.takamaka.messages.chat.mention;

import io.takamaka.messages.chat.mention.AddressValidation.KeyType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AddressValidation utility.
 *
 * @since 1.3.0
 */
class AddressValidationTest {

    // Sample keys for testing
    private static final String ED25519_KEY = "7tVZucubIlMg2eWX6CzOOOU1-GMPQmNFK-R4i1tczms.";
    private static final String SHORT_B64_KEY = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6A7B8C9D0E1F2";
    private static final String SHORT_HEX_KEY = "f54848b4c119be770f87bc5ef54848b4c119be770f87bc5ef54848b4c119be770f87bc5ef54848b4c119be770f87bc5e";

    // Create a qTesla key (19,840 chars) - using repeated pattern for testing
    private static final String QTESLA_KEY = "a".repeat(19840);

    @Test
    void testEd25519Valid() {
        assertTrue(AddressValidation.isValidPublicKey(ED25519_KEY));
    }

    @Test
    void testQTeslaValid() {
        assertTrue(AddressValidation.isValidPublicKey(QTESLA_KEY));
    }

    @Test
    void testShortB64Valid() {
        assertTrue(AddressValidation.isValidPublicKey(SHORT_B64_KEY));
    }

    @Test
    void testShortHexValid() {
        assertTrue(AddressValidation.isValidPublicKey(SHORT_HEX_KEY));
    }

    @Test
    void testInvalidLength() {
        String invalidKey = "tooshort";
        assertFalse(AddressValidation.isValidPublicKey(invalidKey));
    }

    @Test
    void testInvalidCharacters() {
        // 44 chars but with invalid characters for Ed25519
        String invalidKey = "7tVZ!@#$%^&*()_+{}|:<>?[];',./\\~`1234567890";
        assertFalse(AddressValidation.isValidPublicKey(invalidKey));
    }

    @Test
    void testGetKeyTypeEd25519() {
        assertEquals(KeyType.ED25519, AddressValidation.getKeyType(ED25519_KEY));
    }

    @Test
    void testGetKeyTypeQTesla() {
        assertEquals(KeyType.QTESLA, AddressValidation.getKeyType(QTESLA_KEY));
    }

    @Test
    void testGetKeyTypeShortB64() {
        assertEquals(KeyType.SHORT_B64, AddressValidation.getKeyType(SHORT_B64_KEY));
    }

    @Test
    void testGetKeyTypeShortHex() {
        assertEquals(KeyType.SHORT_HEX, AddressValidation.getKeyType(SHORT_HEX_KEY));
    }

    @Test
    void testQTeslaAbbreviation() {
        String abbreviated = AddressValidation.abbreviateForDisplay(QTESLA_KEY);

        // Should return 96-char hex hash
        assertNotNull(abbreviated);
        assertEquals(96, abbreviated.length(),
            "qTesla key should be abbreviated to 96-char hex");

        // Should be different from original (much shorter)
        assertNotEquals(QTESLA_KEY, abbreviated);
    }

    @Test
    void testEd25519Abbreviation() {
        String abbreviated = AddressValidation.abbreviateForDisplay(ED25519_KEY);

        // Ed25519 should return full key
        assertEquals(ED25519_KEY, abbreviated);
    }

    @Test
    void testShortB64Abbreviation() {
        String abbreviated = AddressValidation.abbreviateForDisplay(SHORT_B64_KEY);

        // Short B64 should return full key
        assertEquals(SHORT_B64_KEY, abbreviated);
    }

    @Test
    void testShortHexAbbreviation() {
        String abbreviated = AddressValidation.abbreviateForDisplay(SHORT_HEX_KEY);

        // Short Hex should return full key
        assertEquals(SHORT_HEX_KEY, abbreviated);
    }

    @Test
    void testNullKey() {
        assertFalse(AddressValidation.isValidPublicKey(null));
        assertEquals(KeyType.INVALID, AddressValidation.getKeyType(null));
        assertEquals("", AddressValidation.abbreviateForDisplay(null));
    }

    @Test
    void testEmptyKey() {
        assertFalse(AddressValidation.isValidPublicKey(""));
        assertEquals(KeyType.INVALID, AddressValidation.getKeyType(""));
        assertEquals("", AddressValidation.abbreviateForDisplay(""));
    }

    @Test
    void testGetKeyTypeInvalid() {
        String invalidKey = "invalid123";
        assertEquals(KeyType.INVALID, AddressValidation.getKeyType(invalidKey));
    }

    @Test
    void testKeyTypeLengthProperty() {
        assertEquals(44, KeyType.ED25519.getLength());
        assertEquals(64, KeyType.SHORT_B64.getLength());
        assertEquals(96, KeyType.SHORT_HEX.getLength());
        assertEquals(19840, KeyType.QTESLA.getLength());
        assertEquals(0, KeyType.INVALID.getLength());
    }

    @Test
    void testKeyTypeRegexProperty() {
        assertNotNull(KeyType.ED25519.getRegex());
        assertNotNull(KeyType.SHORT_B64.getRegex());
        assertNotNull(KeyType.SHORT_HEX.getRegex());
        assertNotNull(KeyType.QTESLA.getRegex());
        assertEquals("", KeyType.INVALID.getRegex());
    }
}
