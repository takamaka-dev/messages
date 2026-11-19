package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.util.AccessPattern;
import java.io.IOException;

/**
 * A {@code long} / {@code Long} of a call object: Jackson's own reading (every refusal of {@link CallJson}'s config
 * kept: no float, no string, no {@code null} for a primitive), then the I-JSON / RFC 8785 interoperable range
 * ±(2^53−1) = ±{@value #MAX_SAFE}. The Dart port's {@code StrictJson} refuses an integer outside that range at
 * parse time (it cannot be represented exactly on the web); without this check Java read it into a {@code long}.
 *
 * <p>Registered on the strict call mapper for {@code long} and {@code Long}, so it covers every field of every call
 * bean, nested beans and collection elements ({@code List<Long>}, {@code Map<String, Long>}) alike. A refusal is an
 * {@link InvalidFormatException} (a {@code MismatchedInputException}, hence a {@code JsonProcessingException}),
 * thrown during the parse: a signed object with an out-of-range field never reaches the verifier. Serialisation is
 * untouched. ({@code int} / {@code Integer} need no check: Jackson already refuses beyond 32 bits, inside the range.)
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallSafeLongDeserializer extends StdDeserializer<Long> {

    private static final long serialVersionUID = 1L;

    /** 2^53 − 1, the largest integer every JSON implementation reads exactly (RFC 7493 §2.2). */
    public static final long MAX_SAFE = 9007199254740991L;

    /** For {@code Long}. */
    public static final CallSafeLongDeserializer WRAPPER = new CallSafeLongDeserializer(Long.class);
    /** For {@code long}. */
    public static final CallSafeLongDeserializer PRIMITIVE = new CallSafeLongDeserializer(Long.TYPE);

    private final JsonDeserializer<Long> standard;

    @SuppressWarnings("unchecked")
    private CallSafeLongDeserializer(Class<Long> type) {
        super(type);
        this.standard = (JsonDeserializer<Long>) NumberDeserializers.find(type, type.getName());
    }

    /** True if {@code v} is within ±(2^53−1). */
    public static boolean isSafe(long v) {
        return v >= -MAX_SAFE && v <= MAX_SAFE;
    }

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        Long v = standard.deserialize(p, ctxt);
        if (v != null && !isSafe(v)) {
            throw InvalidFormatException.from(p,
                    "integer outside the safe range ±(2^53-1) (I-JSON / RFC 8785)", v, Long.class);
        }
        return v;
    }

    // ---- null / empty handling and the logical type: exactly Jackson's own (a null for a primitive is refused) ----

    @Override
    public Long getNullValue(DeserializationContext ctxt) throws JsonMappingException {
        return standard.getNullValue(ctxt);
    }

    @Override
    public AccessPattern getNullAccessPattern() {
        return standard.getNullAccessPattern();
    }

    @Override
    public Object getEmptyValue(DeserializationContext ctxt) throws JsonMappingException {
        return standard.getEmptyValue(ctxt);
    }

    @Override
    public AccessPattern getEmptyAccessPattern() {
        return standard.getEmptyAccessPattern();
    }

    @Override
    public LogicalType logicalType() {
        return standard.logicalType();
    }

    @Override
    public boolean isCachable() {
        return true;
    }
}
