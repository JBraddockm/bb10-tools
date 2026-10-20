package com.jbraddockm.link.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.link.pairing.pki.CertificateDetails;
import com.jbraddockm.link.pairing.pki.TestKeys;
import com.jbraddockm.link.testing.Resources;
import java.math.BigInteger;
import java.security.cert.CertificateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link ReturnedCertificate}: reading the certificate returned by the device. */
@DisplayName("ReturnedCertificate: a certificate that a device returned, read and checked")
class ReturnedCertificateTest {

  private static String resource(String name) {
    return Resources.text(TestKeys.class, name);
  }

  @Test
  @DisplayName("parse gives the certificate, what it says, and the text of its block")
  void parseGivesTheThreeThings() throws Exception {
    var leaf = resource("certificate--device-issued.pem");

    var returned = ReturnedCertificate.parse(leaf);

    assertThat(returned.certificate().getSerialNumber()).isEqualTo(BigInteger.valueOf(4097));
    assertThat(returned.details()).isEqualTo(CertificateDetails.of(returned.certificate()));
    assertThat(returned.pem()).isEqualTo(leaf);
  }

  @Test
  @DisplayName("parse keeps only the block, not the text around it")
  void parseKeepsOnlyTheBlock() throws Exception {
    var returned = ReturnedCertificate.parse(resource("certificate--with-noise.txt"));

    assertThat(returned.pem()).isEqualTo(resource("certificate--device-issued.pem"));
  }

  @Test
  @DisplayName("parse reads the first of several certificates, but keeps the text of all of them")
  void parseKeepsTheTextOfEveryBlock() throws Exception {
    var returned = ReturnedCertificate.parse(resource("certificate--two-certificates.pem"));

    assertThat(returned.certificate().getSerialNumber()).isEqualTo(BigInteger.valueOf(4097));
    assertThat(returned.pem()).isEqualTo(resource("certificate--two-certificates.pem"));
  }

  @ParameterizedTest(name = "{0} is not a certificate")
  @ValueSource(
      strings = {
        "pem--no-block.txt",
        "certificate--bare-text.txt",
        "pem--bad-base64.txt",
        "certificate--not-a-certificate.pem"
      })
  @DisplayName(
      "parse fails for a text with no certificate block, a block that is not base64, or one that is"
          + " not X.509")
  void parseFailsForWhatIsNotACertificate(String name) {
    var text = resource(name);

    assertThatThrownBy(() -> ReturnedCertificate.parse(text))
        .isInstanceOf(CertificateException.class);
  }
}
