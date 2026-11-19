package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallJson;
import io.takamaka.messages.call.CallStrictStringDeserializer;
import java.io.Closeable;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Plaintext of a channel box (spec §5.2 {@code body}, §6.4 handover, §10.1 conv_seed). Not signed itself: it
 * travels inside the AEAD box of a signed {@code channel} object.
 *
 * <p>Encoding (this implementation's resolution of ambiguity A-6 of the C182 status report): the plaintext is
 * the UTF-8 (ASCII) of the JCS canonical form of this object; the handover fields of §6.4 sit at the top level;
 * {@code conv_seed} (hex) is added when the recipient is an era-0 {@code inv} leg; an "empty" body is {@code {}}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallChannelBodyBean {

    /**
     * Body kind: the handovers {@code epoch} | {@code broadcast}, the catch-up request {@code catchup}
     * ({@link io.takamaka.messages.call.CallConstants#H_CATCHUP}) and, from protocol 1.1, the media notice
     * {@code media} ({@link io.takamaka.messages.call.CallConstants#H_MEDIA}); absent when the body carries none. From
     * audio-only S1 a RECEIVED body whose {@code h} is any other string is an ignored kind
     * ({@link #parseChannelBody(String)}).
     */
    @JsonProperty("h")
    private String h;
    @JsonProperty("epoch")
    private Long epoch;
    /** Era hash. */
    @JsonProperty("era")
    private String era;
    /** Sorted ann_hash list of the roster (epoch handover). */
    @JsonProperty("roster")
    private List<String> roster;
    /** epoch_secret_e, hex (epoch handover). */
    @JsonProperty("secret")
    private String secret;
    @JsonProperty("chain_idx")
    private Long chainIdx;
    /**
     * [0.2] Epoch handover only: the committer's milliseconds elapsed since the last fresh commit it applied (§6.4,
     * shell oracle finding O-11). The newcomer starts its budget window at {@code now − since}.
     */
    @JsonProperty("since")
    private Long since;
    /**
     * [0.2] (2026-10-08) Epoch handover only, required: the SIGNED {@code ts} (ms since the Unix epoch) of the fresh
     * commit that produced the handed-over secret (§6.4 step 3). The adopting leg measures its §6.2 budget window from
     * it — a committer timestamp — never from its own clock minus {@code since}, so a late handover cannot make it
     * refuse the next commit.
     */
    @JsonProperty("prev_ts")
    private Long prevTs;
    /**
     * [0.3] S-1 (2026-10-13): the catch-up REQUEST carries a fresh random {@code req} (16 bytes hex); the catch-up
     * handover echoes it, and the requester accepts a catch-up handover only with the {@code req} of its outstanding
     * request. Absent on a join handover (gated by the phase and the sender instead).
     */
    @JsonProperty("req")
    private String req;
    /** broadcast_e, hex (broadcast handover). */
    @JsonProperty("key")
    private String key;
    /** conv_seed, hex (spec §10.1). */
    @JsonProperty("conv_seed")
    private String convSeed;
    /**
     * Audio-only calls S0 (design AUDIO_ONLY_CALLS_DESIGN_2026-11-19 §5/§6, ruling R-1; spec §5.3; protocol 1.1): the
     * field of the media notice {@code {"h":"media","media":"video"|"audio"}} — the SENDER's own declared media
     * intent ({@link io.takamaka.messages.call.CallConstants#MEDIA_VIDEO} /
     * {@link io.takamaka.messages.call.CallConstants#MEDIA_AUDIO}).
     *
     * <p>LABELLING ONLY: a receiver uses it to say why no video arrives («by choice»); it never gates rendering,
     * decryption or any key-plane step. Speakers only: a listener never sends it and never receives one, and a
     * receiver clears a leg's declaration on a role change. It is a string so that other values can come later: this
     * bean does NOT refuse an unknown value, and a receiver treats one as «unknown cause». No counter: the channel's
     * own {@code seq} orders and de-duplicates the notices.
     */
    @JsonProperty("media")
    @JsonDeserialize(using = CallStrictStringDeserializer.class)
    private String media;

    /**
     * The media declaration a RECEIVER takes from this body (audio-only S0, ruling of 2026-11-19): {@code media} is
     * read ONLY when {@code h} is exactly {@code "media"} ({@link io.takamaka.messages.call.CallConstants#H_MEDIA}).
     *
     * @return the declared value as received — {@code "audio"}, {@code "video"}, or any other string (an unknown
     * value, the empty string included: «unknown cause») — and {@code null} when the body declares nothing: any body
     * whose {@code h} is not {@code "media"} (absent, {@code catchup}, a handover, … — a {@code media} member there is
     * ignored), and an {@code h:"media"} body without {@code media} (absent or JSON {@code null}; «no declaration» =
     * unknown cause). Not a bean property: it is never serialised.
     */
    public String declaredMedia() {
        return io.takamaka.messages.call.CallConstants.H_MEDIA.equals(h) ? media : null;
    }

    /**
     * The body kinds ({@code h}) this version knows: the handovers {@code epoch} and {@code broadcast}, the catch-up
     * request {@code catchup} and the media notice {@code media}. Each keeps the full strict schema. Case-sensitive.
     */
    public static final Set<String> KNOWN_KINDS = Set.of(CallConstants.H_EPOCH, CallConstants.H_BROADCAST,
            CallConstants.H_CATCHUP, CallConstants.H_MEDIA);

    /**
     * The members an UNKNOWN-kind body keeps besides {@code h} ({@link #parseChannelBody(String)}): the two that carry
     * meaning on a channel OPEN whatever the kind, {@code epoch} (O-7) and {@code conv_seed} (§10.1).
     */
    public static final Set<String> UNKNOWN_KIND_KEEPS = Set.of("epoch", "conv_seed");

    /** True when {@code h} is one of {@link #KNOWN_KINDS}. */
    public static boolean isKnownKind(String h) {
        return h != null && KNOWN_KINDS.contains(h);
    }

    /** True when this body's {@code h} is one of {@link #KNOWN_KINDS}. Not a bean property. */
    public boolean knownKind() {
        return isKnownKind(h);
    }

    /**
     * True when this body's {@code h} is a string this version does not know: the kind is IGNORED (on a message) or
     * treated as a plain non-handover open (on an open). Dart {@code CallChannelBody.ignoredKind}. Not a bean property.
     */
    public boolean ignoredKind() {
        return h != null && !KNOWN_KINDS.contains(h);
    }

    /**
     * THE parser of a RECEIVED channel body (the box plaintext), for every Java reader of a channel body (Messages
     * {@code HybridChannel} for opens, rsclient {@code CallClientState} for messages). Audio-only S1, Iris §7.189 F1,
     * design AUDIO_ONLY_CALLS_DESIGN_2026-11-19 §6 «The exact parse spec»; Dart {@code CallChannelBody.parse}.
     *
     * <ol>
     * <li>The text is decoded as strict JSON first (the {@link CallJson} mapper's parser: duplicate keys refused); a
     * non-object is refused.</li>
     * <li>If {@code h} is present, a JSON string and not one of {@link #KNOWN_KINDS} (case-sensitive), the body is an
     * UNKNOWN kind, decided before any field or schema check: it keeps only {@code h}, {@code epoch} and
     * {@code conv_seed} and drops every other member silently (unknown ones and known ones of other kinds, such as
     * {@code secret}, {@code roster}, {@code media}). The kept members stay strictly typed: {@code epoch} is a JSON
     * integer in the safe range ±(2^53 − 1) (a string, a fraction or a float form is refused), {@code conv_seed} is a
     * JSON string (a number is refused); JSON {@code null} means absent.</li>
     * <li>A known {@code h}, an absent {@code h} and a non-string {@code h} (JSON {@code null} included): the full strict
     * schema, unchanged ({@code CallJson.parse(json, CallChannelBodyBean.class)}).</li>
     * </ol>
     *
     * <p>The canonical form ({@link CallJson#canonical(Object)}) of a parsed unknown body is the JCS of the kept
     * members only, e.g. {@code {"conv_seed":"ab","epoch":7,"h":"future"}}; its {@link #declaredMedia()} is null.
     *
     * @throws JsonProcessingException on any refusal (the same exception type as the strict bean parse)
     */
    public static CallChannelBodyBean parseChannelBody(String json) throws JsonProcessingException {
        JsonNode tree = CallJson.mapper().readTree(json);
        if (tree == null || !tree.isObject()) {
            throw refusal("channel body: not a JSON object");
        }
        JsonNode hNode = tree.get("h");
        if (hNode == null || !hNode.isTextual() || isKnownKind(hNode.textValue())) {
            return CallJson.parse(json, CallChannelBodyBean.class);
        }
        CallChannelBodyBean b = new CallChannelBodyBean();
        b.setH(hNode.textValue());
        JsonNode e = tree.get("epoch");
        if (e != null && !e.isNull()) {
            if (!e.isIntegralNumber() || !e.canConvertToLong()
                    || e.longValue() > MAX_SAFE_INTEGER || e.longValue() < -MAX_SAFE_INTEGER) {
                throw refusal("channel body (unknown kind): epoch is not a safe integer");
            }
            b.setEpoch(e.longValue());
        }
        JsonNode s = tree.get("conv_seed");
        if (s != null && !s.isNull()) {
            if (!s.isTextual()) {
                throw refusal("channel body (unknown kind): conv_seed is not a string");
            }
            b.setConvSeed(s.textValue());
        }
        return b;
    }

    /** 2^53 − 1: the integer range every port reads exactly (Dart {@code CallBytes.maxSafeInt}). */
    private static final long MAX_SAFE_INTEGER = (1L << 53) - 1;

    private static JsonMappingException refusal(String message) {
        return new JsonMappingException((Closeable) null, message);
    }
}
