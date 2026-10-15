package com.jbraddockm.link.pairing;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.FormField;
import com.jbraddockm.link.cgi.ResponseBody;
import com.jbraddockm.link.cgi.ResponseElement;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.internal.Scrub;
import com.jbraddockm.link.session.DeviceCommand;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Enrols a client certificate on the device.
 *
 * <p>The certificate is sent exactly as given. bb-link does not check it: the device parses it
 * itself and is described as rejecting only what is not X.509 **[RESEARCH]**, and a stricter check
 * here could refuse a certificate that the device accepts, or keep a deliberately invalid one from
 * reaching the device so that its answer can be captured.
 *
 * <p>The command changes the device and is never repeated after a dropped connection. It is meant
 * to run behind {@link com.jbraddockm.link.actions.ConfirmedByPin}.
 *
 * @param deviceId the identifier of the peer that is paired; the device uses it as the name of an
 *     object, so only letters, digits, dashes and underscores are accepted
 * @param certificate the certificate as PEM text; sent as the form field {@code certdata}
 * @param ecoid the BlackBerry account identifier, which the device records and does not use
 */
public record PairDevice(String deviceId, String certificate, Optional<String> ecoid)
    implements DeviceCommand<PairingAnswer> {

  /** The length that the device is said to accept for {@code certdata} **[RESEARCH]**. */
  public static final int DEVICE_LIMIT_BYTES = 1024;

  private static final int MAXIMUM_CERTIFICATE_BYTES = 16 * 1024;
  private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
  private static final String ERROR_STATUS = "Error";
  private static final String SUCCESS_STATUS = "Success";

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if the identifier has characters other than letters, digits,
   *     dashes and underscores or is longer than 64, if the certificate is blank or longer than 16
   *     KiB, or if the account identifier is blank
   */
  public PairDevice {
    if (deviceId == null || !DEVICE_ID.matcher(deviceId).matches()) {
      throw new InvalidArgumentException(
          "deviceId", "is required: 1 to 64 letters, digits, dashes or underscores");
    }
    if (certificate == null || certificate.isBlank()) {
      throw new InvalidArgumentException("certificate", "is required");
    }
    if (certificate.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_CERTIFICATE_BYTES) {
      throw new InvalidArgumentException("certificate", "is longer than 16 KiB");
    }
    if (ecoid.isPresent() && ecoid.get().isBlank()) {
      throw new InvalidArgumentException("ecoid", "must not be blank");
    }
  }

  /**
   * Tells whether the certificate is longer than the device is said to accept.
   *
   * @return {@code true} if it exceeds {@link #DEVICE_LIMIT_BYTES}
   */
  public boolean exceedsDeviceLimit() {
    return certificate.getBytes(StandardCharsets.UTF_8).length > DEVICE_LIMIT_BYTES;
  }

  @Override
  public Conversation<PairingAnswer> conversation() {
    return new Conversation<>() {
      @Override
      public Step<PairingAnswer> start() {
        var form =
            CgiRequest.Form.to(Endpoint.PAIRING)
                .withText(FormField.CERT_DATA, certificate)
                .withText(FormField.DEVICE_ID, deviceId);
        if (ecoid.isPresent()) {
          form = form.withText(FormField.ECOID, ecoid.get());
        }
        return new Step.Send<>(form.describedAs("Enrolling a client certificate"));
      }

      @Override
      public Step<PairingAnswer> onAnswer(
          CgiResponse response, Optional<ResponseEnvelope> envelope) {
        if (envelope.isEmpty()) {
          return new Step.Fail<>(
              ResponseFailures.notXml(response),
              Optional.of(
                  new PairingAnswer(
                      response.contentType(), null, null, null, ResponseBody.of(response.body()))));
        }
        var payload = envelope.get().payload();
        var status = payload.childText("Status").orElse(null);
        var identifier =
            payload.textAt("Error", "ErrorIdentifier").flatMap(PairDevice::integer).orElse(null);
        var description =
            payload.textAt("Error", "ErrorDescription").map(Scrub::forDisplay).orElse(null);
        var answer =
            new PairingAnswer(
                response.contentType(),
                status,
                identifier,
                description,
                ResponseBody.of(response.body()));

        if (envelope.get().element().orElse(null) != ResponseElement.PAIRING) {
          return new Step.Fail<>(
              ResponseFailures.notExpected(envelope.get(), ResponseElement.PAIRING),
              Optional.of(answer));
        }
        if (ERROR_STATUS.equalsIgnoreCase(status)) {
          return new Step.Fail<>(
              new ServiceError(
                  CoreErrorCode.COMMAND_FAILED,
                  description == null || description.isBlank()
                      ? "The device rejected the pairing request"
                      : description,
                  Json.object("errorIdentifier", identifier)),
              Optional.of(answer));
        }
        if (SUCCESS_STATUS.equalsIgnoreCase(status)) {
          return new Step.Finish<>(answer);
        }
        // What a successful pairing looks like has not been seen. Anything unrecognised is
        // reported as a failure that still carries the body, never as a success.
        return new Step.Fail<>(
            new ServiceError(
                LinkErrorCode.UNEXPECTED_RESPONSE,
                "The pairing answer has no status this version recognises",
                Json.object("status", status)),
            Optional.of(answer));
      }
    };
  }

  private static Optional<Integer> integer(String text) {
    try {
      return Optional.of(Integer.valueOf(text));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
