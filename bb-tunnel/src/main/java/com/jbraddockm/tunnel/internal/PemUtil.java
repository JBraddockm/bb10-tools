package com.jbraddockm.tunnel.internal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.bouncycastle.util.io.pem.PemReader;

/**
 * Loads DER blocks of a given PEM type (e.g. "CERTIFICATE", "PRIVATE KEY") from a classpath
 * resource or file.
 */
public final class PemUtil {
  private PemUtil() {}

  /**
   * Reads every PEM object whose type contains {@code type} from the classpath resource at {@code
   * resourcePath}, returning each one's raw DER content. Used to load the bundled tunnel cert chain
   * and PKCS#8 key from {@code src/main/resources/certs/}.
   */
  public static List<byte[]> readBlocks(String resourcePath, String type) throws Exception {
    try (InputStream is = PemUtil.class.getResourceAsStream(resourcePath)) {
      if (is == null)
        throw new FileNotFoundException("classpath resource not found: " + resourcePath);
      return read(new InputStreamReader(is, StandardCharsets.US_ASCII), type);
    }
  }

  /** As {@link #readBlocks(String, String)}, but reads a PEM file from the filesystem. */
  public static List<byte[]> readBlocksFromFile(Path pem, String type) throws Exception {
    try (Reader r = Files.newBufferedReader(pem, StandardCharsets.US_ASCII)) {
      return read(r, type);
    }
  }

  /** A decoded PEM object: its exact {@code type} line (e.g. {@code EC PRIVATE KEY}) and raw DER. */
  public record Block(String type, byte[] der) {}

  /** Reads every PEM object in {@code pem} as (exact type, DER), in file order. */
  public static List<Block> readAllFromFile(Path pem) throws IOException {
    try (Reader r = Files.newBufferedReader(pem, StandardCharsets.US_ASCII);
        PemReader pr = new PemReader(r)) {
      var out = new ArrayList<Block>();
      org.bouncycastle.util.io.pem.PemObject o;
      while ((o = pr.readPemObject()) != null) {
        out.add(new Block(o.getType(), o.getContent()));
      }
      return out;
    }
  }

  /** Core: pull every PEM object whose type contains {@code type} from {@code src} as raw DER. */
  private static List<byte[]> read(Reader src, String type) throws IOException {
    List<byte[]> out = new ArrayList<>();
    try (PemReader r = new PemReader(src)) {
      org.bouncycastle.util.io.pem.PemObject o;
      while ((o = r.readPemObject()) != null)
        if (o.getType().contains(type)) out.add(o.getContent());
    }
    return out;
  }
}
