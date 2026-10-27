package com.jbraddockm.link.endpoint.pimsync;

import com.jbraddockm.link.transport.cgi.WireValue;

/** The reading commands of {@code pimsync.cgi} that bb-link sends. */
enum PimCommand implements WireValue {

  /** Lists the accounts known to the device. */
  LIST_ACCOUNTS("list_accounts", "listAccounts"),

  /** Looks up the account that belongs to a workstation. */
  FIND_ACCOUNT_ID("find_accountId", "findAccount"),

  /** Returns the items of one kind. */
  GET("get", "get");

  private final String wireName;
  private final String jsonName;

  PimCommand(String wireName, String jsonName) {
    this.wireName = wireName;
    this.jsonName = jsonName;
  }

  @Override
  public String wireName() {
    return wireName;
  }

  /**
   * Returns the name used in bb-link's results.
   *
   * @return for example {@code listAccounts}
   */
  public String jsonName() {
    return jsonName;
  }
}
