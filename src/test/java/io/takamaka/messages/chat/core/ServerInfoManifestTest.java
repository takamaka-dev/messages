package io.takamaka.messages.chat.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The capability manifest is a CONTRACT with clients, so the two things that break them are pinned here:
 * the field names on the wire, and additivity.
 *
 * <p>Manifest 1.3 adds the DR-025 delete window. A client needs it because the window is
 * operator-configurable: one that hardcodes "2 days" silently drifts the moment it is retuned, offering a
 * delete that can only be rejected or hiding one that would have worked.</p>
 *
 * <p>Manifest 1.4 adds {@code supportedRoutes} — the routes a BUILD actually serves. It exists because a
 * version number cannot describe a partial deployment: a server can serve {@code deletemessage} but not
 * {@code retrievedeletions} (observed on the test VM, 2026-07-30), and without the set a client cannot
 * tell "this build has no such route" from "the call failed" — so it swallows both and a deletion
 * catch-up reports success having asked nobody.</p>
 */
class ServerInfoManifestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void manifestVersionIsCurrent() {
        assertEquals("1.4", ServerInfoResponseBean.MANIFEST_VERSION_CURRENT,
                "bump this when a field is ADDED — clients gate optional fields on it");
    }

    @Test
    void deleteWindowRoundTripsUnderItsWireName() throws Exception {
        ServerInfoResponseBean bean = new ServerInfoResponseBean();
        bean.setEditDeleteWindowMs(172_800_000L);

        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("editDeleteWindowMs"),
                "the field must appear on the wire — a client cannot gate on what is not sent: " + json);

        ServerInfoResponseBean back = mapper.readValue(json, ServerInfoResponseBean.class);
        assertEquals(172_800_000L, back.getEditDeleteWindowMs());
    }

    /**
     * An OLDER server sends no such field. Parsing must yield {@code 0} — the documented "not advertised"
     * signal — rather than failing, because the manifest's whole purpose is that clients keep working across
     * versions. A client seeing 0 simply does not gate and lets the server reject.
     */
    @Test
    void olderServerWithoutTheField_parsesAsNotAdvertised() throws Exception {
        String legacy = "{\"serverVersion\":\"0.8.2\",\"manifestVersion\":\"1.2\","
                + "\"maxAttachmentSizeBytes\":1000000000}";

        ServerInfoResponseBean bean = mapper.readValue(legacy, ServerInfoResponseBean.class);

        assertEquals(0L, bean.getEditDeleteWindowMs(),
                "absent must read as 0 = not advertised, never as an error");
        assertEquals("1.2", bean.getManifestVersion());
    }

    @Test
    void supportedRoutesRoundTripsUnderItsWireName() throws Exception {
        ServerInfoResponseBean bean = new ServerInfoResponseBean();
        bean.setSupportedRoutes(new java.util.TreeSet<>(java.util.List.of("messages", "retrievedeletions")));

        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("supportedRoutes"),
                "a client cannot reason about a route set that is not on the wire: " + json);

        ServerInfoResponseBean back = mapper.readValue(json, ServerInfoResponseBean.class);
        assertTrue(back.getSupportedRoutes().contains("retrievedeletions"));
        assertEquals(2, back.getSupportedRoutes().size());
    }

    /**
     * The additivity guarantee that makes the whole manifest safe: an OLD client parsing a NEW manifest
     * must not break, and a NEW client parsing an OLD manifest must see absence rather than a wrong value.
     * Absence of {@code supportedRoutes} means UNKNOWN — never "supports nothing", which would disable
     * working features against every server that predates 1.4.
     */
    @Test
    void anOlderManifestOmittingTheRouteSetParsesAsAbsentNotEmpty() throws Exception {
        String pre14 = "{\"serverVersion\":\"0.8.2\",\"protocolCurrent\":\"1.2\","
                + "\"manifestVersion\":\"1.3\",\"editDeleteWindowMs\":172800000}";

        ServerInfoResponseBean back = mapper.readValue(pre14, ServerInfoResponseBean.class);

        assertEquals(null, back.getSupportedRoutes(),
                "a pre-1.4 server says NOTHING about routes; the client must read that as unknown and try "
                        + "the call, not conclude the route is missing");
        assertEquals(172_800_000L, back.getEditDeleteWindowMs(),
                "and the fields it DOES send must still arrive — additivity cuts both ways");
    }

    @Test
    void anUnknownFutureFieldDoesNotBreakParsing() throws Exception {
        String future = "{\"serverVersion\":\"9.9\",\"manifestVersion\":\"1.9\","
                + "\"somethingNobodyHasImplementedYet\":42}";

        ServerInfoResponseBean back = mapper.readValue(future, ServerInfoResponseBean.class);

        assertEquals("9.9", back.getServerVersion(),
                "manifest growth must never strand an older client — that is what 'additive' buys");
    }
}
