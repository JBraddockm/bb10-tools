package com.jbraddockm.link.testing;

/** Throwaway certificates and keys for tests, provided as resource files. */
public final class TestPki {

  private TestPki() {}

  /** The server keystore (PKCS #12, password {@code changeit}) as Base64. */
  public static final String SERVER_KEYSTORE_BASE64 =
      Resources.text(TestPki.class, "test-pki--server-keystore.base64.txt").strip();

  /** The password of the server keystore. */
  public static final String SERVER_KEYSTORE_PASSWORD = "changeit";
}
