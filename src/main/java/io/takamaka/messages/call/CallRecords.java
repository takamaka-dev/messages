package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallCreateBean;
import io.takamaka.messages.call.beans.CallEraBean;
import io.takamaka.messages.call.beans.CallRecordsBean;
import java.util.List;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;

/**
 * [0.2] The read-only records fetch of spec §7.3 step 0 (shell oracle finding O-1), client side: build the signed
 * {@code records} request, and verify the answer — the creation record and every era record — before anything about
 * the call is shown (N7) or announced.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallRecords {

    private CallRecords() {
    }

    /** The signed {@code records} request: {@code t, f, aud = svc:<id>, n, ts, call}. */
    public static CallRecordsBean request(String audSvc, long ts, String callIdHex, String nonceHex,
            AsymmetricCipherKeyPair identity) {
        CallRecordsBean r = new CallRecordsBean();
        r.setAud(audSvc);
        r.setTs(ts);
        r.setN(nonceHex);
        r.setCall(callIdHex);
        CallSignatures.sign(r, identity);
        return r;
    }

    /**
     * Verifies a records answer for {@code callIdHex} and returns the CURRENT era_hash (the one to announce under):
     * the creation record verifies under its own {@code f} (the owner) with audience {@code audSvc}, names
     * {@code serviceId} in {@code svc}, and hashes to {@code callIdHex}; every era record verifies under the owner,
     * names the call, and chains ({@code era} = 1, 2, … and {@code prev} = the previous era_hash).
     *
     * @throws CallProtocolException {@code bad_signature} on a signature/context failure, {@code era_conflict} on a
     *         broken chain
     */
    public static String verifyChain(CallCreateBean create, List<CallEraBean> eras, String audSvc, String serviceId,
            String callIdHex) throws CallProtocolException {
        if (create == null) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "no creation record");
        }
        CallSignatures.verify(create, CallConstants.T_CREATE, audSvc);
        if (!CallHashes.callIdHex(create).equals(callIdHex)) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "creation record is not this call");
        }
        if (serviceId != null && !serviceId.equals(create.getSvc())) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "record names another service (N14)");
        }
        String owner = create.getF();
        String current = CallHashes.eraHashHex(create);
        int n = 0;
        for (CallEraBean e : eras == null ? List.<CallEraBean>of() : eras) {
            CallSignatures.verify(e, CallConstants.T_ERA, audSvc, owner);
            n++;
            if (!callIdHex.equals(e.getCall()) || e.getEra() == null || e.getEra() != n || !current.equals(e.getPrev())) {
                throw new CallProtocolException(CallError.ERA_CONFLICT, "era does not chain");
            }
            current = CallHashes.eraHashHex(e);
        }
        return current;
    }
}
