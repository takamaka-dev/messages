package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
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
