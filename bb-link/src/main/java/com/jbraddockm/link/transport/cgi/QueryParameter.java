package com.jbraddockm.link.transport.cgi;

/** The names of the query parameters that GET requests carry. */
public enum QueryParameter implements WireValue {

  /** The operation to perform. */
  COMMAND("command"),

  /** The kind of items an operation works on. */
  SERVICE("service"),

  /** The identifier of the client the operation is for. */
  WORKSTATION_ID("workstation_id"),

  /** The human-readable name of the client, sent when an account is created or updated. */
  WORKSTATION_NAME("workstation_name"),

  /** The identifier of a single item within a service, such as the calendar to make the default. */
  OBJECT_ID("object_id"),

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
  TYPE("type"),

  /** The version of the protocol that a request to {@code login.cgi} speaks. */
  REQUEST_VERSION("request_version"),

  /** Asks {@code login.cgi} for the authentication by certificate. */
  CGI_AUTH("cgiauth"),

  /** The action to perform, such as {@code Restore} on a backup upload. */
  ACTION("action"),

  /** The number of bytes of the category uploaded in a restore step. */
  SIZE("size"),

  /**
   * A PIM write's item body as its own JSON, sent in the query when the body carries a multipart
   * upload — a contact photo put. The CGI re-wraps it as the backend's {@code json=} body.
   */
  DATA("data");

  private final String wireName;

  QueryParameter(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
