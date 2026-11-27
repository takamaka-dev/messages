package io.takamaka.messages.call;

import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.InvalidWalletIndexException;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.KeyContexts;
import java.util.Arrays;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

/**
 * FIXED keys for the call vectors. Every seed is {@code SHA3-256(ASCII("tkm-call/v1/vectors/" + name))}, so the
 * Dart port derives the same keys from the same names (the seeds are also written into the vector file).
 */
public final class CallTestKeys {

    private CallTestKeys() {
    }

    /** 32-byte seed of a named fixture value. */
    public static byte[] seed(String name) {
        return CallCrypto.h(CallBytes.ascii("tkm-call/v1/vectors/" + name));
    }

    /** RFC 8032 Ed25519 key pair from a 32-byte private seed. */
    public static AsymmetricCipherKeyPair ed25519(byte[] seed) {
        Ed25519PrivateKeyParameters priv = new Ed25519PrivateKeyParameters(seed, 0);
        Ed25519PublicKeyParameters pub = priv.generatePublicKey();
        return new AsymmetricCipherKeyPair(pub, priv);
    }

    public static AsymmetricCipherKeyPair identity(String name) {
        return ed25519(seed("identity/" + name));
    }

    /** A wallet keystore holding one fixed Ed25519 key at index 0 (exercises the iwk signing path). */
    public static final class FixedKeystore implements InstanceWalletKeystoreInterface {

        private final String id;
        private final AsymmetricCipherKeyPair kp;

        public FixedKeystore(String id, AsymmetricCipherKeyPair kp) {
            this.id = id;
            this.kp = kp;
        }

        @Override
        public KeyContexts.WalletCypher getWalletCypher() {
            return KeyContexts.WalletCypher.Ed25519BC;
        }

        @Override
        public AsymmetricCipherKeyPair getKeyPairAtIndex(int i) throws WalletException {
            if (i != 0) {
                throw new InvalidWalletIndexException("fixed keystore has index 0 only");
            }
            return kp;
        }

        @Override
        public byte[] getPublicKeyAtIndexByte(int i) throws WalletException {
            return ((Ed25519PublicKeyParameters) getKeyPairAtIndex(i).getPublic()).getEncoded();
        }

        @Override
        public String getPublicKeyAtIndexURL64(int i) throws WalletException {
            return CallSignatures.identityOf(getKeyPairAtIndex(i));
        }

        @Override
        public String getCurrentWalletID() {
            return id;
        }

        @Override
        public int compareTo(InstanceWalletKeystoreInterface o) {
            return id.compareTo(o.getCurrentWalletID());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof FixedKeystore f && f.id.equals(id) && Arrays.equals(
                    ((Ed25519PublicKeyParameters) f.kp.getPublic()).getEncoded(),
                    ((Ed25519PublicKeyParameters) kp.getPublic()).getEncoded());
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
