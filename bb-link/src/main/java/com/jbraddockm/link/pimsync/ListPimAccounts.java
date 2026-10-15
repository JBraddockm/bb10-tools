package com.jbraddockm.link.pimsync;

import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.session.DeviceCommand;
import java.util.function.UnaryOperator;

/** Lists the accounts known to the device. Only reads. */
public record ListPimAccounts() implements DeviceCommand<PimAnswer> {

  @Override
  public Conversation<PimAnswer> conversation() {
    return new PimSyncConversation(
        PimCommand.LIST_ACCOUNTS, null, "Listing the PIM accounts", UnaryOperator.identity());
  }

  @Override
  public boolean readOnly() {
    return true;
  }
}
