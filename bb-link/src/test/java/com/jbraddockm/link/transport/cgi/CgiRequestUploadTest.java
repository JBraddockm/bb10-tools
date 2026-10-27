package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.jbraddockm.link.testing.TestPhase;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link CgiRequest.Upload}. */
@DisplayName("CgiRequest.Upload: a file sent to the device")
class CgiRequestUploadTest {

  private static final Path FILE = Path.of("package.bar");
  private static final CgiRequest.Upload BASE =
      CgiRequest.Upload.of(Endpoint.UPDATE, FILE, 1024L, TestPhase.UPLOADING, "package.bar");

  @Test
  @DisplayName(
      "of makes an upload with the file, its length, its phase and its item, and nothing else")
  void ofMakesTheUpload() {
    assertThat(BASE.endpoint()).isEqualTo(Endpoint.UPDATE);
    assertThat(BASE.file()).isEqualTo(FILE);
    assertThat(BASE.length()).isEqualTo(1024L);
    assertThat(BASE.phase()).isEqualTo(TestPhase.UPLOADING);
    assertThat(BASE.item()).isEqualTo("package.bar");
    assertThat(BASE.query()).isEmpty();
    assertThat(BASE.purpose()).isEmpty();
    assertThat(BASE.readTimeout()).isEqualTo(CgiRequest.DEFAULT_READ_TIMEOUT);
  }

  @Test
  @DisplayName("an empty answer to an upload is never normal")
  void anEmptyAnswerIsNeverNormal() {
    assertThat(BASE.emptyAnswerIsNormal()).isFalse();
  }

  @Test
  @DisplayName("a file of no bytes is allowed, and a file of a negative length is not")
  void theLengthIsNotNegative() {
    assertThat(CgiRequest.Upload.of(Endpoint.UPDATE, FILE, 0L, TestPhase.UPLOADING, "x").length())
        .isZero();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> CgiRequest.Upload.of(Endpoint.UPDATE, FILE, -1L, TestPhase.UPLOADING, "x"))
        .withMessageContaining("negative length");
  }

  @Test
  @DisplayName("with adds a parameter to the query, and pathAndQuery writes it after the path")
  void withAddsAParameter() {
    var upload = BASE.with(QueryParameter.TYPE, FormField.ACTION);

    assertThat(upload.query()).containsExactly(Map.entry(QueryParameter.TYPE, "action"));
    assertThat(upload.pathAndQuery()).isEqualTo("/cgi-bin/update.cgi?type=action");
    assertThat(BASE.pathAndQuery()).isEqualTo("/cgi-bin/update.cgi");
  }

  @Test
  @DisplayName(
      "describe says what is posted where when there is no purpose, and the purpose when there is")
  void describeSaysWhatTheUploadDoes() {
    assertThat(BASE.describe()).isEqualTo("POST a file to update.cgi");
    assertThat(BASE.describedAs("Sending the package").describe()).isEqualTo("Sending the package");
  }

  @Test
  @DisplayName("waitingUpTo sets the time to wait for the answer, and keeps the rest")
  void waitingUpToSetsTheTime() {
    var upload = BASE.waitingUpTo(Duration.ofMinutes(10));

    assertThat(upload.readTimeout()).isEqualTo(Duration.ofMinutes(10));
    assertThat(upload.file()).isEqualTo(FILE);
    assertThat(upload.length()).isEqualTo(1024L);
  }

  @Test
  @DisplayName("each part of an upload is required")
  void eachPartIsRequired() {
    var time = CgiRequest.DEFAULT_READ_TIMEOUT;
    var phase = TestPhase.UPLOADING;

    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new CgiRequest.Upload(null, new LinkedHashMap<>(), FILE, 1L, phase, "x", "", time))
        .withMessage("endpoint");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new CgiRequest.Upload(
                    Endpoint.UPDATE, new LinkedHashMap<>(), null, 1L, phase, "x", "", time))
        .withMessage("file");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new CgiRequest.Upload(
                    Endpoint.UPDATE, new LinkedHashMap<>(), FILE, 1L, null, "x", "", time))
        .withMessage("phase");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new CgiRequest.Upload(
                    Endpoint.UPDATE, new LinkedHashMap<>(), FILE, 1L, phase, null, "", time))
        .withMessage("item");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new CgiRequest.Upload(
                    Endpoint.UPDATE, new LinkedHashMap<>(), FILE, 1L, phase, "x", null, time))
        .withMessage("purpose");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new CgiRequest.Upload(
                    Endpoint.UPDATE, new LinkedHashMap<>(), FILE, 1L, phase, "x", "", null))
        .withMessage("readTimeout");
  }
}
