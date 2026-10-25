package com.jbraddockm.tunnel;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeForeignAccess;

/**
 * GraalVM native-image build-time feature that registers {@link TunDevice}'s libc downcalls.
 *
 * <p>Native image does not auto-discover Panama FFM downcalls, so without this registration the native
 * binary throws {@code MissingForeignRegistrationError} on the first {@code utun} downcall (after a
 * successful TLS/DTLS handshake). It registers exactly the {@link TunDevice#DOWNCALLS} descriptors, so
 * there is no layout guesswork.
 *
 * <p><b>Build-only.</b> It is loaded by {@code native-image} at build time (wired via
 * {@code --features=} in this module's {@code pom.xml}) and is never loaded at run time; the GraalVM
 * SDK it imports is a {@code provided} dependency and is not bundled in the jar.
 */
public final class ForeignRegistrationFeature implements Feature {

  @Override
  public void duringSetup(DuringSetupAccess access) {
    for (var descriptor : TunDevice.DOWNCALLS) {
      RuntimeForeignAccess.registerForDowncall(descriptor);
    }
  }
}
