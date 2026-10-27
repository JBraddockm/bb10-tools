package com.jbraddockm.link.testing;

import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.cgi.DeviceTarget;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import java.net.InetAddress;

/** Opens sessions to a {@link FakeDevice}. */
public final class Sessions {

  private Sessions() {}

  /**
   * A session to a fake device over plain HTTP, authenticated with the password {@code secret}.
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
