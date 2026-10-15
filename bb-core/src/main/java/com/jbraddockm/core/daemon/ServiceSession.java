package com.jbraddockm.core.daemon;

/** A session a service keeps for one client connection to one device. */
public interface ServiceSession extends AutoCloseable {

  /** Releases everything the session holds. Must not throw and may be called more than once. */
  @Override
  void close();
}
