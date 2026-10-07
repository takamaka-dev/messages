package io.takamaka.messages.call;

/**
 * Route names of the call service (spec §8; workstream C182, DR-055), all prefixed {@code call.}. The call service is its own RSocket-over-WebSocket
 * server (its own socket, logs and heap — Design §6.1), so the route names live in their own namespace and are
 * deliberately distinct from {@code io.takamaka.messages.chat.constant.ChatServerEndpoints}: a chat client can never
 * reach a call route by accident and vice versa.
 *
 * <p>Payloads are the JSON of the call objects of spec §3.3 exactly as signed (parsed strictly by
 * {@link CallJson#parse(String, Class)}); answers are the wrappers of {@code io.takamaka.messages.call.service}:
 * {@link io.takamaka.messages.call.service.CallReplyBean} for request-response routes, a stream of
 * {@link io.takamaka.messages.call.service.CallEventBean} for {@link #ANNOUNCE}. Every refusal is a typed §11 code
 * ({@link io.takamaka.messages.call.service.CallErrorBean}), never free text and never an RSocket error carrying text.
 *
 * <table>
 * <caption>Routes</caption>
 * <tr><th>Route</th><th>Pattern</th><th>Payload</th><th>Answer</th></tr>
 * <tr><td>{@value #NONCE}</td><td>request-response</td><td>none</td><td>reply with {@code n} (§8.1)</td></tr>
 * <tr><td>{@value #MANIFEST}</td><td>request-response</td><td>none</td><td>the signed manifest (§8.6)</td></tr>
 * <tr><td>{@value #CREATE}</td><td>request-response</td><td>{@code create}</td><td>reply with {@code call}, {@code era}</td></tr>
 * <tr><td>{@value #ERA}</td><td>request-response</td><td>{@code era}</td><td>reply with {@code era}</td></tr>
 * <tr><td>{@value #ANNOUNCE}</td><td>request-stream</td><td>{@code announce}</td><td>{@code joined} then live events
 * (§7.3); a re-announcement on a leg whose stream is live answers {@code ok} and completes</td></tr>
 * <tr><td>{@value #CHANNEL}</td><td>request-response</td><td>{@code channel}</td><td>reply; relayed opaque to {@code to}</td></tr>
 * <tr><td>{@value #COMMIT}</td><td>request-response</td><td>{@code commit}</td><td>reply; {@code epoch_taken} if not first</td></tr>
 * <tr><td>{@value #GOODBYE}</td><td>request-response</td><td>{@code goodbye}</td><td>reply</td></tr>
 * <tr><td>{@value #DECLINE}</td><td>request-response</td><td>{@code decline}</td><td>reply</td></tr>
 * <tr><td>{@value #MUTE} / {@value #UNMUTE}</td><td>request-response</td><td>{@code mute} / {@code unmute}</td><td>reply</td></tr>
 * <tr><td>{@value #PRESENCE}</td><td>request-response</td><td>{@link io.takamaka.messages.call.service.CallLegRefBean}</td>
 * <td>reply with {@code presence}; only on the connection that holds that leg's stream</td></tr>
 * </table>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallServiceEndpoints {

    private CallServiceEndpoints() {
    }

    /** Default WebSocket mapping path of the call service. */
    public static final String MAPPING_PATH = "/callsvc";

    public static final String NONCE = "call.nonce";
    public static final String MANIFEST = "call.manifest";
    public static final String CREATE = "call.create";
    public static final String ERA = "call.era";
    public static final String ANNOUNCE = "call.announce";
    public static final String CHANNEL = "call.channel";
    public static final String COMMIT = "call.commit";
    public static final String GOODBYE = "call.goodbye";
    public static final String DECLINE = "call.decline";
    public static final String MUTE = "call.mute";
    public static final String UNMUTE = "call.unmute";
    public static final String PRESENCE = "call.presence";
}
