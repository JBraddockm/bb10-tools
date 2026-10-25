package com.jbraddockm.tunnel;

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.List;

/**
 * A macOS <b>utun</b> interface implemented in pure Java via the Panama Foreign Function &amp;
 * Memory API (no native build, no JNI). It exposes the tunnel to the OS so normal applications' IP
 * traffic can be bridged to/from the DTLS data channel.
 *
 * <p><b>Requires sudo</b> (creating a utun is privileged) and <b>{@code
 * --enable-native-access=ALL-UNNAMED}</b> on JDK 25.
 *
 * <p><b>How a utun is created on macOS</b> (the four syscalls in the constructor):
 *
 * <ol>
 *   <li>{@code socket(PF_SYSTEM, SOCK_DGRAM, SYSPROTO_CONTROL)} — a kernel-control socket.
 *   <li>{@code ioctl(CTLIOCGINFO, &ctl_info)} with name {@code "com.apple.net.utun_control"} to
 *       resolve the utun control id.
 *   <li>{@code connect(sockaddr_ctl)} with {@code sc_unit = unit+1} to create {@code utun<unit>};
 *       we try units 0..15 until one is free.
 *   <li>{@code getsockopt(UTUN_OPT_IFNAME)} to read back the assigned interface name (e.g.
 *       "utun4").
 * </ol>
 *
 * <p><b>The 4-byte AF header.</b> Every utun read/write is prefixed by a 4-byte big-endian
 * address-family word ({@code AF_INET=2} or {@code AF_INET6=0x1e}). {@link #read} strips it; {@link
 * #write} prepends it (choosing the family from the IP version nibble).
 */
public class TunDevice implements AutoCloseable {

  /**
   * The libc downcall descriptors this device binds. {@link ForeignRegistrationFeature} registers
   * these so the GraalVM native image can perform the downcalls (native image does not
   * auto-discover FFM calls). <b>Keep in sync with the {@code h(...)} calls in the constructor.</b>
   */
  static final List<FunctionDescriptor> DOWNCALLS =
      List.of(
          FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT), // socket
          FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, ADDRESS), // ioctl
          FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT), // connect
          FunctionDescriptor.of(JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG), // read / write
          FunctionDescriptor.of(JAVA_INT, JAVA_INT), // close
          FunctionDescriptor.of(
              JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS), // getsockopt
          FunctionDescriptor.of(ADDRESS, JAVA_INT), // strerror
          FunctionDescriptor.of(ADDRESS)); // __error
  // --- macOS constants ---
  private static final int PF_SYSTEM = 32;
  private static final int SOCK_DGRAM = 2;
  private static final int SYSPROTO_CONTROL = 2;
  private static final int AF_SYSTEM = 32;
  private static final int AF_SYS_CONTROL = 2;
  private static final int UTUN_OPT_IFNAME = 2; // getsockopt option under level SYSPROTO_CONTROL
  // CTLIOCGINFO = _IOWR('N', 3, struct ctl_info); struct ctl_info = { u32 ctl_id; char ctl_name[96]
  // } = 100 bytes
  private static final long CTLIOCGINFO = 0xC0644E03L;
  private static final String UTUN_CONTROL_NAME = "com.apple.net.utun_control";
  private final Linker linker = Linker.nativeLinker();
  private final SymbolLookup std = linker.defaultLookup();
  private final MethodHandle socket,
      ioctl,
      connect,
      read,
      write,
      close,
      getsockopt,
      strerror,
      errnoLoc;
  private final int fd;
  private final String ifName;
  private final Arena arena = Arena.ofShared();

  public TunDevice() throws Throwable {
    // Bind the libc functions we need as Panama downcall handles.
    socket = h("socket", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
    ioctl = h("ioctl", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, ADDRESS));
    connect = h("connect", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
    read = h("read", FunctionDescriptor.of(JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG));
    write = h("write", FunctionDescriptor.of(JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG));
    close = h("close", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    getsockopt =
        h(
            "getsockopt",
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    strerror = h("strerror", FunctionDescriptor.of(ADDRESS, JAVA_INT));
    errnoLoc = h("__error", FunctionDescriptor.of(ADDRESS)); // macOS: errno lives at *__error()

    // 1) socket(PF_SYSTEM, SOCK_DGRAM, SYSPROTO_CONTROL)
    int s = (int) socket.invoke(PF_SYSTEM, SOCK_DGRAM, SYSPROTO_CONTROL);
    if (s < 0) throw err("socket");

    // 2) ioctl(s, CTLIOCGINFO, &ctl_info) to resolve the utun control id by name.
    MemorySegment ctlInfo = arena.allocate(100);
    ctlInfo.setString(4, UTUN_CONTROL_NAME); // ctl_name at offset 4 (after the u32 ctl_id)
    if ((int) ioctl.invoke(s, CTLIOCGINFO, ctlInfo) < 0) {
      close.invoke(s);
      throw err("ioctl CTLIOCGINFO");
    }
    int ctlId = ctlInfo.get(JAVA_INT, 0);

    // 3) connect(s, sockaddr_ctl) to create utun<unit>. sc_unit = unit+1; try 0..15 for a free
    // unit.
    // struct sockaddr_ctl { u8 sc_len; u8 sc_family; u16 ss_sysaddr; u32 sc_id; u32 sc_unit; u32
    // sc_reserved[5]; } = 32 bytes
    int chosen = -1;
    int unit = 0;
    for (unit = 0; unit < 16; unit++) {
      MemorySegment sc = arena.allocate(32);
      sc.set(JAVA_BYTE, 0, (byte) 32); // sc_len = sizeof(struct)
      sc.set(JAVA_BYTE, 1, (byte) AF_SYSTEM); // sc_family
      sc.set(JAVA_SHORT, 2, (short) AF_SYS_CONTROL); // ss_sysaddr
      sc.set(JAVA_INT, 4, ctlId); // sc_id (from CTLIOCGINFO)
      sc.set(JAVA_INT, 8, unit + 1); // sc_unit → utun<unit>
      if ((int) connect.invoke(s, sc, 32) == 0) {
        chosen = unit;
        break;
      }
    }
    if (chosen < 0) {
      close.invoke(s);
      throw err("connect sockaddr_ctl (all utun units busy?)");
    }
    fd = s;

    // 4) getsockopt(SYSPROTO_CONTROL, UTUN_OPT_IFNAME) to read the interface name ("utunN").
    MemorySegment nameBuf = arena.allocate(32);
    MemorySegment nameLen = arena.allocate(JAVA_INT);
    nameLen.set(JAVA_INT, 0, 32);
    getsockopt.invoke(fd, SYSPROTO_CONTROL, UTUN_OPT_IFNAME, nameBuf, nameLen);
    ifName = nameBuf.getString(0);
  }

  /** The kernel-assigned interface name, e.g. "utun4". */
  public String name() {
    return ifName;
  }

  /** Reads one raw IP packet from the tun, stripping the leading 4-byte AF header. Blocks. */
  public byte[] read() throws Throwable {
    MemorySegment buf = arena.allocate(2048);
    long n = (long) read.invoke(fd, buf, 2048L);
    if (n <= 4) return new byte[0]; // nothing but (or less than) the AF header
    byte[] pkt = new byte[(int) n - 4];
    MemorySegment.copy(buf, JAVA_BYTE, 4, pkt, 0, pkt.length); // skip the 4-byte AF header
    return pkt;
  }

  /**
   * Writes one raw IP packet to the tun, prepending the 4-byte AF header. The family is chosen from
   * the IP version nibble: {@code 0x60..} → IPv6 ({@code AF_INET6=0x1e}), else IPv4 ({@code
   * AF_INET=2}).
   */
  public void write(byte[] pkt, int off, int len) throws Throwable {
    boolean v6 = (pkt[off] & 0xf0) == 0x60;
    MemorySegment buf = arena.allocate(4 + len);
    buf.set(
        JAVA_INT, 0, v6 ? 0x1E000000 : 0x02000000); // AF header, big-endian (htonl of 0x1e / 0x02)
    MemorySegment.copy(pkt, off, buf, JAVA_BYTE, 4, len);
    write.invoke(fd, buf, (long) (4 + len));
  }

  @Override
  public void close() {
    try {
      close.invoke(fd);
    } catch (Throwable ignored) {
    }
    arena.close();
  }

  // --- helpers ---

  /** Binds a libc function by name to a Panama downcall handle. */
  private MethodHandle h(String name, FunctionDescriptor d) {
    return linker.downcallHandle(
        std.find(name).orElseThrow(() -> new RuntimeException("missing libc: " + name)), d);
  }

  /** Builds a RuntimeException carrying the current errno and strerror text. */
  private RuntimeException err(String what) throws Throwable {
    MemorySegment ep = (MemorySegment) errnoLoc.invoke();
    int e = ep.reinterpret(4).get(JAVA_INT, 0);
    MemorySegment msg = ((MemorySegment) strerror.invoke(e)).reinterpret(256);
    return new RuntimeException(what + " failed: errno=" + e + " " + msg.getString(0));
  }
}
