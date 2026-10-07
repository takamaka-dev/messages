package io.takamaka.messages.call.channel;

import io.takamaka.messages.call.CallCrypto;
import java.util.Arrays;

/**
 * One end of a pairwise hybrid channel (spec §5.3): the two directional chains at index {@code k}, the send
 * counter, and the receive high-water mark.
 *
 * <p>Resolutions recorded in the C182 status report: {@code k} is per channel and counts the fresh commits applied
 * since the channel opened (spec §5.3, A-8); {@code seq} is per direction and never reset when {@code k} advances
 * (A-7); a received {@code seq} must be strictly greater than the last accepted one (replay).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class ChannelState {

    private final boolean opener;
    private byte[] sendChain;
    private byte[] recvChain;
    private long k;
    private long nextSendSeq;
    private long lastRecvSeq = -1;

    ChannelState(boolean opener, byte[] sendChain, byte[] recvChain, long nextSendSeq, long lastRecvSeq) {
        this.opener = opener;
        this.sendChain = sendChain.clone();
        this.recvChain = recvChain.clone();
        this.k = 0;
        this.nextSendSeq = nextSendSeq;
        this.lastRecvSeq = lastRecvSeq;
    }

    public boolean isOpener() {
        return opener;
    }

    public long chainIndex() {
        return k;
    }

    public byte[] sendChain() {
        return sendChain.clone();
    }

    public byte[] recvChain() {
        return recvChain.clone();
    }

    public long nextSendSeq() {
        return nextSendSeq;
    }

    /** Seals {@code plaintext} on the send chain at the current k: returns nonce ‖ ciphertext ‖ tag. */
    public byte[] seal(byte[] aad, byte[] plaintext) {
        byte[] box = HybridChannel.seal(sendChain, k, nextSendSeq, aad, plaintext);
        nextSendSeq++;
        return box;
    }

    /** Opens a box on the receive chain at the current k; refuses another k and a non-increasing seq. */
    public byte[] open(byte[] aad, byte[] box) throws CallCrypto.AeadException {
        HybridChannel.Opened o = HybridChannel.open(recvChain, k, aad, box);
        if (o.seq() <= lastRecvSeq) {
            throw new CallCrypto.AeadException("replayed or reordered seq");
        }
        lastRecvSeq = o.seq();
        return o.plaintext();
    }

    /**
     * Advances both chains to k+1 (spec §5.3: at every fresh commit sent or applied) and forgets the old chain
     * keys.
     */
    public void advance() {
        byte[] s = HybridChannel.nextChain(sendChain);
        byte[] r = HybridChannel.nextChain(recvChain);
        Arrays.fill(sendChain, (byte) 0);
        Arrays.fill(recvChain, (byte) 0);
        sendChain = s;
        recvChain = r;
        k++;
    }

    /** Best-effort zeroisation (spec §7.4). */
    public void destroy() {
        Arrays.fill(sendChain, (byte) 0);
        Arrays.fill(recvChain, (byte) 0);
    }
}
