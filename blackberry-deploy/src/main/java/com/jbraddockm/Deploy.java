package com.jbraddockm;

import java.util.Arrays;

public class Deploy {
  static void main(String[] args) {

    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");

    if (Arrays.asList(args).contains("-gui")) {
      System.err.println("Error: -gui is not supported in the native build.");
      System.exit(1);
    }

    System.setProperty("jdk.tls.client.protocols", "TLSv1");

    com.qnx.bbt.deploy.Deploy.main(args);
  }
}
