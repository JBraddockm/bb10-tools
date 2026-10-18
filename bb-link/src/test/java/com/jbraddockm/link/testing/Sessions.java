package com.jbraddockm.link.testing;

import com.jbraddockm.link.session.Authentication;
import com.jbraddockm.link.session.DeviceSession;
import com.jbraddockm.link.session.DeviceTarget;
import com.jbraddockm.link.session.ExchangeObserver;
import com.jbraddockm.link.session.TransportScheme;
import java.net.InetAddress;

/** Sessions that checks open to a fake device. */
public final class Sessions {

  private Sessions() {}

  /**
   * A session to a fake device over plain HTTP, with the password that the fake device was made
   * with in the checks, {@code secret}.
   *
   * @param device the fake device
   * @return the session
   */
  public static DeviceSession password(FakeDevice device) {
    return DeviceSession.forTarget(
        new DeviceTarget(
            InetAddress.getLoopbackAddress().getHostAddress(),
            device.port(),
            TransportScheme.HTTP,
            new Authentication.Password("secret")),
        ExchangeObserver.NONE);
  }
}
