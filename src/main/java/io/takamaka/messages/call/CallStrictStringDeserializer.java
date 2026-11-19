package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/**
 * A string field that accepts ONLY a JSON string. Jackson's own {@code String} deserializer turns a JSON number or
 * boolean into its text ({@code true} → {@code "true"}, {@code 1} → {@code "1"}) even on the strict call mapper
 * ({@link CallJson}); the Dart port's schema refuses a non-string ({@code not a string}). For a field of an UNSIGNED
 * object (the channel body) nothing else would catch the difference, so the fields added by audio-only S0
 * ({@code media} of the ring and of the channel body) use this deserializer: Java and Dart refuse the same inputs.
 *
 * <p>Since the strict mapper refuses number/boolean → {@code String} coercion mapper-wide (the
 * {@code LogicalType.Textual} coercion config of {@link CallJson}), this deserializer is belt-and-braces: it keeps
 * the two {@code media} fields strict even under a mapper that lacks that config.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class CallStrictStringDeserializer extends StdDeserializer<String> {

    private static final long serialVersionUID = 1L;

    public CallStrictStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() != JsonToken.VALUE_STRING) {
            return (String) ctxt.handleUnexpectedToken(String.class, p);
        }
        return p.getText();
    }
}
