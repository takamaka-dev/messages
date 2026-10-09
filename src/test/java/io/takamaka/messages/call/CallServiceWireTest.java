package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.messages.call.beans.CallSignedObject;
import io.takamaka.messages.call.service.CallErrorBean;
import io.takamaka.messages.call.service.CallEventBean;
import io.takamaka.messages.call.service.CallJoinedBean;
import io.takamaka.messages.call.service.CallLegRefBean;
import io.takamaka.messages.call.service.CallPresenceBean;
import io.takamaka.messages.call.service.CallReplyBean;
import io.takamaka.messages.call.service.CallSequencedAnnouncementBean;
import io.takamaka.messages.chat.constant.ChatServerEndpoints;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The call-service wire wrappers (Messages 1.13.0): every signed object carried inside a wrapper survives the strict
 * parser and still verifies; the wrappers are ASCII and strict; the routes never collide with the chat routes.
 */
class CallServiceWireTest {

    static CallVectorScenario s;

    @BeforeAll
    static void build() throws Exception {
        s = new CallVectorScenario();
    }

    @Test
    void everySignedObjectSurvivesAnEventAndStillVerifies() throws Exception {
        for (CallSignedObject o : s.byType.values()) {
            CallEventBean ev = CallEventBean.object(o.getT(), o);
            String wire = CallJson.toWire(ev);
            assertTrue(CallBytes.isAscii(wire));
            CallEventBean back = CallJson.parse(wire, CallEventBean.class);
            assertEquals(o.getClass(), back.getObj().getClass(), o.getT());
            assertEquals(o, back.getObj(), o.getT());
            CallSignatures.verify(back.getObj(), o.getT(), o.getAud(), o.getF());
        }
    }

    @Test
    void joinedCarriesTheChainAnnouncementsAndGrantVerbatim() throws Exception {
        CallJoinedBean j = new CallJoinedBean();
        j.setCreate(s.create);
        j.setEras(List.of(s.era1));
        j.setAnns(List.of(new CallSequencedAnnouncementBean(s.annO, 0, 0L, CallSequencedAnnouncementBean.KIND_JOIN),
                new CallSequencedAnnouncementBean(s.annB, 1, 1L, CallSequencedAnnouncementBean.KIND_JOIN)));
        j.setLegIndex(1);
        j.setJoin(1L);
        j.setEpoch(1L);
        j.setGrant(s.grantB);
        j.setPresence(List.of(new CallPresenceBean(s.annO.getLeg(), 0, CallConstants.ROLE_SPEAKER, CallPresenceBean.PRESENT)));
        CallEventBean ev = CallEventBean.of(CallEventBean.JOINED);
        ev.setJoined(j);
        CallEventBean back = CallJson.parse(CallJson.toWire(ev), CallEventBean.class);
        CallJoinedBean jb = back.getJoined();
        assertEquals(s.callId, CallHashes.callIdHex(jb.getCreate()));
        CallSignatures.verify(jb.getCreate(), CallConstants.T_CREATE, CallVectorScenario.AUD_SVC, jb.getCreate().getF());
        assertEquals(s.eraHash1, CallHashes.eraHashHex(jb.getEras().get(0)));
        assertEquals(CallHashes.annHashHex(s.annB), CallHashes.annHashHex(jb.getAnns().get(1).getAnn()));
        CallSignatures.verify(jb.getGrant(), CallConstants.T_GRANT, s.grantB.getAud(), s.grantB.getF());
        assertEquals(j, jb);
    }

    @Test
    void errorAndReplyShapes() throws Exception {
        assertEquals("{\"error\":{\"err\":\"bad_nonce\"},\"ok\":false}",
                CallJson.canonical(CallReplyBean.refused(CallError.BAD_NONCE)));
        assertEquals("{\"err\":\"rate_limited\",\"limit\":\"nonce_per_minute\"}",
                CallJson.canonical(CallErrorBean.rateLimited("nonce_per_minute")));
        assertEquals("{\"err\":\"not_registered\",\"keys\":[\"k1\"]}",
                CallJson.canonical(CallErrorBean.notRegistered(List.of("k1"))));
        assertEquals("{\"ok\":true}", CallJson.canonical(CallReplyBean.ok()));
        CallEventBean e = CallJson.parse(CallJson.toWire(CallEventBean.error(CallErrorBean.of(CallError.EPOCH_TAKEN))),
                CallEventBean.class);
        assertEquals("epoch_taken", e.getError().getErr());
        assertEquals(new CallLegRefBean("ab", "cd"), CallJson.parse("{\"call\":\"ab\",\"leg\":\"cd\"}", CallLegRefBean.class));
    }

    @Test
    void wrappersAreStrict() {
        assertThrows(JsonProcessingException.class, () -> CallJson.parse("{\"e\":\"ok\",\"extra\":1}", CallEventBean.class));
        assertThrows(JsonProcessingException.class, () -> CallJson.parse("{\"ok\":true,\"ok\":false}", CallReplyBean.class));
        assertThrows(JsonProcessingException.class, () -> CallJson.parse("{\"e\":\"commit\",\"obj\":{\"t\":\"nope\"}}", CallEventBean.class));
    }

    @Test
    void routesArePrefixedAndNeverCollideWithChatRoutes() throws Exception {
        Set<String> chat = new HashSet<>();
        for (Field f : ChatServerEndpoints.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                f.setAccessible(true);
                chat.add((String) f.get(null));
            }
        }
        int routes = 0;
        for (Field f : CallServiceEndpoints.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class && !f.getName().equals("MAPPING_PATH")) {
                String route = (String) f.get(null);
                assertTrue(route.startsWith("call."), route);
                assertFalse(chat.contains(route), route);
                routes++;
            }
        }
        assertEquals(15, routes, "12 of draft 0.1 + call.records [0.2] + call.text + call.grant [0.2]");
    }
}
