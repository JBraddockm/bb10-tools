package com.jbraddockm.link.pairing.pki;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Builds ASN.1 in its distinguished encoding, the small part of it that a certificate request and a
 * certificate need.
 *
 * <p>Every method returns a complete element, tag and length included, so that elements nest by
 * passing one to another.
 */
public final class Der {

  private static final int BOOLEAN = 0x01;
  private static final int INTEGER = 0x02;
  private static final int BIT_STRING = 0x03;
  private static final int OCTET_STRING = 0x04;
  private static final int NULL = 0x05;
  private static final int OBJECT_IDENTIFIER = 0x06;
  private static final int UTF8_STRING = 0x0C;
  private static final int UTC_TIME = 0x17;
  private static final int GENERALIZED_TIME = 0x18;
  private static final int SEQUENCE = 0x30;
  private static final int SET = 0x31;
  private static final int CONTEXT_CONSTRUCTED = 0xA0;

  private static final DateTimeFormatter UTC_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter GENERALIZED_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

  private Der() {}

  /**
   * A SEQUENCE.
   *
   * @param parts the elements it holds, in order
   * @return the encoded element
   */
  public static byte[] sequence(byte[]... parts) {
    return encode(SEQUENCE, concatenate(parts));
  }

  /**
   * A SET. The caller keeps the elements in the order the encoding requires; a set of one element,
   * as in a name, has no order to get wrong.
   *
   * @param parts the elements it holds
   * @return the encoded element
   */
  public static byte[] set(byte[]... parts) {
    return encode(SET, concatenate(parts));
  }

  /**
   * A context-specific, constructed element, as {@code [number]} in a module: an explicit tag, and
   * also an implicit one over a SEQUENCE or SET.
   *
   * @param number the tag number, 0 to 30
   * @param parts the elements it holds
   * @return the encoded element
   * @throws IllegalArgumentException if the number is out of range
   */
  public static byte[] contextual(int number, byte[]... parts) {
    if (number < 0 || number > 30) {
      throw new IllegalArgumentException("tag number out of range: " + number);
    }
    return encode(CONTEXT_CONSTRUCTED | number, concatenate(parts));
  }

  /**
   * An INTEGER.
   *
   * @param value the value
   * @return the encoded element, in the shortest two's complement form
   */
  public static byte[] integer(BigInteger value) {
    return encode(INTEGER, value.toByteArray());
  }

  /**
   * An INTEGER.
   *
   * @param value the value
   * @return the encoded element
   */
  public static byte[] integer(long value) {
    return integer(BigInteger.valueOf(value));
  }

  /**
   * A NULL.
   *
   * @return the encoded element
   */
  public static byte[] nullValue() {
    return encode(NULL, new byte[0]);
  }

  /**
   * An OBJECT IDENTIFIER.
   *
   * @param dotted the arcs, as in {@code 2.5.4.3}
   * @return the encoded element
   * @throws IllegalArgumentException if it is not at least two numeric arcs, the first 0 to 2
   */
  public static byte[] objectIdentifier(String dotted) {
    var arcs = dotted.split("\\.");
    if (arcs.length < 2) {
      throw new IllegalArgumentException("an object identifier has at least two arcs: " + dotted);
    }
    var first = Long.parseLong(arcs[0]);
    var second = Long.parseLong(arcs[1]);
    if (first < 0 || first > 2 || second < 0 || (first < 2 && second > 39)) {
      throw new IllegalArgumentException("not an object identifier: " + dotted);
    }
    var content = new ByteArrayOutputStream();
    base128(content, first * 40 + second);
    for (var index = 2; index < arcs.length; index++) {
      base128(content, Long.parseLong(arcs[index]));
    }
    return encode(OBJECT_IDENTIFIER, content.toByteArray());
  }

  /**
   * A UTF8String.
   *
   * @param text the text
   * @return the encoded element
   */
  public static byte[] utf8String(String text) {
    return encode(UTF8_STRING, text.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * A BIT STRING of whole bytes.
   *
   * @param bytes the content
   * @return the encoded element, with no unused bits
   */
  public static byte[] bitString(byte[] bytes) {
    var content = new byte[bytes.length + 1];
    System.arraycopy(bytes, 0, content, 1, bytes.length);
    return encode(BIT_STRING, content);
  }

  /**
   * An OCTET STRING.
   *
   * @param bytes the content
   * @return the encoded element
   */
  public static byte[] octetString(byte[] bytes) {
    return encode(OCTET_STRING, bytes);
  }

  /**
   * A point in time: a UTCTime for the years 1950 to 2049, a GeneralizedTime otherwise, as a
   * certificate requires.
   *
   * @param instant the time, to the second
   * @return the encoded element
   */
  public static byte[] time(Instant instant) {
    var year = instant.atZone(ZoneOffset.UTC).getYear();
    return year >= 1950 && year <= 2049
        ? encode(UTC_TIME, UTC_TIME_FORMAT.format(instant).getBytes(StandardCharsets.US_ASCII))
        : encode(
            GENERALIZED_TIME,
            GENERALIZED_TIME_FORMAT.format(instant).getBytes(StandardCharsets.US_ASCII));
  }

  /**
   * Joins byte arrays.
   *
   * @param parts the arrays, in order
   * @return one array
   */
  public static byte[] concatenate(byte[]... parts) {
    var joined = new ByteArrayOutputStream();
    Arrays.stream(parts).forEach(joined::writeBytes);
    return joined.toByteArray();
  }

  private static byte[] encode(int tag, byte[] content) {
    var element = new ByteArrayOutputStream();
    element.write(tag);
    writeLength(element, content.length);
    element.writeBytes(content);
    return element.toByteArray();
  }

  private static void writeLength(ByteArrayOutputStream output, int length) {
    if (length < 0x80) {
      output.write(length);
      return;
    }
    var size = 0;
    for (var remaining = length; remaining > 0; remaining >>>= 8) {
      size++;
    }
    output.write(0x80 | size);
    for (var shift = (size - 1) * 8; shift >= 0; shift -= 8) {
      output.write(length >>> shift);
    }
  }

  private static void base128(ByteArrayOutputStream output, long value) {
    var groups = 1;
    for (var remaining = value >>> 7; remaining > 0; remaining >>>= 7) {
      groups++;
    }
    for (var group = groups - 1; group >= 0; group--) {
      var bits = (int) ((value >>> (group * 7)) & 0x7F);
      output.write(group == 0 ? bits : bits | 0x80);
    }
  }
}
