package com.jbraddockm.link.endpoint.pairing.pki;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Reads and writes the textual form of DER data: blocks between BEGIN and END lines. */
public final class Pem {

  /**
   * One block of a text.
   *
   * @param label what the block holds, such as {@code CERTIFICATE}
   * @param content the decoded bytes
   */
  public record Block(String label, byte[] content) {

    /**
     * Writes the block again, in the form this class writes.
     *
     * @return the text, ending in a line break
     */
    public String text() {
      return encode(label, content);
    }
  }

  private static final Pattern BLOCK =
      Pattern.compile("-----BEGIN ([A-Z0-9 ]+)-----(.*?)-----END \\1-----", Pattern.DOTALL);
  private static final int LINE_LENGTH = 64;

  private Pem() {}

  /**
   * Writes one block, with lines of 64 characters and a line feed at the end of each.
   *
   * @param label the label, such as {@code CERTIFICATE REQUEST}
   * @param content the bytes
   * @return the text
   */
  public static String encode(String label, byte[] content) {
    var base64 = Base64.getEncoder().encodeToString(content);
    var text = new StringBuilder("-----BEGIN ").append(label).append("-----\n");
    for (var start = 0; start < base64.length(); start += LINE_LENGTH) {
      text.append(base64, start, Math.min(base64.length(), start + LINE_LENGTH)).append('\n');
    }
    return text.append("-----END ").append(label).append("-----\n").toString();
  }

  /**
   * Finds every block of a text, whatever its label, in order.
   *
   * @param text the text, which may hold anything around the blocks
   * @return the blocks; empty if there is none
   * @throws IllegalArgumentException if a block does not hold base64
   */
  public static List<Block> blocks(String text) {
    var blocks = new ArrayList<Block>();
    var matcher = BLOCK.matcher(text);
    while (matcher.find()) {
      var body = matcher.group(2).replaceAll("\\s", "");
      blocks.add(new Block(matcher.group(1), Base64.getDecoder().decode(body)));
    }
    return blocks;
  }

  /**
   * Finds the blocks with a label.
   *
   * @param text the text
   * @param label the label to keep
   * @return those blocks, in order
   */
  public static List<Block> blocks(String text, String label) {
    return blocks(text).stream().filter(block -> block.label().equals(label)).toList();
  }

  /**
   * Finds the first block with a label.
   *
   * @param text the text
   * @param label the label
   * @return the block, if there is one
   */
  public static Optional<Block> first(String text, String label) {
    return blocks(text, label).stream().findFirst();
  }

  /**
   * The size of a text as it is sent, in bytes.
   *
   * @param text the text
   * @return the length in UTF-8
   */
  public static int size(String text) {
    return text.getBytes(StandardCharsets.UTF_8).length;
  }
}
