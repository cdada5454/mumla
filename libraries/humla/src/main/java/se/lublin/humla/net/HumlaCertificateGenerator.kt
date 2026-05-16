package se.lublin.humla.net

import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import org.spongycastle.asn1.x500.X500Name
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter
import org.spongycastle.cert.X509v3CertificateBuilder
import org.spongycastle.jce.provider.BouncyCastleProvider
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder

object HumlaCertificateGenerator {
    private const val ISSUER = "CN=Humla Client"
    private const val YEARS_VALID = 20

    @JvmStatic
    fun generateCertificate(output: OutputStream): X509Certificate {
        val provider = BouncyCastleProvider()
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val keyPair = generator.generateKeyPair()

        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        val signer = JcaContentSignerBuilder("SHA1withRSA").setProvider(provider).build(keyPair.private)
        val startDate = Date()
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        calendar.add(Calendar.YEAR, YEARS_VALID)
        val endDate = calendar.time

        val certBuilder = X509v3CertificateBuilder(
            X500Name(ISSUER),
            BigInteger.ONE,
            startDate,
            endDate,
            X500Name(ISSUER),
            publicKeyInfo
        )
        val certificateHolder = certBuilder.build(signer)
        val certificate = JcaX509CertificateConverter().setProvider(provider).getCertificate(certificateHolder)

        val keyStore = KeyStore.getInstance("PKCS12", provider)
        keyStore.load(null, null)
        keyStore.setKeyEntry("Humla Key", keyPair.private, null, arrayOf<X509Certificate>(certificate))
        keyStore.store(output, CharArray(0))
        return certificate
    }
}
