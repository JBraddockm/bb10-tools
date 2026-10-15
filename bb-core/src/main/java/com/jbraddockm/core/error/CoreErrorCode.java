package com.jbraddockm.core.error;

/** The error codes shared by every daemon built on this runtime. */
public enum CoreErrorCode implements ErrorCode {

  /** The device rejected the password. */
  AUTH_FAILED,

  /** The device rejected the password and allows no further attempts. */
  AUTH_LOCKED,

  /** The login cookie was rejected because the clocks of host and device differ too much. */
  CLOCK_OUT_OF_SYNC,

  /** The device answered the login request with something that cannot be interpreted. */
  DEVICE_ERROR,

  /** The device could not be reached: timeout, refused connection, unknown host or TLS failure. */
  CONNECTION_FAILED,

  /** An established connection dropped while a command was running. */
  CONNECTION_LOST,

  /** The device received the command and reported that it failed. */
  COMMAND_FAILED,

  /** A parameter is missing, malformed or not accepted by the command. */
  INVALID_ARGUMENT,

  /** Any other input or output failure. */
  IO_ERROR,

  /** A request is not valid JSON, or lacks a usable {@code id} or {@code method}. */
  INVALID_REQUEST,

  /** The requested method does not exist. */
  UNKNOWN_METHOD,

  /** The session does not exist, was closed, or is still connecting. */
  UNKNOWN_SESSION,

  /** An unexpected failure that is not attributable to the caller or the device. */
  INTERNAL;

  @Override
  public String wireName() {
    return name();
  }
}
