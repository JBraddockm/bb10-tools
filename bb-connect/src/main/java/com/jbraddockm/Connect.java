package com.jbraddockm;

import com.qnx.tools.bbt.qconndoor.AbstractQConnDoorCommandLine;
import com.qnx.tools.bbt.qconndoor.IConnectionRegistry;
import com.qnx.tools.bbt.qconndoor.settings.ISettingsManager;
import java.util.List;

public class Connect extends AbstractQConnDoorCommandLine {

  private static final int TARGET_PORT = 4455;

  public Connect(String identifier, List<Option> additionalOptions) {
    super(identifier, additionalOptions);
  }

  static void main(String[] args) {
    new Connect("<blackberry-connect>", List.of()).run(args);
  }

  @Override
  public void createConnection() throws Exception {
    var options = getOptions();

    var targetSetting =
        ISettingsManager.INSTANCE.addTargetSetting(
            options.get("targetHost").getValue(),
            TARGET_PORT,
            Integer.parseInt(options.get("targetVersion").getValue()),
            options.get("password").getValue(),
            false);

    byte[] sshKey = loadSSHKey();
    IConnectionRegistry.INSTANCE.createConnectionToTarget(targetSetting, sshKey, true);
  }

  @Override
  public boolean isPasswordOptional() {
    return true;
  }
}
