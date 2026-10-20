package com.jbraddockm.link.testing;

import com.jbraddockm.link.session.DeviceSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;

/** A {@link FakeDevice} together with a session to it, for tests of operations on a device. */
public final class DeviceFixture implements AutoCloseable {

  private static TestAuthority sharedAuthority;

  private final FakeDevice device;
  private final DeviceSession session;

  private DeviceFixture(boolean withAuthority) {
    try {
      device = new FakeDevice("secret");
    } catch (IOException failure) {
      throw new UncheckedIOException("the fake device cannot be started", failure);
    }
    if (withAuthority) {
      device.authority = authority();
    }
    session = Sessions.password(device);
  }

  /**
   * A device that can pair and can sign a certificate request.
   *
   * @return the fixture; close it
   */
  public static DeviceFixture withAuthority() {
    return new DeviceFixture(true);
  }

  /**
   * A device that cannot: it answers error 7 to a pairing and error 1 to a request for a
   * certificate, as the real one does when it has nothing to go on.
   *
   * @return the fixture; close it
   */
  public static DeviceFixture withoutAuthority() {
    return new DeviceFixture(false);
  }

  /**
   * The fake device.
   *
   * @return it
   */
  public FakeDevice device() {
    return device;
  }

  /**
   * A session to the fake device, over plain HTTP, with its password.
   *
   * @return it
   */
  public DeviceSession session() {
    return session;
  }

  @Override
  public void close() {
    session.close();
    device.close();
  }

  /** One authority for all tests: generating its RSA key is slow, and it keeps no state. */
  private static synchronized TestAuthority authority() {
    try {
      if (sharedAuthority == null) {
        sharedAuthority = new TestAuthority();
      }
      return sharedAuthority;
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("the test authority cannot be made", failure);
    }
  }
}
