# Add project specific ProGuard rules here.

# Conscrypt (used by cbssh for SSH crypto)
-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl

# Apache SSHD (used by sshj)
-dontwarn javax.management.MBeanException
-dontwarn javax.management.ReflectionException

# EdDSA (net.i2p.crypto.eddsa - used for ED25519 keys)
-dontwarn sun.security.x509.X509Key