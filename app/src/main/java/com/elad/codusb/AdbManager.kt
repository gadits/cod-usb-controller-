package com.elad.codusb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/** ADB client with a persistent key, so pairing is needed only once. */
class AdbManager private constructor(context: Context) : AbsAdbConnectionManager() {

    private val mKey: PrivateKey
    private val mCert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val keyFile = File(context.filesDir, "adb_key.pk8")
        val certFile = File(context.filesDir, "adb_cert.der")
        if (keyFile.exists() && certFile.exists()) {
            mKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            mCert = CertificateFactory.getInstance("X.509")
                .generateCertificate(certFile.inputStream())
        } else {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()
            val name = X500Name("CN=codusb")
            val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(
                name,
                BigInteger.valueOf(now),
                Date(now - 86_400_000L),
                Date(now + 3650L * 86_400_000L),
                name,
                kp.public
            )
            val der = builder.build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private)).encoded
            mKey = kp.private
            mCert = CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream())
            keyFile.writeBytes(kp.private.encoded)
            certFile.writeBytes(der)
        }
    }

    override fun getPrivateKey(): PrivateKey = mKey

    override fun getCertificate(): Certificate = mCert

    override fun getDeviceName(): String = "CodUsb"

    companion object {
        @Volatile
        private var instance: AdbManager? = null

        fun get(context: Context): AdbManager =
            instance ?: synchronized(this) {
                instance ?: AdbManager(context.applicationContext).also { instance = it }
            }
    }
}
