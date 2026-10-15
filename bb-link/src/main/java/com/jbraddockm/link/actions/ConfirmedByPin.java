package com.jbraddockm.link.actions;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.session.DeviceCommand;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs a command that changes who the device trusts, or what it holds, only after the caller has
 * shown the PIN of the device.
 *
 * <p>The PIN is read from the device first, every time, and compared with the one the caller
 * supplied; digits may be written in either case and with a leading {@code 0x}. If they differ the
 * command ends with {@code PIN_MISMATCH} before anything is sent that could change the device, and
 * the error does not reveal the real PIN. This is a safeguard against an automatic or mistaken
 * call, not a secret: the PIN is shown on the device and in the result of {@code connect}.
 *
 * @param <R> the result type of the guarded command
 * @param command the command to run once the PIN matches
 * @param confirmPin the PIN as supplied by the caller
 */
public record ConfirmedByPin<R extends CommandResult>(DeviceCommand<R> command, String confirmPin)
    implements DeviceCommand<R> {

  /**
   * Validates the guard.
   *
   * @throws InvalidArgumentException if the PIN is missing or blank
   */
  public ConfirmedByPin {
    Objects.requireNonNull(command, "command");
    if (confirmPin == null || confirmPin.isBlank()) {
      throw new InvalidArgumentException("confirmPin", "is required");
    }
  }

  private static String normalise(String pin) {
    var upper = pin.strip().toUpperCase(Locale.ROOT);
    return upper.startsWith("0X") ? upper.substring(2) : upper;
  }

  @Override
  public Conversation<R> conversation() {
    return new Conversation<>() {
      private Conversation<R> guarded;

      @Override
      public Step<R> start() {
        return new Step.Send<>(GetPin.request());
      }

      @Override
      public Step<R> onAnswer(CgiResponse response, Optional<ResponseEnvelope> envelope) {
        if (guarded != null) {
          return guarded.onAnswer(response, envelope);
        }
        if (envelope.isEmpty()) {
          return new Step.Fail<>(ResponseFailures.notXml(response));
        }
        return switch (PinReader.read(envelope.get())) {
          case Step.Fail<DevicePin>(var error, _) -> new Step.Fail<>(error);
          case Step.Finish<DevicePin>(var devicePin)
              when normalise(devicePin.pin()).equals(normalise(confirmPin)) -> {
            guarded = command.conversation();
            yield guarded.start();
          }
          case Step.Finish<DevicePin> _ ->
              new Step.Fail<>(
                  new ServiceError(
                      LinkErrorCode.PIN_MISMATCH,
                      "The confirmation PIN does not match the PIN of the device"));
          case Step.Send<DevicePin> _ ->
              throw new IllegalStateException("reading the PIN never asks for another request");
        };
      }
    };
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
