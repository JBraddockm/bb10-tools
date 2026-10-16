package com.jbraddockm.link.session;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.properties.DeviceProperties;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.testing.FakeDevice;
import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

/** Checks how an answer that says "not authorized" is told apart from other errors. */
public final class StaleSessionChecks {

  private StaleSessionChecks() {}

  private static String answer(String description) {
    return "<RimTabletResponse><DynamicProperties><Status>Error</Status><Error>"
        + "<ErrorIdentifier>1011</ErrorIdentifier><ErrorDescription>"
        + description
        + "</ErrorDescription></Error></DynamicProperties></RimTabletResponse>";
  }

  private static boolean indicates(String body) {
    var response = new CgiResponse(200, "text/xml", body, null);
    return StaleSession.indicatedBy(
        CgiRequest.Form.to(Endpoint.DYNAMIC_PROPERTIES),
        response,
        Optional.of(ResponseEnvelope.parse(body)));
  }

  /**
   * Runs the checks.
   *
   * @throws Exception if the harness itself fails
   */
  public static void run() throws Exception {
    for (var description :
        List.of(
            "Error - authorization",
            "Error - authorization.",
            "  Error - authorization.  ",
            "error - AUTHORIZATION",
            "Error - authorization .")) {
      check(indicates(answer(description)), "a lost session: [" + description + "]");
    }
    for (var description :
        List.of(
            "Error - authorization failed",
            "Error - authorisation",
            "Error - something went wrong.",
            "authorization",
            "")) {
      check(!indicates(answer(description)), "not a lost session: [" + description + "]");
    }
    check(
        indicates(FakeDevice.UNAUTHORIZED_PROPERTIES),
        "the answer that a real device gives a request without a session");

    // a session that the device does not accept: renewed once, then said plainly
    try (var device = new FakeDevice("secret")) {
      device.propertiesXml = FakeDevice.UNAUTHORIZED_PROPERTIES;
      var session =
          DeviceSession.forTarget(
              new DeviceTarget(
                  InetAddress.getLoopbackAddress().getHostAddress(),
                  device.port(),
                  TransportScheme.HTTP,
                  new Authentication.Password("secret")));
      try (session) {
        var outcome = session.execute(new GetDeviceProperties(), CommandListener.SILENT);
        check(outcome instanceof CommandOutcome.Failed<DeviceProperties>, "it fails: " + outcome);
        var error = ((CommandOutcome.Failed<DeviceProperties>) outcome).error();
        equal("AUTH_FAILED", error.code().wireName(), "as an authentication failure");
        equal(
            "The device rejected the session right after a new login",
            error.message(),
            "after a renewal that did not help");
        equal(
            4,
            device.loginRequests.get(),
            "two logins of two requests each: the login was renewed once, and no more");
      }
    }
  }
}
