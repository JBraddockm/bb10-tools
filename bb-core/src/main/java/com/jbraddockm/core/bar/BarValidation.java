package com.jbraddockm.core.bar;

import com.jbraddockm.core.json.Json;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What BlackBerry's rules say about a manifest.
 *
 * <p>The rules are the library's own, for the kind of package and for a signed or an unsigned one.
 * They are not necessarily the rules of the device, which may accept what they refuse or refuse
 * what they accept; the result is information.
 *
 * @param rules which rules were applied, {@code signed} or {@code unsigned}
 * @param messages what the rules found, errors and warnings
 */
public record BarValidation(String rules, List<Message> messages) {

  /** How serious a finding is. */
  public enum Severity {

    /** The manifest breaks a rule. */
    ERROR("error"),

    /** The manifest is questionable. */
    WARNING("warning");

    private final String wireName;

    Severity(String wireName) {
      this.wireName = wireName;
    }

    /**
     * Returns the name used in the wire protocol.
     *
     * @return {@code error} or {@code warning}
     */
    public String wireName() {
      return wireName;
    }
  }

  /**
   * One finding.
   *
   * @param severity how serious it is
   * @param text what the rules say
   */
  public record Message(Severity severity, String text) {

    /**
     * Validates the finding.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public Message {
      Objects.requireNonNull(severity, "severity");
      Objects.requireNonNull(text, "text");
    }
  }

  /**
   * Takes an immutable copy of the messages.
   *
   * @throws NullPointerException if a component is {@code null}
   */
  public BarValidation {
    Objects.requireNonNull(rules, "rules");
    messages = List.copyOf(messages);
  }

  /**
   * Tells whether the rules found no error. Warnings do not count.
   *
   * @return {@code true} if there is no message of severity {@link Severity#ERROR}
   */
  public boolean valid() {
    return messages.stream().noneMatch(message -> message.severity() == Severity.ERROR);
  }

  /**
   * Returns the wire representation.
   *
   * @return an insertion-ordered map
   */
  public Map<String, Object> toJson() {
    return Json.object(
        "rules",
        rules,
        "valid",
        valid(),
        "messages",
        messages.stream()
            .map(
                message ->
                    (Object)
                        Json.object(
                            "severity", message.severity().wireName(), "text", message.text()))
            .toList());
  }
}
