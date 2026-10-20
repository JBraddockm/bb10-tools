package com.jbraddockm.link.testing;

import java.util.ArrayList;
import java.util.List;

/** A minimal DER reader that takes a certificate request apart. */
public final class TestDer {

  /**
   * One element.
   *
   * @param tag the tag byte
   * @param encoded the element whole, tag and length included
   * @param content what is inside it
   */
  public record Element(int tag, byte[] encoded, byte[] content) {

    /**
     * The elements inside this one.
     *
     * @return the children, in order
     */
    public List<Element> children() {
      var found = new ArrayList<Element>();
      var offset = 0;
      while (offset < content.length) {
        var child = read(content, offset);
        found.add(child);
        offset += child.encoded().length;
      }
      return found;
    }
  }

  private TestDer() {}

  /**
   * Reads the element that starts at an offset.
   *
   * @param data the bytes
   * @param offset where the element starts
   * @return the element
   */
  public static Element read(byte[] data, int offset) {
    var tag = data[offset] & 0xFF;
    var first = data[offset + 1] & 0xFF;
    var lengthBytes = first < 0x80 ? 0 : first & 0x7F;
    var length = first;
    if (lengthBytes > 0) {
      length = 0;
      for (var index = 0; index < lengthBytes; index++) {
        length = (length << 8) | (data[offset + 2 + index] & 0xFF);
      }
    }
    var headerLength = 2 + lengthBytes;
    var content = new byte[length];
    System.arraycopy(data, offset + headerLength, content, 0, length);
    var encoded = new byte[headerLength + length];
    System.arraycopy(data, offset, encoded, 0, encoded.length);
    return new Element(tag, encoded, content);
  }
}
