package io.takamaka.messages.call.service;

import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallSignatures;
import io.takamaka.messages.call.beans.CallCapsBean;
import io.takamaka.messages.call.beans.CallGrantBean;
import io.takamaka.messages.call.beans.CallParamsBean;
import io.takamaka.messages.call.beans.CallRelayBean;
import java.util.List;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;

/**
 * Builds and signs a grant, service &rarr; leg (spec §8.4): audience {@code leg:<leg_id>}, {@code exp = ts + 60000},
 * signed by the service key.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class GrantBuilder {

    public static final String TIER_FREE = "free";
    public static final String TIER_PREMIUM = "premium";
    public static final int HQ_KBPS_FREE = 1500;
    public static final int HQ_KBPS_PREMIUM = 4000;

    private GrantBuilder() {
    }

    public static CallGrantBean build(AsymmetricCipherKeyPair serviceKey, long ts, String callIdHex, String legIdHex,
            int legIndex, String relayUrl, String relayToken, List<String> layers, boolean premium,
            CallParamsBean params) {
        CallGrantBean g = new CallGrantBean();
        g.setAud(CallConstants.AUD_LEG + legIdHex);
        g.setTs(ts);
        g.setExp(ts + CallConstants.GRANT_LIFETIME_MS);
        g.setCall(callIdHex);
        g.setLegIndex(legIndex);
        g.setRelay(new CallRelayBean(relayUrl, relayToken));
        g.setLayers(layers);
        g.setCaps(new CallCapsBean(premium ? HQ_KBPS_PREMIUM : HQ_KBPS_FREE));
        g.setTier(premium ? TIER_PREMIUM : TIER_FREE);
        g.setParams(params);
        CallSignatures.sign(g, serviceKey);
        return g;
    }
}
