package mysh.dev.gemcap.util

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

fun generateKeyPair(): KeyPair {
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048, SecureRandom())
    return generator.generateKeyPair()
}

fun generateCertificate(keyPair: KeyPair): X509Certificate {
    val now = Date()
    val subject = X500Name("CN=Test Identity,O=Gemcap,EMAILADDRESS=test@gemcap.dev")
    val builder = JcaX509v3CertificateBuilder(
        subject,
        BigInteger(128, SecureRandom()),
        Date(now.time - 60_000),
        Date(now.time + 86_400_000),
        subject,
        keyPair.public
    )
    val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
    return JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider())
        .getCertificate(builder.build(signer))
}
