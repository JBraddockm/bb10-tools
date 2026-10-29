package com.jbraddockm.link.endpoint.pimsync;

import com.jbraddockm.link.transport.cgi.WireValue;

/** The commands of {@code pimsync.cgi} that bb-link sends. */
public enum PimCommand implements WireValue {

  /** Lists the accounts known to the device. */
  LIST_ACCOUNTS("list_accounts", "listAccounts"),

  /** Looks up the account that belongs to a workstation. */
  FIND_ACCOUNT_ID("find_accountId", "findAccount"),

  /** Returns the items of one kind. */
  GET("get", "get"),

  /** Registers a workstation as a BlackBerry Link account. Changes the device. */
  CREATE_ACCOUNT("create_account", "createAccount"),

  /** Updates the account of a workstation. Changes the device. */
  UPDATE_ACCOUNT("update_account", "updateAccount"),

  /** Removes the account of a workstation. Changes the device. */
  DELETE_ACCOUNT("delete_account", "deleteAccount"),

  /** Sets the account's default calendar object. Changes the device. */
  DEFAULT("default", "setDefaultCalendar"),

  /** Removes one item of a service. Changes the device. */
  DELETE("delete", "delete"),

  /** Creates or updates one item of a service. Changes the device. */
  PUT("put", "put"),

  /** Opens a sync round and asks the device what changed. Part of a sync round. */
  SUMMARY("summary", "summary"),

  /** Closes a sync round, submitting the reconciled result. Changes the device. */
  DONE("done", "done");

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
