# Add project specific ProGuard rules here.

# Conscrypt (used by cbssh for SSH crypto)
-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl

# Apache SSHD (used by sshj / Mina SSHD optional providers)
-dontwarn javax.management.MBeanException
-dontwarn javax.management.ReflectionException
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn org.bouncycastle.crypto.prng.**
-dontwarn org.bouncycastle.pqc.crypto.ntruprime.**

# EdDSA (net.i2p.crypto.eddsa - used for ED25519 keys)
-dontwarn sun.security.x509.X509Key
# Logging policy: strip direct verbose/debug/info logcat calls from release builds.
# Warnings and errors are kept for crash diagnostics. Timber file logging is gated
# separately by FileLoggingTree.minFilePriority().
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
