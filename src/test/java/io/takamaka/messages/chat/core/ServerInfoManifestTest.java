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
 */
class ServerInfoManifestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void manifestVersionIsCurrent() {
        assertEquals("1.3", ServerInfoResponseBean.MANIFEST_VERSION_CURRENT,
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
}
