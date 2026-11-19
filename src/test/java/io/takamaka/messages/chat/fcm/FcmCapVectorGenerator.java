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
package io.takamaka.messages.chat.fcm;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.messages.chat.constant.ChatServerEndpoints;
import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.chat.core.ServerInfoResponseBean;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
import io.takamaka.messages.utils.SimpleRequestHelper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Generates {@code java_fcm_cap_vectors.json} (DR-074 amendment 1, A-16): the Java reference verdicts on the
 * {@code cap} member of the FCM registration content, shared byte for byte with rsclient-flutter (and read by the
 * wallet's test). Deterministic: the fixed input of {@code FcmCanonicalVectorTest} ({@code VECTOR_WITH_DEVICE}).
 *
 * <p>Each row carries {@code expect} = {@code FcmCapabilities.validate(cap, null)} as a code ({@code OK} or
 * {@code INVALID_CAP}; what a SIGNER does before signing), {@code canonical} (the JCS form of the signed content,
 * only when {@code OK}), {@code stored_form} ({@code FcmCapabilities.join}, only when {@code OK}), and
 * {@code expect_server} = {@code validate(cap, server_known)} (what {@code registerfcmtoken} answers on a server that
 * advertises {@code server_known}). No free-text rule: the code is the contract (C-3).
 * {@link FcmCapVectorFileTest} asserts the committed file equals {@link #render()}, and that every verdict is the one
 * the amendment asks for.</p>
 *
 * <p>Regenerate deliberately: {@code mvn -o test -Dtest=FcmCapVectorFileTest -Dfcm.cap.vectors.write=true}, or run
 * {@link #main} with the target path(s) (this file and the rsclient-flutter fixtures copy).</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class FcmCapVectorGenerator {

    private FcmCapVectorGenerator() {
    }

    /** Non-ASCII input is written as {@code \\uXXXX}: the file stays ASCII. */
    static final ObjectMapper M = JsonMapper.builder().enable(JsonWriteFeature.ESCAPE_NON_ASCII).build();

    // the fixed input of FcmCanonicalVectorTest.VECTOR_WITH_DEVICE (keep identical)
    static final NonceResponseBean NONCE = new NonceResponseBean(
            "11111111-2222-3333-4444-555555555555", 1700000000000L, 60000L);
    static final String TOKEN = "fixed-fcm-token-XYZ";
    static final String PLATFORM = "android";
    static final String DEVICE_ID = "device-01";
    static final Set<String> SERVER_KNOWN = Set.of(FcmCapabilities.CALLS);

    static final String OK = "OK";

    /** A list that may hold a null element (List.of refuses one). */
    static List<String> list(String... values) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values)));
    }

    static String code(String validateResult) {
        return validateResult == null ? OK : validateResult;
    }

    /** The cap of a row: null when the row has no cap ({@code cap_present = false}); null elements kept. */
    static List<String> capOf(JsonNode row) {
        if (!row.get("cap_present").asBoolean()) {
            return null;
        }
        return M.convertValue(row.get("cap"), new TypeReference<List<String>>() {
        });
    }

    static String canonical(List<String> cap) throws Exception {
        return SimpleRequestHelper.getCanonicalJson(
                new FcmTokenRegistrationSignedContentBean(NONCE, TOKEN, PLATFORM, DEVICE_ID, cap));
    }

    private static void row(ArrayNode rows, String id, String kind, String description, List<String> cap) throws Exception {
        ObjectNode r = rows.addObject();
        r.put("id", id);
        r.put("kind", kind);
        r.put("description", description);
        r.put("cap_present", cap != null);
        if (cap != null) {
            ArrayNode a = r.putArray("cap");
            for (String v : cap) {
                a.add(v);
            }
        }
        String expect = code(FcmCapabilities.validate(cap, null));
        r.put("expect", expect);
        if (OK.equals(expect)) {
            r.put("canonical", canonical(cap));
            r.put("stored_form", FcmCapabilities.join(cap));
        }
        r.put("expect_server", code(FcmCapabilities.validate(cap, SERVER_KNOWN)));
    }

    public static String render() throws Exception {
        ObjectNode root = M.createObjectNode();
        root.put("version", "1.0.0");
        root.put("generated_by", "FcmCapVectorGenerator.java");
        root.put("source", "Messages (Java reference)");
        root.put("decision", "DR-074 amendment 1 (draft 3): the calls capability 'cap' at FCM registration; "
                + "serverinfo.fcmTokenCapabilities (manifest 1.8)");

        ObjectNode k = root.putObject("cap_constants");
        k.put("endpoint", ChatServerEndpoints.REGISTER_FCM_TOKEN);
        k.put("message_type", CHAT_MESSAGE_TYPES.FCM_TOKEN_REGISTRATION.name());
        k.put("content_key", "fcm_token_registration_signed_content");
        k.put("cap_key", "cap");
        k.put("calls", FcmCapabilities.CALLS);
        k.put("error_invalid_cap", FcmCapabilities.ERROR_INVALID_CAP);
        k.put("max_values", FcmCapabilities.MAX_VALUES);
        k.put("max_value_length", FcmCapabilities.MAX_VALUE_LENGTH);
        k.put("value_regex", FcmCapabilities.VALUE_REGEX);
        k.put("storage_check_regex", FcmCapabilities.storageCheckRegex());
        k.put("max_stored_length", FcmCapabilities.maxStoredLength());
        k.put("note", "cap is absent, or 1 to max_values strings, each matching value_regex over the WHOLE string "
                + "(Java matches(), never find(); no lower-casing or other normalisation first), in strictly ascending "
                + "code-point order (so unique). An empty list is absent and is never sent: the canonical form omits "
                + "the key (NON_EMPTY on the cap field only; device_id keeps its explicit null). 'expect' is the "
                + "signer's verdict (shape only): OK, or INVALID_CAP refused BEFORE signing, never a throw. "
                + "'canonical' and 'stored_form' are given only when expect is OK. 'expect_server' is the "
                + "registerfcmtoken verdict on a server advertising 'server_known' (unknown values are refused, never "
                + "dropped); an INVALID_CAP refusal spends no nonce. Rows of kind 'normative' must give the same "
                + "verdict (and canonical) in Java and Dart; the others are builder, server and boundary checks.");
        ArrayNode known = root.putArray("server_known");
        SERVER_KNOWN.stream().sorted().forEach(known::add);

        ObjectNode in = root.putObject("fixed_input");
        in.set("nonce", M.valueToTree(NONCE));
        in.put("fcm_token", TOKEN);
        in.put("platform", PLATFORM);
        in.put("device_id", DEVICE_ID);

        ArrayNode rows = root.putArray("vectors");
        row(rows, "V1", "normative", "cap absent: canonical byte-identical to FcmCanonicalVectorTest.VECTOR_WITH_DEVICE",
                null);
        row(rows, "V2", "normative", "the calls capability", list("calls"));
        row(rows, "V3", "normative", "unsorted: refused, never sorted", list("calls", "a"));
        row(rows, "V4", "normative", "duplicate: refused, never de-duplicated", list("calls", "calls"));
        row(rows, "V5", "builder", "empty list: the same value as absent, omitted (canonical = V1), never sent as []",
                list());
        row(rows, "V7", "normative", "upper-case: refused, never lower-cased", list("Calls"));
        row(rows, "V8a", "server", "9 values: more than max_values",
                list("a", "b", "c", "d", "e", "f", "g", "h", "i"));
        row(rows, "V8b", "server", "a 33-character value: longer than max_value_length",
                list("a".repeat(FcmCapabilities.MAX_VALUE_LENGTH + 1)));
        row(rows, "V9", "server", "a well-formed value this server does not advertise: shape OK, the server refuses",
                list("calls-2"));
        row(rows, "V12", "normative", "a null element: refused with INVALID_CAP, never a throw (Java List<String>, "
                + "Dart List<String?>)", list((String) null));
        row(rows, "V13", "normative", "a trailing line feed: refused (pins matches(), not find())", list("calls\n"));
        row(rows, "B1", "boundary", "max_values values of max_value_length characters: the longest stored form",
                list("a".repeat(32), "b".repeat(32), "c".repeat(32), "d".repeat(32),
                        "e".repeat(32), "f".repeat(32), "g".repeat(32), "h".repeat(32)));
        row(rows, "B2", "boundary", "a one-character value", list("a"));
        row(rows, "B3", "boundary", "hyphen-only values are well-formed (A-12.1)", list("-", "--"));
        row(rows, "B4", "boundary", "a prefix sorts first: calls < calls-2", list("calls", "calls-2"));
        row(rows, "B5", "boundary", "the reverse order of B4", list("calls-2", "calls"));
        row(rows, "B6", "boundary", "an empty string value", list(""));
        row(rows, "B7", "boundary", "a trailing space", list("calls "));
        row(rows, "B8", "boundary", "a comma inside a value (the stored separator)", list("calls,x"));
        row(rows, "B9", "boundary", "a non-ASCII letter (Turkish dotless i)", list("calıs"));
        row(rows, "B10", "boundary", "an underscore", list("calls_2"));

        ArrayNode hv = root.putArray("has_value_vectors");
        Object[][] has = {
            {"calls", "calls"}, {"calls-2", "calls"}, {"calls,other", "calls"}, {"a,calls", "calls"},
            {"calls-2,callsx", "calls"}, {"xcalls", "calls"}, {null, "calls"}, {"", "calls"}
        };
        for (Object[] h : has) {
            ObjectNode n = hv.addObject();
            n.put("stored", (String) h[0]);
            n.put("value", (String) h[1]);
            n.put("expect", FcmCapabilities.hasValue((String) h[0], (String) h[1]));
        }

        ObjectNode si = root.putObject("serverinfo_vectors");
        ServerInfoResponseBean s18 = new ServerInfoResponseBean();
        s18.setServerVersion("0.11.0-SNAPSHOT");
        s18.setProtocolCurrent("1.2");
        s18.setProtocolMajor(1);
        s18.setProtocolMinMinor(0);
        s18.setProtocolMaxMinor(2);
        s18.setManifestVersion(ServerInfoResponseBean.MANIFEST_VERSION_CURRENT);
        s18.setAudience("rschat:rig1");
        s18.setFcmTokenCapabilities(SERVER_KNOWN);
        si.put("manifest_version_current", ServerInfoResponseBean.MANIFEST_VERSION_CURRENT);
        si.put("member", "fcmTokenCapabilities");
        si.set("manifest_1_8_json", M.valueToTree(s18));
        ArrayNode e18 = si.putArray("manifest_1_8_expected_fcm_token_capabilities");
        SERVER_KNOWN.stream().sorted().forEach(e18::add);
        ObjectNode s17 = M.valueToTree(s18);
        s17.remove("fcmTokenCapabilities");
        s17.put("manifestVersion", "1.7");
        si.set("manifest_1_7_json", s17);
        si.putNull("manifest_1_7_expected_fcm_token_capabilities");
        si.put("note", "fcmTokenCapabilities means 'this server validates, stores and honours cap', NOT 'calls are "
                + "available'. A client sends cap = (its own capabilities) intersected with this set; a 1.7 server has "
                + "no member: read it as no capability (null), send no cap.");

        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentObjectsWith(new DefaultIndenter("  ", "\n"));
        pp.indentArraysWith(new DefaultIndenter("  ", "\n"));
        return M.writer(pp).writeValueAsString(root) + "\n";
    }

    public static void main(String[] args) throws Exception {
        String text = render();
        for (String a : args) {
            Path p = Paths.get(a);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, text);
            System.out.println("wrote " + p.toAbsolutePath() + " (" + text.length() + " chars)");
        }
    }
}
