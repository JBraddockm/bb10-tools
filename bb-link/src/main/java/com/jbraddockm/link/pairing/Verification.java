package com.jbraddockm.link.pairing;

import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * Whether a new identity was tried, and whether the device took it.
 *
 * @param state what happened
 * @param error why it failed, for {@link State#FAILED}
 */
public record Verification(State state, ServiceError error) {

  /** The outcome of the trial. */
  public enum State {
    /** The device accepted the certificate: a login with it, and nothing else, worked. */
    VERIFIED("verified"),
    /** The device did not accept it, or could not be asked. */
    FAILED("failed"),
    /** It was not tried. */
    SKIPPED("skipped");

    private final String wireName;

    State(String wireName) {
      this.wireName = wireName;
    }

    String wireName() {
      return wireName;
    }
  }

  /**
   * A trial that worked.
   *
   * @return the verification
   */
  public static Verification verified() {
    return new Verification(State.VERIFIED, null);
  }

  /**
   * A trial that was not made.
   *
   * @return the verification
   */
  public static Verification skipped() {
    return new Verification(State.SKIPPED, null);
  }

  /**
   * A trial that failed.
   *
   * @param error why
   * @return the verification
   */
  public static Verification failed(ServiceError error) {
    return new Verification(State.FAILED, error);
  }

  /**
   * The verification as JSON.
   *
   * @return the state, and the code and message of the error if there is one
   */
  public Map<String, Object> toJson() {
    return Json.object(
        "state", state.wireName(),
        "errorCode", error != null ? error.code().wireName() : null,
        "errorMessage", error != null ? error.message() : null);
  }
}
