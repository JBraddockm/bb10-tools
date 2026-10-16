package com.jbraddockm.link.cgi;

/** The names of the query parameters that GET requests carry. */
public enum QueryParameter implements WireValue {

  /** The operation to perform. */
  COMMAND("command"),

  /** The kind of items an operation works on. */
  SERVICE("service"),

  /** The identifier of the client the operation is for. */
  WORKSTATION_ID("workstation_id"),

  /** Only items changed since this time. */
  UPDATED_TIME("updated_time"),

  /** The most items to return. */
  LIMIT("limit"),

  /** Only items that were changed and not yet synchronised. */
  DIRTY_ONLY("dirty_only"),

  /** Only what the device has stored itself, without asking anything else. */
  LOCAL_ONLY("local_only"),

  /** Where to start when the items are returned in pages. */
  OFFSET("offset"),

  /** The kind of package that is uploaded. */
  TYPE("type");

  private final String wireName;

  QueryParameter(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
