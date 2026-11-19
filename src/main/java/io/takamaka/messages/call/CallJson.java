package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.takamaka.messages.call.beans.CallSignedObject;
import io.takamaka.messages.utils.SimpleRequestHelper;
import java.util.Arrays;

/**
 * JSON of call objects: the canonical form of spec §2.3 and a strict parser.
 *
 * <p>The canonical form is exactly {@link SimpleRequestHelper#getCanonicalJson(Object)} (Jackson compact output
 * re-canonicalised by {@code JsonCanonicalizer}, RFC 8785) applied to the object with the excluded fields
 * removed.
 *
 * <p>The parser is strict: unknown fields, duplicate keys, floats for integers and scalar coercions are refused.
 * Scalar coercions are refused in BOTH directions, mapper-wide: a JSON string is never read into a number or a
 * boolean ({@code ALLOW_COERCION_OF_SCALARS} off), and a JSON number or boolean is never read into a {@code String}
 * (the {@link LogicalType#Textual} coercion config below; Jackson's {@code String} deserializer would otherwise turn
 * {@code true} into {@code "true"} and {@code 1} into {@code "1"}, which the Dart port refuses as {@code not a
 * string}). This matters most for the UNSIGNED objects (the channel body, the service wire): on a signed object a
 * coerced value would not match the signature anyway.
 * Trailing content after the top-level value ({@code {"h":"future"} x}, {@code {}{}}) is refused
 * ({@code FAIL_ON_TRAILING_TOKENS}, on {@code readValue} and {@code readTree} alike); surrounding whitespace is JSON
 * and stays accepted. One string is one call object: nothing parses a stream of concatenated objects through here.
 * A verifier rebuilds the signed octets from the parsed bean, so any field it does not know could not be covered
 * anyway.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallJson {

    private static final ObjectMapper STRICT = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            // N1: nothing but whitespace after the top-level value, as the Dart StrictJson ('trailing content').
            // Honoured by readValue AND readTree on this mapper (ObjectMapper._readTreeAndClose checks it).
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, cfg -> cfg
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .disable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private CallJson() {
    }

    public static ObjectMapper mapper() {
        return STRICT;
    }

    /** Parses a call object; the class is chosen by {@code t}. */
    public static CallSignedObject parse(String json) throws JsonProcessingException {
        return STRICT.readValue(json, CallSignedObject.class);
    }

    public static <T> T parse(String json, Class<T> type) throws JsonProcessingException {
        return STRICT.readValue(json, type);
    }

    /** Compact wire JSON of any bean (not canonical; key order is Jackson's). */
    public static String toWire(Object bean) {
        try {
            return STRICT.writeValueAsString(bean);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** JCS canonical form of the bean with the named top-level fields removed. */
    public static String canonicalWithout(Object bean, String... excluded) {
        ObjectNode node = STRICT.valueToTree(bean);
        Arrays.stream(excluded).forEach(node::remove);
        try {
            return SimpleRequestHelper.getCanonicalJson(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** JCS canonical form of the whole bean. */
    public static String canonical(Object bean) {
        return canonicalWithout(bean);
    }
}
