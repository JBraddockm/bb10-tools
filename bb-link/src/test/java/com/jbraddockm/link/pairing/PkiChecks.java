package com.jbraddockm.link.pairing;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;

import com.jbraddockm.link.pairing.pki.AuthorityCertificate;
import com.jbraddockm.link.pairing.pki.CertificateDetails;
import com.jbraddockm.link.pairing.pki.CertificateRequest;
import com.jbraddockm.link.pairing.pki.CertificateRequest.Rdn;
import com.jbraddockm.link.pairing.pki.Der;
import com.jbraddockm.link.pairing.pki.EcKeys;
import com.jbraddockm.link.pairing.pki.Pem;
import com.jbraddockm.link.pairing.pki.RsaKeys;
import com.jbraddockm.link.pairing.pki.SignatureScheme;
import com.jbraddockm.link.testing.TestAuthority;
import com.jbraddockm.link.testing.TestDer;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Checks the key, request and certificate plumbing without a device: the encoder against known byte
 * sequences, a request against the JDK's own verification and against {@code openssl} where there
 * is one, and the authority that the other checks sign with.
 */
public final class PkiChecks {

  private PkiChecks() {}

  private static String hex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }

  private static boolean refuses(Supplier<?> action) {
    try {
      action.get();
      return false;
    } catch (IllegalArgumentException _) {
      return true;
    }
  }

  /**
   * Runs the checks.
   *
   * @throws Exception if the harness itself fails
   */
  public static void run() throws Exception {
    encoding();
    pem();
    requests();
    requestSubject();
    issuedByADevice();
    peerAuthority();
    authority();
  }

  private static void encoding() {
    equal("020100", hex(Der.integer(0)), "der: zero");
    equal("02017f", hex(Der.integer(127)), "der: 127 is one byte");
    equal("02020080", hex(Der.integer(128)), "der: 128 needs a leading zero");
    equal("02020100", hex(Der.integer(256)), "der: 256");
    equal("0201ff", hex(Der.integer(-1)), "der: -1");
    equal("020180", hex(Der.integer(-128)), "der: -128");
    equal("0202ff7f", hex(Der.integer(-129)), "der: -129");
    equal(
        "047f", hex(Der.octetString(new byte[127])).substring(0, 4), "der: length 127 is one byte");
    equal(
        "048180",
        hex(Der.octetString(new byte[128])).substring(0, 6),
        "der: length 128 is long form");
    equal("04820100", hex(Der.octetString(new byte[256])).substring(0, 8), "der: length 256");
    equal(
        "0483010000", hex(Der.octetString(new byte[65_536])).substring(0, 10), "der: length 65536");
    equal("0603550403", hex(Der.objectIdentifier("2.5.4.3")), "der: the common name");
    equal(
        "06092a864886f70d01010b",
        hex(Der.objectIdentifier("1.2.840.113549.1.1.11")),
        "der: sha256WithRSA");
    equal(
        "06096086480186f842010d",
        hex(Der.objectIdentifier("2.16.840.1.113730.1.13")),
        "der: an arc of more than 14 bits");
    equal("0101ff", hex(Der.bool(true)), "der: true");
    equal("010100", hex(Der.bool(false)), "der: false");
    equal("0500", hex(Der.nullValue()), "der: null");
    equal("0c0141", hex(Der.utf8String("A")), "der: a string");
    equal(
        "0303000102",
        hex(Der.bitString(new byte[] {1, 2})),
        "der: a bit string has no unused bits");
    equal("3000", hex(Der.sequence()), "der: an empty sequence");
    equal("3100", hex(Der.set()), "der: an empty set");
    equal("a000", hex(Der.contextual(0)), "der: an empty [0]");
    equal("a303020101", hex(Der.contextual(3, Der.integer(1))), "der: [3] holds an element");
    equal(
        "170d" + hex("261017123045Z".getBytes(StandardCharsets.US_ASCII)),
        hex(Der.time(Instant.parse("2026-10-17T12:30:45Z"))),
        "der: a time before 2050 is a UTCTime");
    equal(
        "180f" + hex("20500101000000Z".getBytes(StandardCharsets.US_ASCII)),
        hex(Der.time(Instant.parse("2050-01-01T00:00:00Z"))),
        "der: a time from 2050 is a GeneralizedTime");
    check(refuses(() -> Der.objectIdentifier("1")), "der: one arc is not an identifier");
    check(refuses(() -> Der.objectIdentifier("3.1")), "der: the first arc is 0 to 2");
    check(
        refuses(() -> Der.objectIdentifier("1.40")),
        "der: the second arc is below 40 under 0 and 1");
    check(refuses(() -> Der.contextual(31)), "der: a tag number above 30 is not supported");
  }

  private static void pem() {
    var content = new byte[100];
    for (var index = 0; index < content.length; index++) {
      content[index] = (byte) index;
    }
    var text = Pem.encode("CERTIFICATE", content);
    check(text.startsWith("-----BEGIN CERTIFICATE-----\n"), "pem: begins as expected");
    check(text.endsWith("-----END CERTIFICATE-----\n"), "pem: ends with a line break");
    check(
        text.lines().skip(1).limit(2).allMatch(line -> line.length() == 64),
        "pem: lines of 64 characters");
    equal(
        hex(content),
        hex(Pem.first(text, "CERTIFICATE").orElseThrow().content()),
        "pem: what is written is read back");
    var both = "noise\n" + text + "more noise\n" + Pem.encode("PRIVATE KEY", new byte[] {1, 2, 3});
    equal(2, Pem.blocks(both).size(), "pem: blocks between other text");
    equal(1, Pem.blocks(both, "PRIVATE KEY").size(), "pem: blocks by label");
    check(
        Pem.first(both, "CERTIFICATE REQUEST").isEmpty(),
        "pem: no block of a label that is not there");
    check(
        refuses(() -> Pem.blocks("-----BEGIN CERTIFICATE-----\n!!!\n-----END CERTIFICATE-----\n")),
        "pem: a block that is not base64");
    equal(text, Pem.blocks(text).getFirst().text(), "pem: a block writes itself again");
  }

  private static void requests() throws Exception {
    var keys = RsaKeys.generate(RsaKeys.DEVICE_KEY_BITS);
    var longest = "BBCGIAUTH [FFFFFFFF] " + "a".repeat(19);
    check(
        longest.length() <= CertificateRequest.MAXIMUM_COMMON_NAME_LENGTH,
        "request: the longest name the format makes is within the 64 that X.509 allows");
    for (var scheme : List.of(SignatureScheme.SHA256_WITH_RSA, SignatureScheme.SHA1_WITH_RSA)) {
      var request = request(keys, "BBCGIAUTH [2BD63247] mac", scheme);
      var parts = TestDer.read(request.der(), 0).children();
      var information = parts.get(0);
      var fields = information.children();
      equal("020100", hex(fields.get(0).encoded()), "request: version 0 (" + scheme + ")");
      equal(
          hex(keys.getPublic().getEncoded()),
          hex(fields.get(2).encoded()),
          "request: the public key is in it (" + scheme + ")");
      equal("a000", hex(fields.get(3).encoded()), "request: no attributes (" + scheme + ")");
      equal(
          hex(scheme.algorithmIdentifier()),
          hex(parts.get(1).encoded()),
          "request: the algorithm is named (" + scheme + ")");
      var verifier = Signature.getInstance(scheme.jcaName());
      verifier.initVerify(
          KeyFactory.getInstance("RSA")
              .generatePublic(new X509EncodedKeySpec(fields.get(2).encoded())));
      verifier.update(information.encoded());
      var signature = parts.get(2).content();
      check(
          verifier.verify(signature, 1, signature.length - 1),
          "request: the signature verifies with the key it holds (" + scheme + ")");
    }
    var pem = request(keys, longest, SignatureScheme.SHA256_WITH_RSA).pem();
    check(pem.startsWith("-----BEGIN CERTIFICATE REQUEST-----\n"), "request: its own label");
    check(
        Pem.size(pem) <= 1024,
        "request: even the longest name fits the 1024 bytes that a device takes, got "
            + Pem.size(pem));
    check(
        refuses(() -> subjectRequest(keys, List.of(Rdn.commonName(" "))))
            && refuses(() -> subjectRequest(keys, List.of(Rdn.commonName("x".repeat(65))))),
        "request: a name that is blank or longer than 64");
    var privatePem = Pem.encode("PRIVATE KEY", keys.getPrivate().getEncoded());
    check(privatePem.startsWith("-----BEGIN PRIVATE KEY-----\n"), "key: written as PKCS #8");
    equal(
        hex(keys.getPrivate().getEncoded()),
        hex(
            KeyFactory.getInstance("RSA")
                .generatePrivate(
                    new PKCS8EncodedKeySpec(
                        Pem.first(privatePem, "PRIVATE KEY").orElseThrow().content()))
                .getEncoded()),
        "key: read back as the same key");
    opensslAgrees(pem);
  }

  /**
   * Asks {@code openssl} to verify the request, where it is installed; there is no check without.
   */
  private static void opensslAgrees(String requestPem) throws IOException, InterruptedException {
    var file = Files.createTempFile("bb-link-request", ".csr");
    try {
      Files.writeString(file, requestPem);
      var process =
          new ProcessBuilder("openssl", "req", "-in", file.toString(), "-verify", "-noout")
              .redirectErrorStream(true)
              .start();
      var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      check(process.waitFor(30, TimeUnit.SECONDS), "openssl finished");
      check(
          process.exitValue() == 0 && output.contains("verify OK"),
          "request: openssl verifies its signature: " + output.strip());
    } catch (IOException _) {
      // openssl is not installed here; the checks above stand on their own
    } finally {
      Files.deleteIfExists(file);
    }
  }

  /** A certificate that a phone issued through {@code login.cgi}: captured, as it was sent. */
  private static final String ISSUED_BY_A_DEVICE =
"""
-----BEGIN CERTIFICATE-----
MIIEkjCCA3qgAwIBAgICcKUwDQYJKoZIhvcNAQEFBQAwajEbMBkGA1UECgwSUmVz
ZWFyY2ggSW4gTW90aW9uMRMwEQYDVQQLDApCbGFja0JlcnJ5MTYwNAYDVQQDDC1C
QjEwLzEwM2JhN2M0ZTMyZWEwZGE1ZDNhNWJkMjIwMTNjOTBmNTc1ZjcwY2IwHhcN
MjYxMDExMDgwNjI1WhcNMjYxMTE3MDgwNjI1WjCBqDEuMCwGA1UEAwwlQkJDR0lB
VVRIIG1zLU1hY0Jvb2stUHJvLW0gWzJDMUU3NTgzXTFmMGQGA1UEDQxdQ2xpZW50
IGF1dGhlbnRpY2F0aW9uIGNlcnRpZmljYXRlIGZvciB3aXJlbGVzcyBhY2Nlc3Mu
IFN1cHBvcnQgZm9yIGRldmljZSB3aXRoIFBJTiBbMkMxRTc1ODNdMQ4wDAYDVQQF
EwUyODgzNzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMZfFY5q37q3
pABwWWfeEeeD3qQlkx1L8Q1arkMHNb0wePBMUL7MySJ1SkZXITBcqrKyuF1NbsIZ
kXVhmpO79jUWSdJ/xT+tC5S6vfmD+Z8nMi5tLKIBLgE5WBUmP+l1VCYvIRYKsbKG
AsgS20g4/IRTTyaoRU6saoYWAt8nFiYKYLeJofTVTlsAR+RvncyhDWOxnVuiYmsi
jzj7B+tDyUQ/3ukQlZPuJAt4EIIf7NN+9ZQneAnDiWxxPtNFykDpBELGQGEiFL9g
iVMIV+fVbwCN0E+x5clE+5s8404piVZC5eiQAXxwZ8i1nKRx9P425bUaFpkrIAGb
myGaZwx5yt0CAwEAAaOCAQEwgf4wCQYDVR0TBAIwADAuBglghkgBhvhCAQ0EIRYf
Ik9wZW5TU0wgR2VuZXJhdGVkIENlcnRpZmljYXRlIjAdBgNVHQ4EFgQUhrHQD1yb
1YanfX0l7ZoHLH4oAAwwgZQGA1UdIwSBjDCBiYAUA7UgBQzg+DZGukqKzvpZlhQp
COOhbqRsMGoxGzAZBgNVBAoMElJlc2VhcmNoIEluIE1vdGlvbjETMBEGA1UECwwK
QmxhY2tCZXJyeTE2MDQGA1UEAwwtQkIxMC8xMDNiYTdjNGUzMmVhMGRhNWQzYTVi
ZDIyMDEzYzkwZjU3NWY3MGNiggEqMAsGA1UdDwQEAwIF4DANBgkqhkiG9w0BAQUF
AAOCAQEAwdG5sXw9i7UNPSrwytG2DOH1+RASab1v0bp/Ym2s4z2Q7Dg93borr+Qb
lm5x+RJ/qdZIIbombg/oolXj5CDYJV9b5i6Tkx+rkjFt9fjsdQanLi4Ub4CbLeAX
03x7rzrLC6kNI2InXa6gCKM0vIKtmbydptnJLUbrY2R8RbyDgp1Dtg1fyyQk8XIk
hwY23XTY0tPbY0CiakdFNGhCVZfavHFpxW3S3o76EVSaT529Rw/uiNv8zQvfewsx
3wVMxYpSjtu/AGxuV5Y5fXv6CMqu8QtdRiz5AORWtXQPzjm4ZA4gRuMZ5Ug2xnod
UZJxfIICFnAYmVRxRe0wlUixCdcvHQ==
-----END CERTIFICATE-----
""";

  private static void issuedByADevice() throws Exception {
    var certificate = CertificateDetails.firstCertificate(ISSUED_BY_A_DEVICE);
    var details = CertificateDetails.of(certificate);
    var label = "issued by a device: ";
    equal(
        "CN=BBCGIAUTH ms-MacBook-Pro-m [2C1E7583], DESCRIPTION=Client authentication certificate"
            + " for wireless access. Support for device with PIN [2C1E7583], SERIALNUMBER=28837",
        details.subject(),
        label + "the subject, with its description in words and not as an array");
    equal(
        "O=Research In Motion, OU=BlackBerry, CN=BB10/103ba7c4e32ea0da5d3a5bd22013c90f575f70cb",
        details.issuer(),
        label + "the issuer is the phone's own authority");
    equal(
        BigInteger.valueOf(28_837),
        details.serialNumber(),
        label + "the serial number, which is also in the subject");
    equal(
        37L,
        Duration.between(details.notBefore(), details.notAfter()).toDays(),
        label + "valid for 37 days");
    check(!details.certificateAuthority(), label + "not an authority");
    equal("SHA1withRSA", details.signatureAlgorithm(), label + "signed with SHA-1 and RSA");
    equal(2048, details.publicKeyBits(), label + "an RSA key of 2048 bits");
    equal(
        "[true, true, true, false, false, false, false, false, false]",
        Arrays.toString(certificate.getKeyUsage()),
        label + "key usage: signature, non-repudiation and key encipherment");
  }

  /** The certificate that BlackBerry Link's pairing left in a phone's trust store. */
  private static final String PEER_AUTHORITY_OF_LINK =
"""
-----BEGIN CERTIFICATE-----
MIICMjCCAZSgAwIBAgIRAPgMrgQSdhMUuAkyT4vq0MswCQYHKoZIzj0EATAlMSMw
IQYDVQQDDBpQQyBpc3N1ZXIgMjdBRkI0QUY3RjJEQjQ2NzAeFw0xMjA2MjYxNDM1
NThaFw00OTA1MDgxNzE4MjdaMCUxIzAhBgNVBAMMGlBDIGlzc3VlciAyN0FGQjRB
RjdGMkRCNDY3MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQBUTS9QuAyUhgVpzja
KXjAxftdSYbZCiAPL2lFHAxfwSKNUQFoGFNanMo5BxfM5ruDJB2jkRJqr9df0YFB
rJTe+GoA+M3Tn5zrxkl5wqQIO+xxBxCo5gRbPEY79NeOGgmAj8X48sKQ2HXFJlGa
qKSZngNFUddCMuCYN27Sv0eNCFBIzHajYzBhMA8GA1UdEwEB/wQFMAMBAf8wHQYD
VR0OBBYEFEA2/dGmyHkYaTmXLgy2aiJvqR/PMB8GA1UdIwQYMBaAFEA2/dGmyHkY
aTmXLgy2aiJvqR/PMA4GA1UdDwEB/wQEAwIBxjAJBgcqhkjOPQQBA4GMADCBiAJC
AcW+MTiEqJvzvJBf1CeIM+O6TolHpHHO20bNOruGw4iVmtmx4mHq+NdGAYct8iXx
CWfhXYHx7hOBN5JZmtOHneY6AkIBhrRd5c7xodQGxBLl/pbOsEIw6SzkG0vsYLff
xQ0+HoPf2IKnP5ktmLBw3jvYnIuz/4IJT64w0gFnGkSSM2H3p5c=
-----END CERTIFICATE-----
""";

  private static void peerAuthority() throws Exception {
    var real = CertificateDetails.firstCertificate(PEER_AUTHORITY_OF_LINK);
    var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    var serial = new BigInteger(127, new SecureRandom()).add(BigInteger.ONE);
    var keys = EcKeys.generateP521();
    var made =
        AuthorityCertificate.create(
            keys,
            SignatureScheme.SHA1_WITH_ECDSA,
            "PC issuer 27AFB4AF7F2DB467",
            serial,
            now.minus(Duration.ofDays(7)),
            now.plus(Duration.ofDays(3650)));
    var ours = CertificateDetails.firstCertificate(made.pem());
    ours.verify(ours.getPublicKey());
    var label = "authority: ";
    equal(real.getVersion(), ours.getVersion(), label + "version 3");
    equal(
        real.getSigAlgOID(),
        ours.getSigAlgOID(),
        label + "signed with ECDSA and SHA-1, as the real one is: " + ours.getSigAlgOID());
    equal(
        real.getCriticalExtensionOIDs(),
        ours.getCriticalExtensionOIDs(),
        label + "the same extensions are critical: " + ours.getCriticalExtensionOIDs());
    equal(
        real.getNonCriticalExtensionOIDs(),
        ours.getNonCriticalExtensionOIDs(),
        label + "and the same are not: " + ours.getNonCriticalExtensionOIDs());
    equal(
        Arrays.toString(real.getKeyUsage()),
        Arrays.toString(ours.getKeyUsage()),
        label + "the same key usage: " + Arrays.toString(ours.getKeyUsage()));
    equal(
        real.getBasicConstraints(),
        ours.getBasicConstraints(),
        label + "an authority with no limit on the path");
    equal(
        real.getSubjectX500Principal(),
        ours.getSubjectX500Principal(),
        label + "the same name, when given the real one");
    equal(
        ours.getSubjectX500Principal(),
        ours.getIssuerX500Principal(),
        label + "it is its own issuer");
    equal(
        ((ECPublicKey) real.getPublicKey()).getParams().getOrder().bitLength(),
        ((ECPublicKey) ours.getPublicKey()).getParams().getOrder().bitLength(),
        label + "on the same curve, P-521");
    var skiOurs = ours.getExtensionValue("2.5.29.14");
    var akiOurs = ours.getExtensionValue("2.5.29.35");
    check(
        Arrays.equals(
            Arrays.copyOfRange(skiOurs, skiOurs.length - 20, skiOurs.length),
            Arrays.copyOfRange(akiOurs, akiOurs.length - 20, akiOurs.length)),
        label + "the authority key identifier is the subject key identifier");
    check(
        serial.bitLength() >= 120 && ours.getSerialNumber().equals(serial),
        label + "a serial number of about 128 bits");
    var difference = Math.abs(Pem.size(made.pem()) - Pem.size(PEER_AUTHORITY_OF_LINK));
    check(
        difference < 40,
        label + "about as large as the real one, 822 bytes: " + Pem.size(made.pem()));
    check(Pem.size(made.pem()) <= 1024, label + "within what the device takes");
    opensslVerifiesItself(made.pem(), label);
    check(
        refuses(() -> authorityOf(keys, " ", BigInteger.ONE))
            && refuses(() -> authorityOf(keys, "x".repeat(65), BigInteger.ONE)),
        label + "a name that is blank or over 64");
    check(
        refuses(() -> authorityOf(keys, "PC issuer X", BigInteger.ZERO)),
        label + "a serial number that is not positive");
  }

  private static Object authorityOf(KeyPair keys, String name, BigInteger serial) {
    try {
      var now = Instant.now();
      return AuthorityCertificate.create(
          keys, SignatureScheme.SHA1_WITH_ECDSA, name, serial, now, now.plusSeconds(60));
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException(failure);
    }
  }

  /** Asks {@code openssl} to verify a certificate as its own authority, where it is installed. */
  private static void opensslVerifiesItself(String certificatePem, String label)
      throws IOException, InterruptedException {
    var file = Files.createTempFile("bb-link-self-signed", ".pem");
    try {
      Files.writeString(file, certificatePem);
      var process =
          new ProcessBuilder("openssl", "verify", "-CAfile", file.toString(), file.toString())
              .redirectErrorStream(true)
              .start();
      var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      check(process.waitFor(30, TimeUnit.SECONDS), "openssl finished");
      check(
          process.exitValue() == 0 && output.contains("OK"),
          label + "openssl verifies it against itself: " + output.strip());
    } catch (IOException _) {
      // openssl is not installed here; the checks above stand on their own
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private static CertificateRequest request(KeyPair keys, String commonName, SignatureScheme scheme)
      throws GeneralSecurityException {
    return CertificateRequest.withSubject(keys, List.of(Rdn.commonName(commonName)), scheme);
  }

  private static void requestSubject() throws Exception {
    var keys = RsaKeys.generate(RsaKeys.DEVICE_KEY_BITS);
    var description =
        "Client authentication certificate for wireless access. Support for device with PIN"
            + " [2A3B4C5D]";
    var request =
        CertificateRequest.withSubject(
            keys,
            List.of(
                Rdn.commonName("BBCGIAUTH host-login [2A3B4C5D]"),
                new Rdn("2.5.4.13", description)),
            SignatureScheme.SHA1_WITH_RSA);
    var information = TestDer.read(request.der(), 0);
    var entries = information.children().get(0).children().get(1).children();
    equal(2, entries.size(), "subject: two entries, each in a set of its own");
    equal(
        "550403",
        hex(entries.get(0).children().getFirst().children().get(0).content()),
        "subject: the first is the common name");
    equal(
        "55040d",
        hex(entries.get(1).children().getFirst().children().get(0).content()),
        "subject: the second is the description");
    equal(
        "2a864886f70d010105",
        hex(information.children().get(1).children().getFirst().content()),
        "subject: signed with SHA-1 and RSA");
    equal(
        "a000",
        hex(information.children().get(0).children().get(3).encoded()),
        "subject: and no attributes");
    check(refuses(() -> subjectRequest(keys, List.of())), "subject: an empty subject");
    check(
        refuses(() -> subjectRequest(keys, List.of(Rdn.commonName("x".repeat(65))))),
        "subject: a common name over 64");
    check(refuses(() -> new Rdn("2.5.4.13", " ")), "subject: an entry is not blank");
    opensslReadsSubject(request.pem(), List.of("BBCGIAUTH host-login [2A3B4C5D]", description));
  }

  private static Object subjectRequest(KeyPair keys, List<Rdn> subject) {
    try {
      return CertificateRequest.withSubject(keys, subject, SignatureScheme.SHA1_WITH_RSA);
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException(failure);
    }
  }

  /** Asks {@code openssl} to verify a request and print its subject, where it is installed. */
  private static void opensslReadsSubject(String requestPem, List<String> parts)
      throws IOException, InterruptedException {
    var file = Files.createTempFile("bb-link-request", ".csr");
    try {
      Files.writeString(file, requestPem);
      var process =
          new ProcessBuilder(
                  "openssl", "req", "-in", file.toString(), "-noout", "-subject", "-verify")
              .redirectErrorStream(true)
              .start();
      var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      check(process.waitFor(30, TimeUnit.SECONDS), "openssl finished");
      check(
          process.exitValue() == 0 && output.contains("verify OK"),
          "subject: openssl verifies the request: " + output.strip());
      for (var part : parts) {
        check(output.contains(part), "subject: openssl shows " + part);
      }
    } catch (IOException _) {
      // openssl is not installed here; the checks above stand on their own
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private static void authority() throws Exception {
    var authority = new TestAuthority();
    var keys = RsaKeys.generate(RsaKeys.DEVICE_KEY_BITS);
    var request = request(keys, "BBCGIAUTH mac-alice [2BD63247]", SignatureScheme.SHA256_WITH_RSA);
    var issued = authority.sign(request.pem()).orElseThrow();
    equal("BBCGIAUTH mac-alice [2BD63247]", issued.commonName(), "authority: it read the name");
    var certificate = CertificateDetails.firstCertificate(issued.pem());
    var details = CertificateDetails.of(certificate);
    equal(BigInteger.valueOf(29_717), details.serialNumber(), "authority: the first serial");
    check(
        details.subject().contains("SERIALNUMBER=29717")
            && details.subject().contains("CN=BBCGIAUTH mac-alice [2BD63247]"),
        "authority: the serial is in the subject too, as on a device: " + details.subject());
    check(
        details.issuer().contains("O=Research In Motion"),
        "authority: its issuer: " + details.issuer());
    equal(
        37L,
        Duration.between(details.notBefore(), details.notAfter()).toDays(),
        "authority: valid for 37 days");
    equal("SHA1withRSA", details.signatureAlgorithm(), "authority: signed with SHA-1");
    equal("RSA", details.publicKeyAlgorithm(), "authority: the key's algorithm");
    equal(2048, details.publicKeyBits(), "authority: the key's size");
    check(!details.certificateAuthority(), "authority: a leaf is not an authority");
    check(
        details.sha256Fingerprint().matches("([0-9A-F]{2}:){31}[0-9A-F]{2}"),
        "details: the fingerprint is pairs of capitals with colons: "
            + details.sha256Fingerprint());
    equal(37L, details.daysLeft(details.notBefore()), "details: 37 days left when it starts");
    check(!details.expiredAt(details.notAfter()), "details: valid up to the last second");
    check(details.expiredAt(details.notAfter().plusSeconds(1)), "details: expired after it");
    check(
        details.daysLeft(details.notAfter().plusSeconds(1)) <= 0, "details: no days left after it");
    certificate.verify(
        KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(rootPublicKey(authority))));
    equal(
        29_718L,
        CertificateDetails.of(
                CertificateDetails.firstCertificate(
                    authority.sign(request.pem()).orElseThrow().pem()))
            .serialNumber()
            .longValue(),
        "authority: serial numbers go up");
    var root = CertificateDetails.of(CertificateDetails.firstCertificate(authority.rootPem()));
    check(root.certificateAuthority(), "authority: its own certificate is an authority");
    check(
        authority.sign(TestAuthority.requestWithWrongSignature("BBCGIAUTH [1] x")).isEmpty(),
        "authority: a request whose signature is wrong is not signed");
    check(authority.sign("not a request").isEmpty(), "authority: text that is not a request");
    check(parseFails("garbage"), "details: text without a certificate");
    check(
        parseFails("-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n"),
        "details: a block that is not a certificate");
    check(
        parseFails("-----BEGIN CERTIFICATE-----\n!!!\n-----END CERTIFICATE-----\n"),
        "details: a block that is not base64");
  }

  private static byte[] rootPublicKey(TestAuthority authority) throws Exception {
    return CertificateDetails.firstCertificate(authority.rootPem()).getPublicKey().getEncoded();
  }

  private static Object parse(String text) {
    try {
      return CertificateDetails.firstCertificate(text);
    } catch (CertificateException failure) {
      throw new IllegalArgumentException(failure);
    }
  }

  private static boolean parseFails(String text) {
    return refuses(() -> parse(text));
  }
}
