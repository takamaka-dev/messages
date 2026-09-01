package io.takamaka.messages.beans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.takamaka.messages.utils.ActionType;
import io.takamaka.messages.beans.implementation.PayRequestAction;
import io.takamaka.messages.utils.SimpleRequestHelper;
import io.takamaka.messages.utils.SimpleRequestModels;

/**
 * DR-034 — the wire form of the signed {@code MessageAction}, asserted on the
 * reference implementation itself.
 *
 * <p><b>Why this file exists.</b> The signed bytes are
 * {@code SimpleRequestHelper.getRequestJsonCompact(action)}, and until now the
 * only thing in the estate that exercised them was {@code Messages.main()} — a
 * manual demo harness, not a test. A signature-covered format with no
 * regression test is one refactor away from changing silently, and the
 * consumer that would notice is a Dart client in another repository.
 *
 * <p><b>What DR-034 settled.</b> {@code g} / {@code r} ride as decimal
 * <b>strings</b>; {@code dt} stays a bare number. JSON's number type is
 * underspecified and bounded in practice by the consumer's parser: Jackson is
 * exact with a bare {@code BigInteger} only because it retains the token's
 * literal text, and a consumer whose parser discards that text (Dart's
 * {@code jsonDecode}) cannot recover a large value at all. A decimal string is
 * exact on every platform.
 *
 * <p>The mirror of this file is {@code wallet-core-flutter}
 * {@code test/messages/java_message_action_parity_test.dart}, which pins the
 * Dart port to vectors generated from this very method. Change one and the
 * other goes red — which is the point.
 */
@DisplayName("DR-034 — MessageAction signed wire form")
class MessageActionWireFormTest {

    private static final String HUGE = "123456789012345678901234567890";
    private static final String ED25519 = "4mfAa-hIJBU8_iU7IUDIgQpDZCpBVNp7oyCsMED6Y4A.";

    private static PayRequestAction payAction(BigInteger green, BigInteger red, String text) {
        PayRequestAction a = new PayRequestAction(
                new MessageAddress("c", "abcdefghijkl"), green, red, text);
        a.setFrom(new MessageAddress("f", "AAAAsignerAAAA."));
        a.setDate(1756000000000L);
        return a;
    }

    @Test
    @DisplayName("amounts are emitted as strings; dt stays a bare number")
    void amountsAreStrings() throws Exception {
        String json = SimpleRequestHelper.getRequestJsonCompact(
                payAction(new BigInteger("1000000000000"), BigInteger.valueOf(25), "coffee"));

        assertTrue(json.contains("\"g\":\"1000000000000\""), json);
        assertTrue(json.contains("\"r\":\"25\""), json);
        // Scoped to the two arbitrary-precision fields only.
        assertTrue(json.contains("\"dt\":1756000000000"), json);
        assertFalse(json.matches(".*\"[gr]\":[0-9].*"), "no bare amount may survive: " + json);
    }

    @Test
    @DisplayName("field order is the declaration order the signature depends on")
    void declarationOrderIsStable() throws Exception {
        // The signed form is plain Jackson, NOT JCS — order comes from the
        // field declarations, so a reordering of the bean is a wire change.
        assertEquals(
                "{\"fr\":{\"t\":\"f\",\"ma\":\"AAAAsignerAAAA.\"},"
                + "\"to\":{\"t\":\"c\",\"ma\":\"abcdefghijkl\"},"
                + "\"dt\":1756000000000,\"g\":\"1000000000000\",\"r\":\"25\",\"tm\":\"coffee\"}",
                SimpleRequestHelper.getRequestJsonCompact(
                        payAction(new BigInteger("1000000000000"), BigInteger.valueOf(25), "coffee")));
    }

    @Test
    @DisplayName("an amount far beyond int64 round-trips exactly")
    void arbitraryPrecisionRoundTrips() throws Exception {
        String json = SimpleRequestHelper.getRequestJsonCompact(
                payAction(new BigInteger(HUGE), null, "beyond int64"));
        assertTrue(json.contains("\"g\":\"" + HUGE + "\""), json);

        MessageAction back = SimpleRequestHelper.fromJsonToMessageAction(json);
        assertEquals(HUGE, back.getGreen().toString());
        assertEquals(json, SimpleRequestHelper.getRequestJsonCompact(back));
    }

    @Test
    @DisplayName("zero is emitted, null is omitted — unchanged by DR-034")
    void zeroAndNullBehaviour() throws Exception {
        String zero = SimpleRequestHelper.getRequestJsonCompact(
                payAction(BigInteger.ZERO, BigInteger.ZERO, "zero"));
        assertTrue(zero.contains("\"g\":\"0\""), zero);
        assertTrue(zero.contains("\"r\":\"0\""), zero);

        String greenOnly = SimpleRequestHelper.getRequestJsonCompact(
                payAction(new BigInteger("5000000000000"), null, null));
        assertFalse(greenOnly.contains("\"r\""), "null red must be omitted: " + greenOnly);
        assertFalse(greenOnly.contains("\"tm\""), "null text must be omitted: " + greenOnly);
    }

    @Test
    @DisplayName("the legacy bare-number form still parses, and re-emits as a string")
    void legacyBareFormIsStillReadable() throws Exception {
        // Payloads minted before DR-034 must keep parsing, permanently. Jackson
        // coerces a bare number into BigInteger with no configuration, which is
        // why the flip needed no reader change on this side.
        String current = SimpleRequestHelper.getRequestJsonCompact(
                payAction(new BigInteger("1000000000000"), BigInteger.valueOf(25), "coffee"));
        String legacy = current
                .replace("\"g\":\"1000000000000\"", "\"g\":1000000000000")
                .replace("\"r\":\"25\"", "\"r\":25");

        // Control: the legacy form really is a different string, or this test
        // would be re-checking the current form and proving nothing.
        assertNotEquals(current, legacy);

        MessageAction back = SimpleRequestHelper.fromJsonToMessageAction(legacy);
        assertEquals("1000000000000", back.getGreen().toString());
        assertEquals("25", back.getRed().toString());
        assertEquals(current, SimpleRequestHelper.getRequestJsonCompact(back),
                "a legacy payload must normalise to the DR-034 form on re-emit");
    }

    @Test
    @DisplayName("QR-03 — every model factory stamps its OWN action type")
    void factoriesStampTheirOwnType() throws Exception {
        // getSimpleStakeRequest_V_1_0 and getSimpleStakeUndoRequest_V_1_0 both
        // stamped REQUEST_PAY until 2026-09-01, so a stake went out as
        // {"t":"rp"} and a consumer dispatching on `t` -- the field's only
        // purpose -- processed it as a payment. ActionType.STAKE/STAKE_UNDO
        // existed and were simply not referenced.
        //
        // Asserted across ALL SIX factories rather than only the two that were
        // wrong: the defect was copy-paste, so the guard has to cover the shape,
        // not the instance.
        assertEquals("rp", SimpleRequestModels
                .getSimplePayRequest_V_1_0(ED25519, BigInteger.TEN, null, "pay").getTypeOfAction());
        assertEquals("st", SimpleRequestModels
                .getSimpleStakeRequest_V_1_0(ED25519, BigInteger.TEN, "stake").getTypeOfAction());
        assertEquals("su", SimpleRequestModels
                .getSimpleStakeUndoRequest_V_1_0(0L, "undo").getTypeOfAction());
        assertEquals("b", SimpleRequestModels
                .getSimpleBlobRequest_V_1_0("blob").getTypeOfAction());
    }

    @Test
    @DisplayName("QR-03 — the six type codes are distinct, so `t` can dispatch at all")
    void typeCodesAreDistinct() throws Exception {
        // Control: if two factories ever collapse onto one code again, the
        // assertions above could still pass individually while `t` stopped
        // being a discriminator.
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (ActionType t : ActionType.values()) {
            assertTrue(codes.add(t.getShortCode()),
                    "duplicate action short code: " + t.getShortCode());
        }
        assertTrue(codes.size() >= 6, "expected at least the six documented codes, got " + codes);
    }

    @Test
    @DisplayName("a legacy bare amount beyond int64 also still parses on this side")
    void legacyBareFormIsExactEvenWhenHuge() throws Exception {
        // Jackson keeps the token's literal text, so Java never had the
        // precision ceiling that made DR-034 necessary for Dart. Pinned so the
        // asymmetry is visible in code rather than only in the decision record.
        String legacy = "{\"fr\":{\"t\":\"f\",\"ma\":\"AAAAsignerAAAA.\"},"
                + "\"to\":{\"t\":\"c\",\"ma\":\"abcdefghijkl\"},"
                + "\"dt\":1756000000000,\"g\":" + HUGE + ",\"tm\":\"beyond int64\"}";
        MessageAction back = SimpleRequestHelper.fromJsonToMessageAction(legacy);
        assertEquals(HUGE, back.getGreen().toString());
    }
}
