package com.jbraddockm.link.transport.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link LoginDocument}: reading the status, challenge, denial counts and share
 * credentials of a login answer.
 */
@DisplayName("LoginDocument: what the login of a device answers")
class LoginDocumentTest {

  private static LoginDocument read(String resource) throws UnusableLoginResponseException {
    var text = Resources.text(LoginDocumentTest.class, resource);
    return LoginDocument.of(new CgiResponse(200, "text/xml", text, null));
  }

  private static void assertUnusable(ThrowingCall call, String extra) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            UnusableLoginResponseException.class,
            unusable -> assertThat(unusable.extra()).isEqualTo(extra));
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void run() throws Exception;
  }

  @Nested
  @DisplayName("of")
  class Opening {

    @Test
    @DisplayName("refuses a blank answer, saying that there was none")
    void refusesABlankAnswer() {
      var text = Resources.text(LoginDocumentTest.class, "login-document--blank.txt");

      assertUnusable(
          () -> LoginDocument.of(new CgiResponse(200, "text/html", text, null)),
          "Did not get response");
    }

    @Test
    @DisplayName("refuses an answer that is not well-formed XML, with the reason")
    void refusesMalformedXml() {
      var text = Resources.text(LoginDocumentTest.class, "login-document--malformed.xml");

      assertThatThrownBy(() -> LoginDocument.of(new CgiResponse(200, "text/xml", text, null)))
          .isInstanceOfSatisfying(
              UnusableLoginResponseException.class,
              unusable ->
                  assertThat(unusable.extra())
                      .startsWith("No content or invalid xml: not well-formed XML:"));
    }
  }

  @Nested
  @DisplayName("status")
  class Status {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
      "login-document--challenge.xml, PASSWORD_CHALLENGE",
      "login-document--challenge-legacy.xml, PASSWORD_CHALLENGE",
      "login-document--denied.xml, DENIED",
      "login-document--success.xml, SUCCESS",
      "login-document--success-lowercase-status.xml, SUCCESS"
    })
    @DisplayName("is the status that the device wrote, in any case, from either root")
    void isTheStatusOfTheDevice(String resource, LoginStatus expected) throws Exception {
      assertThat(read(resource).status()).isEqualTo(expected);
    }

    @Test
    @DisplayName("fails for a status that is not known, and says which")
    void failsForAnUnknownStatus() throws Exception {
      var document = read("login-document--status-unknown.xml");

      assertUnusable(document::status, "Unknown status: Strange");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {"login-document--without-status.xml", "login-document--empty-status.xml"})
    @DisplayName("fails when there is no status, or an empty one")
    void failsWithoutAStatus(String resource) throws Exception {
      var document = read(resource);

      assertUnusable(document::status, "Cannot determine status of response");
    }
  }

  @Nested
  @DisplayName("required and integer")
  class Values {

    @Test
    @DisplayName("required gives the text of an element, by the name of the current root")
    void requiredGivesTheText() throws Exception {
      var document = read("login-document--challenge.xml");

      assertThat(document.required(LoginKey.CHALLENGE)).isEqualTo("0123456789");
      assertThat(document.required(LoginKey.SALT)).isEqualTo("abcdef0123456789");
    }

    @Test
    @DisplayName(
        "required keeps a challenge whose text has spaces and an apostrophe, as the real one does,"
            + " and the numbers beside it")
    void aRealChallenge() throws Exception {
      var document = read("login-document--challenge-real.xml");

      assertThat(document.required(LoginKey.CHALLENGE))
          .isEqualTo(
              "What's the airspeed velocity of an unladen swallow?3Pfa94fK6iYorCvomr32L2K5h2al");
      assertThat(document.integer(LoginKey.ALGORITHM)).isEqualTo(2);
      assertThat(document.integer(LoginKey.ITERATION_COUNT)).isEqualTo(6000);
      assertThat(document.required(LoginKey.SALT)).isEqualTo("4EB86E31B720EAFF");
    }

    @Test
    @DisplayName("required reads the small letters of a legacy root")
    void requiredReadsTheLegacyNames() throws Exception {
      var document = read("login-document--challenge-legacy.xml");

      assertThat(document.required(LoginKey.CHALLENGE)).isEqualTo("0123456789");
      assertThat(document.required(LoginKey.ALGORITHM)).isEqualTo("1");
    }

    @Test
    @DisplayName("required fails for an element that is not there, naming it in small letters")
    void requiredFailsForAMissingElement() throws Exception {
      var document = read("login-document--success.xml");

      assertUnusable(() -> document.required(LoginKey.CHALLENGE), "The answer has no challenge");
      assertUnusable(
          () -> document.required(LoginKey.ITERATION_COUNT), "The answer has no iteration_count");
    }

    @Test
    @DisplayName("integer reads a number")
    void integerReadsANumber() throws Exception {
      assertThat(read("login-document--challenge.xml").integer(LoginKey.ITERATION_COUNT))
          .isEqualTo(5000);
    }

    @Test
    @DisplayName("integer fails for text that is not a number, naming the element and the text")
    void integerFailsForText() throws Exception {
      var document = read("login-document--iteration-not-a-number.xml");

      assertUnusable(
          () -> document.integer(LoginKey.ITERATION_COUNT),
          "The iteration_count is not a number: many");
    }
  }

  @Nested
  @DisplayName("denial")
  class Denial {

    @Test
    @DisplayName(
        "is a rejected password with the failed and the remaining attempts that the device gave")
    void isARejectedPassword() throws Exception {
      var rejected = read("login-document--denied.xml").denial();

      assertThat(rejected.failedAttempts()).isEqualTo(2);
      assertThat(rejected.remainingAttempts()).isEqualTo(8);
      assertThat(rejected.isLocked()).isFalse();
    }

    @Test
    @DisplayName("is a locked device when no attempt remains")
    void isLockedWhenNoAttemptRemains() throws Exception {
      assertThat(read("login-document--denied-last-attempt.xml").denial().isLocked()).isTrue();
    }

    @Test
    @DisplayName("reads the counts of a legacy root")
    void readsTheLegacyCounts() throws Exception {
      var rejected = read("login-document--denied-legacy.xml").denial();

      assertThat(rejected.failedAttempts()).isEqualTo(3);
      assertThat(rejected.remainingAttempts()).isEqualTo(7);
    }

    @Test
    @DisplayName("fails when the answer has no counts")
    void failsWithoutCounts() throws Exception {
      var document = read("login-document--success.xml");

      assertUnusable(document::denial, "The answer has no failed_attempts");
    }
  }

  @Nested
  @DisplayName("smb: the credentials of the share")
  class Share {

    @Test
    @DisplayName("are read from the elements, with the workgroup and the NetBIOS name")
    void areReadFromElements() throws Exception {
      assertThat(read("login-document--success-with-share.xml").smb())
          .contains(new SmbCredentials("dtm", "abc123", "WORKGROUP", "BLACKBERRY", ""));
    }

    @Test
    @DisplayName("are read from the attributes of Credentials when there are no elements")
    void areReadFromAttributes() throws Exception {
      assertThat(read("login-document--share-as-attributes.xml").smb())
          .contains(new SmbCredentials("dtm", "abc123", null, null, ""));
    }

    @Test
    @DisplayName("prefer the element to the attribute, for each of the user and the password")
    void preferTheElement() throws Exception {
      var credentials =
          read("login-document--share-element-before-attribute.xml").smb().orElseThrow();

      assertThat(credentials.user()).isEqualTo("element-user");
      assertThat(credentials.password()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("have no workgroup and no NetBIOS name when the device gave none")
    void haveNoWorkgroupWhenNoneWasGiven() throws Exception {
      var credentials =
          read("login-document--share-user-only-workgroup-missing.xml").smb().orElseThrow();

      assertThat(credentials.workgroup()).isNull();
      assertThat(credentials.netBios()).isNull();
    }

    @ParameterizedTest(name = "{0} has none")
    @ValueSource(
        strings = {
          "login-document--share-without-password.xml",
          "login-document--without-share.xml"
        })
    @DisplayName("are nothing when there is no user or no password, or no share at all")
    void areNothingWithoutBoth(String resource) throws Exception {
      assertThat(read(resource).smb()).isEmpty();
    }
  }
}
