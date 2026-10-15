package com.jbraddockm.link.session;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads a client identity from PEM files, with the standard library only.
 *
 * <p>A private key is accepted as {@code PRIVATE KEY} (PKCS #8, RSA or EC) or as {@code RSA PRIVATE
 * KEY} (PKCS #1), the two forms an export from Windows tools normally has. An encrypted key, or an
 * EC key in the older SEC1 form, is refused with the command that converts it.
 */
final class PemIdentity {

  /** The certificate chain, leaf first, and the private key that belongs to the leaf. */
  record Loaded(PrivateKey key, X509Certificate[] chain) {}

  private static final Pattern KEY_BLOCK =
      Pattern.compile(
          "-----BEGIN ([A-Z0-9 ]*PRIVATE KEY)-----(.*?)-----END \\1-----", Pattern.DOTALL);

  /** DER of {@code AlgorithmIdentifier { rsaEncryption, NULL }}. */
  private static final byte[] RSA_ALGORITHM = {
    0x30,
    0x0D,
    0x06,
    0x09,
    0x2A,
    (byte) 0x86,
    0x48,
    (byte) 0x86,
    (byte) 0xF7,
    0x0D,
    0x01,
    0x01,
    0x01,
    0x05,
    0x00
  };

  private static final byte[] VERSION_ZERO = {0x02, 0x01, 0x00};

  private PemIdentity() {}

  static Loaded load(ClientCertificate source) throws IOException {
    var chain = readChain(source);
    var key = readKey(source);
    checkPair(key, chain[0]);
    return new Loaded(key, chain);
  }

  private static X509Certificate[] readChain(ClientCertificate source) throws IOException {
    var bytes = Files.readAllBytes(source.certificateFile());
    try {
      var certificates =
          CertificateFactory.getInstance("X.509")
              .generateCertificates(new ByteArrayInputStream(bytes));
      if (certificates.isEmpty()) {
        throw new InvalidArgumentException("clientCertificate", "contains no certificate");
      }
      return certificates.stream().map(X509Certificate.class::cast).toArray(X509Certificate[]::new);
    } catch (CertificateException failure) {
      throw new InvalidArgumentException(
          "clientCertificate", "is not a PEM certificate: " + failure.getMessage());
    }
  }

  private static PrivateKey readKey(ClientCertificate source) throws IOException {
    String text;
    try {
      text = Files.readString(source.keyFile(), StandardCharsets.UTF_8);
    } catch (CharacterCodingException _) {
      throw new InvalidArgumentException("clientKey", "is not a PEM text file");
    }
    var block = KEY_BLOCK.matcher(text);
    if (!block.find()) {
      throw new InvalidArgumentException(
          "clientKey", "contains no private key; a PEM block with BEGIN PRIVATE KEY is expected");
    }
    var label = block.group(1);
    var body = block.group(2);
    return switch (label) {
      case "PRIVATE KEY" -> keyFrom(decode(body));
      case "RSA PRIVATE KEY" -> {
        if (body.contains("Proc-Type") || body.contains("DEK-Info")) {
          throw encrypted();
        }
        yield keyFrom(pkcs8FromPkcs1(decode(body)));
      }
      case "ENCRYPTED PRIVATE KEY" -> throw encrypted();
      case "EC PRIVATE KEY" ->
          throw new InvalidArgumentException(
              "clientKey",
              "is an EC key in the older form; convert it with: openssl pkcs8 -topk8 -nocrypt -in"
                  + " KEY -out KEY.pkcs8.pem");
      default ->
          throw new InvalidArgumentException(
              "clientKey", "has an unsupported kind of key: " + label);
    };
  }

  private static InvalidArgumentException encrypted() {
    return new InvalidArgumentException(
        "clientKey",
        "is encrypted; decrypt a copy of it with: openssl pkcs8 -topk8 -nocrypt -in KEY -out"
            + " KEY.pkcs8.pem");
  }

  private static byte[] decode(String body) {
    try {
      return Base64.getDecoder().decode(body.replaceAll("\\s", ""));
    } catch (IllegalArgumentException _) {
      throw new InvalidArgumentException("clientKey", "is not valid PEM");
    }
  }

  private static PrivateKey keyFrom(byte[] pkcs8) {
    for (var algorithm : List.of("RSA", "EC")) {
      try {
        return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
      } catch (GeneralSecurityException _) {
        // not this kind of key; try the next
      }
    }
    throw new InvalidArgumentException("clientKey", "is not an RSA or EC private key");
  }

  /**
   * Refuses a key that cannot belong to the certificate, which is a mistake worth catching early. A
   * key of another kind is always a mismatch; two RSA keys are compared by their modulus. Other
   * kinds are not compared further.
   */
  private static void checkPair(PrivateKey key, X509Certificate leaf) {
    var mismatch = !key.getAlgorithm().equals(leaf.getPublicKey().getAlgorithm());
    if (!mismatch
        && key instanceof RSAPrivateKey rsaKey
        && leaf.getPublicKey() instanceof RSAPublicKey rsaPublic) {
      mismatch = !rsaKey.getModulus().equals(rsaPublic.getModulus());
    }
    if (mismatch) {
      throw new InvalidArgumentException(
          "clientKey", "does not belong to the certificate in clientCertificate");
    }
  }

  /** Wraps a PKCS #1 RSA key into the PKCS #8 structure that the key factory reads. */
  private static byte[] pkcs8FromPkcs1(byte[] pkcs1) {
    var content = new ByteArrayOutputStream();
    content.writeBytes(VERSION_ZERO);
    content.writeBytes(RSA_ALGORITHM);
    content.writeBytes(der(0x04, pkcs1));
    return der(0x30, content.toByteArray());
  }

  /** Encodes one DER element: the tag, the length, then the content. */
  private static byte[] der(int tag, byte[] content) {
    var out = new ByteArrayOutputStream();
    out.write(tag);
    if (content.length < 0x80) {
      out.write(content.length);
    } else {
      var length = BigInteger.valueOf(content.length).toByteArray();
      var start = length[0] == 0 ? 1 : 0;
      out.write(0x80 | (length.length - start));
      out.write(length, start, length.length - start);
    }
    out.writeBytes(content);
    return out.toByteArray();
  }
}
