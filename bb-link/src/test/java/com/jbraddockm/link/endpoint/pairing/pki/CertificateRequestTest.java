package com.jbraddockm.link.endpoint.pairing.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.link.endpoint.pairing.pki.CertificateRequest.Rdn;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.TestDer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for {@link CertificateRequest}: the PKCS #10 encoding. */
@DisplayName("CertificateRequest: a request for a certificate, signed with the key it is for")
class CertificateRequestTest {

  private static final HexFormat HEX = HexFormat.of().withUpperCase();
  private static final String DESCRIPTION = "2.5.4.13";

  private final KeyPair keys = TestKeys.rsa();

  private static TestDer.Element parse(CertificateRequest request) {
    return TestDer.read(request.der(), 0);
  }

  private static String text(TestDer.Element element) {
    return new String(element.content(), StandardCharsets.UTF_8);
  }

  private static String name(String resource) {
    return Resources.text(CertificateRequestTest.class, resource).strip();
  }

  private CertificateRequest requestFor(String commonName) throws GeneralSecurityException {
    return CertificateRequest.withSubject(
        keys, List.of(Rdn.commonName(commonName)), SignatureScheme.SHA1_WITH_RSA);
  }

  @Nested
  @DisplayName("what it is made of")
  class Structure {

    @Test
    @DisplayName("is a sequence of the information, the signature algorithm and the signature")
    void isASequenceOfThree() throws Exception {
      var top = parse(requestFor("a"));

      assertThat(top.tag()).isEqualTo(0x30);
      assertThat(top.children()).extracting(TestDer.Element::tag).containsExactly(0x30, 0x30, 0x03);
    }

    @Test
    @DisplayName(
        "has the information: version 0, the subject, the public key of the pair, and no"
            + " attributes")
    void hasTheInformation() throws Exception {
      var information = parse(requestFor("a")).children().getFirst().children();

      assertThat(HEX.formatHex(information.get(0).encoded())).isEqualTo("020100");
      assertThat(information.get(1).tag()).isEqualTo(0x30);
      assertThat(information.get(2).encoded()).isEqualTo(keys.getPublic().getEncoded());
      assertThat(HEX.formatHex(information.get(3).encoded())).isEqualTo("A000");
    }

    @Test
    @DisplayName("has one entry in the subject for a common name, as a UTF8String")
    void hasTheCommonName() throws Exception {
      var subject =
          parse(requestFor("BBCGIAUTH [2A3B4C5D] workstation"))
              .children()
              .getFirst()
              .children()
              .get(1);

      var entry = subject.children().getFirst().children().getFirst();
      assertThat(subject.children()).hasSize(1);
      assertThat(entry.children().get(0).encoded()).isEqualTo(Der.objectIdentifier("2.5.4.3"));
      assertThat(entry.children().get(1).tag()).isEqualTo(0x0C);
      assertThat(text(entry.children().get(1))).isEqualTo("BBCGIAUTH [2A3B4C5D] workstation");
    }

    @Test
    @DisplayName("writes a name with accents as UTF-8")
    void writesUtf8() throws Exception {
      var value =
          parse(requestFor("é"))
              .children()
              .getFirst()
              .children()
              .get(1)
              .children()
              .getFirst()
              .children()
              .getFirst()
              .children()
              .get(1);

      assertThat(HEX.formatHex(value.content())).isEqualTo("C3A9");
    }

    @Test
    @DisplayName(
        "keeps the entries of the subject in the order they were given, each in a set of its own")
    void keepsTheOrderOfTheEntries() throws Exception {
      var request =
          CertificateRequest.withSubject(
              keys,
              List.of(Rdn.commonName("first"), new Rdn(DESCRIPTION, "second")),
              SignatureScheme.SHA256_WITH_RSA);

      var subject = parse(request).children().getFirst().children().get(1);

      assertThat(subject.children()).extracting(TestDer.Element::tag).containsExactly(0x31, 0x31);
      var oids =
          subject.children().stream()
              .map(set -> set.children().getFirst().children().getFirst().encoded())
              .toList();
      assertThat(oids)
          .containsExactly(Der.objectIdentifier("2.5.4.3"), Der.objectIdentifier(DESCRIPTION));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(names = {"SHA256_WITH_RSA", "SHA1_WITH_RSA"})
    @DisplayName("names the scheme it was signed with, as the algorithm identifier of that scheme")
    void namesTheScheme(SignatureScheme scheme) throws Exception {
      var request = CertificateRequest.withSubject(keys, List.of(Rdn.commonName("a")), scheme);

      assertThat(parse(request).children().get(1).encoded())
          .isEqualTo(scheme.algorithmIdentifier());
    }
  }

  @Nested
  @DisplayName("its signature")
  class Signing {

    @ParameterizedTest(name = "{0}")
    @EnumSource(names = {"SHA256_WITH_RSA", "SHA1_WITH_RSA"})
    @DisplayName(
        "is the signature of the information, by the private key, which the public key checks")
    void theSignatureIsValid(SignatureScheme scheme) throws Exception {
      var request = CertificateRequest.withSubject(keys, List.of(Rdn.commonName("a")), scheme);
      var parts = parse(request).children();

      assertThat(verifies(parts, scheme, keys)).isTrue();
    }

    @Test
    @DisplayName("is not valid for another key")
    void theSignatureIsNotValidForAnotherKey() throws Exception {
      var parts = parse(requestFor("a")).children();

      assertThat(verifies(parts, SignatureScheme.SHA1_WITH_RSA, TestKeys.otherRsa())).isFalse();
    }

    @Test
    @DisplayName("is not valid for a request that was changed")
    void theSignatureIsNotValidForAChangedRequest() throws Exception {
      var der = requestFor("a").der();
      der[10] ^= 0x01;

      assertThat(verifies(TestDer.read(der, 0).children(), SignatureScheme.SHA1_WITH_RSA, keys))
          .isFalse();
    }

    @Test
    @DisplayName("is made with ECDSA when the key is an elliptic-curve key and the scheme is ECDSA")
    void anEllipticCurveKeyMakesAnEcdsaSignature() throws Exception {
      var ec = TestKeys.ec();
      var request =
          CertificateRequest.withSubject(
              ec, List.of(Rdn.commonName("a")), SignatureScheme.SHA1_WITH_ECDSA);

      assertThat(verifies(parse(request).children(), SignatureScheme.SHA1_WITH_ECDSA, ec)).isTrue();
    }

    @Test
    @DisplayName("cannot be made with a scheme that does not fit the key")
    void aSchemeThatDoesNotFitTheKey() {
      assertThatThrownBy(
              () ->
                  CertificateRequest.withSubject(
                      TestKeys.ec(), List.of(Rdn.commonName("a")), SignatureScheme.SHA1_WITH_RSA))
          .isInstanceOf(GeneralSecurityException.class);
    }

    private boolean verifies(List<TestDer.Element> parts, SignatureScheme scheme, KeyPair signer)
        throws Exception {
      var verifier = Signature.getInstance(scheme.jcaName());
      verifier.initVerify(signer.getPublic());
      verifier.update(parts.get(0).encoded());
      var signature = parts.get(2).content();
      return verifier.verify(Arrays.copyOfRange(signature, 1, signature.length));
    }
  }

  @Nested
  @DisplayName("how it is written")
  class Writing {

    @Test
    @DisplayName(
        "pem is a CERTIFICATE REQUEST block of the bytes, in lines of at most 64 characters")
    void pemIsABlock() throws Exception {
      var request = requestFor("a");
      var pem = request.pem();

      assertThat(pem)
          .startsWith("-----BEGIN CERTIFICATE REQUEST-----\n")
          .endsWith("-----END CERTIFICATE REQUEST-----\n");
      assertThat(pem.lines()).allMatch(line -> line.length() <= 64);
      assertThat(Pem.first(pem, "CERTIFICATE REQUEST"))
          .hasValueSatisfying(block -> assertThat(block.content()).isEqualTo(request.der()));
    }

    @Test
    @DisplayName("der gives a copy: changing it does not change the request")
    void derGivesACopy() throws Exception {
      var request = requestFor("a");
      var first = request.der();
      first[0] = 0;

      assertThat(request.der()[0]).isEqualTo((byte) 0x30);
    }

    @Test
    @DisplayName("pem and der are the same request")
    void pemAndDerAgree() throws Exception {
      var request = requestFor("a");
      var body =
          request
              .pem()
              .lines()
              .filter(line -> !line.startsWith("-----"))
              .reduce("", String::concat);

      assertThat(Base64.getDecoder().decode(body)).isEqualTo(request.der());
    }
  }

  @Nested
  @DisplayName("what it refuses")
  class Refusals {

    @Test
    @DisplayName("a subject with no entry")
    void aSubjectWithNoEntry() {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () -> CertificateRequest.withSubject(keys, List.of(), SignatureScheme.SHA1_WITH_RSA))
          .withMessageContaining("an entry at least");
    }

    @Test
    @DisplayName("a common name of 64 characters is the longest there is")
    void aCommonNameOf64IsTheLongest() throws Exception {
      assertThat(requestFor(name("common-name--64-characters.txt")).der()).isNotEmpty();
      assertThat(CertificateRequest.MAXIMUM_COMMON_NAME_LENGTH).isEqualTo(64);
    }

    @Test
    @DisplayName("a common name of 65 characters")
    void aCommonNameOf65() {
      var tooLong = name("common-name--65-characters.txt");

      assertThatIllegalArgumentException()
          .isThrownBy(() -> requestFor(tooLong))
          .withMessageContaining("1 to 64 characters");
    }

    @Test
    @DisplayName(
        "is not stricter with another entry: a description may be longer than 64 characters")
    void otherEntriesMayBeLonger() throws Exception {
      var description = name("attribute--long-description.txt");

      var request =
          CertificateRequest.withSubject(
              keys,
              List.of(Rdn.commonName("a"), new Rdn(DESCRIPTION, description)),
              SignatureScheme.SHA1_WITH_RSA);

      assertThat(request.der()).isNotEmpty();
    }

    @Test
    @DisplayName(
        "is not made too large for the device: even with the longest name in use it is within 1024"
            + " bytes")
    void theLongestRequestFitsWhatTheDeviceTakes() throws Exception {
      var request =
          CertificateRequest.withSubject(
              keys,
              List.of(Rdn.commonName(name("common-name--longest-in-use.txt"))),
              SignatureScheme.SHA256_WITH_RSA);

      assertThat(Pem.size(request.pem())).isLessThanOrEqualTo(1024);
    }

    @Test
    @DisplayName("an entry whose value is blank")
    void aBlankEntry() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> Rdn.commonName("  "))
          .withMessageContaining("not blank");
      assertThatIllegalArgumentException().isThrownBy(() -> new Rdn(DESCRIPTION, ""));
    }
  }

  @Test
  @DisplayName("Rdn.commonName is the attribute 2.5.4.3")
  void commonNameIsTheAttribute() {
    assertThat(Rdn.commonName("x").objectIdentifier())
        .isEqualTo("2.5.4.3")
        .isEqualTo(Rdn.COMMON_NAME);
  }
}
