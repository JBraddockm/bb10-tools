package com.jbraddockm.link.endpoint.pimsync;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * How the items of a {@code get} are narrowed down and paged.
 *
 * @param updatedTime only items changed since this time, in the format the device expects (which is
 *     not known yet, so the text is passed on as it is)
 * @param limit the most items to return
 * @param dirtyOnly only items that were changed and not yet synchronised
 * @param localOnly only what the device has stored itself; the only mode in which paging applies
 * @param offset where to start when paging
 */
public record PimFilters(
    String updatedTime,
    OptionalInt limit,
    boolean dirtyOnly,
    boolean localOnly,
    OptionalInt offset) {

  /**
   * Validates the filters.
   *
   * @throws InvalidArgumentException if the time is blank, the limit is not positive, the offset is
   *     negative, or an offset is given without {@code localOnly}
   */
  public PimFilters {
    Objects.requireNonNull(limit, "limit");
    Objects.requireNonNull(offset, "offset");
    if (updatedTime != null && updatedTime.isBlank()) {
      throw new InvalidArgumentException("updatedTime", "must not be blank");
    }
    if (limit.isPresent() && limit.getAsInt() < 1) {
      throw new InvalidArgumentException("limit", "must be at least 1");
    }
    if (offset.isPresent() && offset.getAsInt() < 0) {
      throw new InvalidArgumentException("offset", "must not be negative");
    }
    if (offset.isPresent() && !localOnly) {
      throw new InvalidArgumentException("offset", "only applies together with localOnly");
    }
  }

  /**
   * Returns filters that narrow nothing down.
   *
   * @return no time, no limit, no flags, no offset
   */
  public static PimFilters none() {
    return new PimFilters(null, OptionalInt.empty(), false, false, OptionalInt.empty());
  }
}
