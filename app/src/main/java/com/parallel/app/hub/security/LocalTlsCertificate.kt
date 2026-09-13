package com.parallel.app.hub.security

import android.content.Context
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

class LocalTlsCertificate(private val context: Context) {
    companion object {
        const val KEYSTORE_FILE = "parallel-local-hub.p12"
        const val KEYSTORE_PASSWORD = "parallel-local-hub-keystore"
        const val KEY_ALIAS = "parallel-local-hub"
        const val KEY_PASSWORD = "parallel-local-hub-key"
    }

    data class Material(
        val keyStore: KeyStore,
        val keyPassword: String,
        val certificate: X509Certificate,
        val fingerprintSha256: String
    )

    fun loadOrCreate(localAddress: String?): Material {
        val file = File(context.filesDir, KEYSTORE_FILE)
        val keyStore = KeyStore.getInstance("PKCS12")
        if (file.exists()) {
            file.inputStream().use { keyStore.load(it, KEYSTORE_PASSWORD.toCharArray()) }
            val certificate = keyStore.getCertificate(KEY_ALIAS) as X509Certificate
            return Material(keyStore, KEY_PASSWORD, certificate, fingerprint(certificate))
        }

        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val certificate = createCertificate(keyPair, localAddress)
        keyStore.load(null, KEYSTORE_PASSWORD.toCharArray())
        keyStore.setKeyEntry(KEY_ALIAS, keyPair.private, KEY_PASSWORD.toCharArray(), arrayOf(certificate))
        file.outputStream().use { keyStore.store(it, KEYSTORE_PASSWORD.toCharArray()) }
        return Material(keyStore, KEY_PASSWORD, certificate, fingerprint(certificate))
    }

    private fun createCertificate(keyPair: KeyPair, localAddress: String?): X509Certificate {
        val now = java.util.Date()
        val notAfter = java.util.Date(now.time + 365L * 24 * 60 * 60 * 1000)
        val subject = org.bouncycastle.asn1.x500.X500Name("CN=Parallel Local Hub")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, SecureRandom()),
            now,
            notAfter,
            subject,
            keyPair.public
        )
        val names = buildList {
            add(GeneralName(GeneralName.DNS_NAME, "parallel.local"))
            add(GeneralName(GeneralName.DNS_NAME, "localhost"))
            localAddress?.takeIf { it.isNotBlank() }?.let { add(GeneralName(GeneralName.IP_ADDRESS, it)) }
        }
        builder.addExtension(Extension.subjectAlternativeName, false, GeneralNames(names.toTypedArray()))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val holder: X509CertificateHolder = builder.build(signer)
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    private fun fingerprint(certificate: X509Certificate): String = MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString(":") { "%02X".format(it) }
}
