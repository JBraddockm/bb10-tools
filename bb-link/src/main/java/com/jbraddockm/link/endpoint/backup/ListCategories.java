package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;

/**
 * Reads the categories the device can back up, with their sizes. Only reads, so it is safe to send
 * again after a dropped connection.
 */
final class ListCategories implements DeviceCommand<BackupInventory> {

  ListCategories() {}

  @Override
  public Conversation<BackupInventory> conversation() {
    return new ListCategoriesConversation();
  }

  @Override
  public boolean readOnly() {
    return true;
  }
}
