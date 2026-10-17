package com.jbraddockm.link.session;

import com.jbraddockm.core.command.NoData;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.Step;
import java.util.Optional;

/**
 * A command that asks the device for nothing: running it makes a session log in, and that is all.
 *
 * <p>A connection that only logs in is for a device that answers the login but not the programs
 * that describe it. Over Wi-Fi, a device answers {@code login.cgi} and closes the connection on the
 * programs that read its properties. It is read-only, so a dropped connection during the login is
 * retried like any other.
 */
public record LoginOnly() implements DeviceCommand<NoData> {

  @Override
  public Conversation<NoData> conversation() {
    return new Finished();
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  /** A conversation that is over before it begins. */
  private static final class Finished implements Conversation<NoData> {

    @Override
    public Step<NoData> start() {
      return new Step.Finish<>(NoData.INSTANCE);
    }

    @Override
    public Step<NoData> onAnswer(CgiResponse response, Optional<ResponseEnvelope> envelope) {
      throw new IllegalStateException("nothing was asked, so nothing can be answered");
    }
  }
}
