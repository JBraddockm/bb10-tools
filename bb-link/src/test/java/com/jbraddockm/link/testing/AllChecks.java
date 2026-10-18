package com.jbraddockm.link.testing;

import com.jbraddockm.link.cgi.WireChecks;
import com.jbraddockm.link.daemon.DaemonChecks;
import com.jbraddockm.link.pairing.BoundaryChecks;
import com.jbraddockm.link.pairing.CertificateRetrievalChecks;
import com.jbraddockm.link.pairing.IdentityChecks;
import com.jbraddockm.link.pairing.PairingChecks;
import com.jbraddockm.link.pairing.PkiChecks;
import com.jbraddockm.link.session.CertificateChecks;
import com.jbraddockm.link.session.SessionChecks;
import com.jbraddockm.link.session.StaleSessionChecks;
import com.jbraddockm.link.update.InstallChecks;
import com.jbraddockm.link.update.UninstallChecks;

/** Runs every check of bb-link and sets the exit status. */
public final class AllChecks {

  private AllChecks() {}

  /**
   * Runs the checks.
   *
   * @param arguments ignored
   * @throws Exception if the harness itself fails
   */
  public static void main(String[] arguments) throws Exception {
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
    WireChecks.run();
    SessionChecks.run();
    CertificateChecks.run();
    StaleSessionChecks.run();
    InstallChecks.run();
    UninstallChecks.run();
    PkiChecks.run();
    IdentityChecks.run();
    CertificateRetrievalChecks.run();
    PairingChecks.run();
    BoundaryChecks.run();
    DaemonChecks.run();
    System.exit(Checks.finish("bb-link checks"));
  }
}
