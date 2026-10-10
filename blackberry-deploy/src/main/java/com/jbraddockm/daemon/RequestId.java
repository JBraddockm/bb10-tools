package com.jbraddockm.daemon;

import java.util.Optional;

/**
 * The client's identifier of a request, echoed in every reply. JSON integers and strings are both
 * allowed, and each is echoed as the same JSON type.
 */
sealed interface RequestId {

  /** Recognises an id in a parsed request; empty if it is neither an integer nor a string. */
  static Optional<RequestId> from(Object json) {
    return switch (json) {
      case Long number -> Optional.of(new Numeric(number));
      case String text -> Optional.of(new Text(text));
      case null, default -> Optional.empty();
    };
  }

  /** The value to write into a reply. */
  Object toJson();

  /** An integer id. */
  record Numeric(long value) implements RequestId {
    @Override
    public Object toJson() {
      return value;
    }
  }

  /** A string id. */
  record Text(String value) implements RequestId {
    @Override
    public Object toJson() {
      return value;
    }
  }
}
