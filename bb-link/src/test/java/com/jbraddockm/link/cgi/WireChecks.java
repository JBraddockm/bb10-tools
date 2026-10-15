package com.jbraddockm.link.cgi;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;

import com.jbraddockm.link.internal.Scrub;
import com.jbraddockm.link.xml.MalformedXmlException;
import com.jbraddockm.link.xml.XmlParser;
import java.util.List;

/** Checks the wire vocabulary: form encoding, the XML reader, envelopes and scrubbing. */
public final class WireChecks {

  private WireChecks() {}

  private static boolean rejects(String document) {
    try {
      XmlParser.parse(document);
      return false;
    } catch (MalformedXmlException _) {
      return true;
    }
  }

  /** Runs the checks. */
  public static void run() {
    encoding();
    xml();
    envelopes();
    scrubbing();
  }

  private static void encoding() {
    equal(
        "Get%20Dynamic%20Properties",
        FormEncoding.encode("Get Dynamic Properties"), "a space is %20");
    equal("a%26b%3Dc", FormEncoding.encode("a&b=c"), "reserved characters");
    equal("%C3%A9", FormEncoding.encode("é"), "UTF-8 bytes");
    equal("-_.~AZaz09", FormEncoding.encode("-_.~AZaz09"), "unreserved characters stay");

    var base = CgiRequest.Form.to(Endpoint.DYNAMIC_PROPERTIES);
    var form = base.echo(FormField.GET_DYNAMIC_PROPERTIES).withText(FormField.NAME, "a b");
    equal(
        "Get%20Dynamic%20Properties=Get%20Dynamic%20Properties&name=a%20b",
        form.encodedBody(), "fields keep their order");
    equal("", base.encodedBody(), "a form is immutable");
    equal("POST dynamicProperties.cgi", form.describe(), "description without values");
    equal(
        "Reading the device PIN",
        CgiRequest.Form.to(Endpoint.WIPE).describedAs("Reading the device PIN").describe(),
        "a request is described by what it does, not by the program's name");
    check(!form.emptyAnswerIsNormal(), "an empty answer is not normal by default");
    check(form.expectingNoBody().emptyAnswerIsNormal(), "unless the request says so");
    check(
        form.expectingNoBody().withText(FormField.NAME, "x").emptyAnswerIsNormal(),
        "the flag survives adding fields");
    equal("/cgi-bin/wipe.cgi", Endpoint.WIPE.path(), "endpoint path");
    equal(
        "/cgi-bin/pimsync.cgi",
        Endpoint.PIM_SYNC.path(),
        "the synchronisation of personal information");

    var get = CgiRequest.Get.to(Endpoint.PIM_SYNC);
    equal("/cgi-bin/pimsync.cgi", get.pathAndQuery(), "a GET without a query");
    equal("GET pimsync.cgi", get.describe(), "a GET without a purpose");
    var query =
        get.withText(QueryParameter.COMMAND, "list_accounts")
            .withText(QueryParameter.WORKSTATION_ID, "a b&c=d/é");
    equal(
        "/cgi-bin/pimsync.cgi?command=list_accounts&workstation_id=a%20b%26c%3Dd%2F%C3%A9",
        query.pathAndQuery(), "a query is encoded and keeps its order");
    equal("/cgi-bin/pimsync.cgi", get.pathAndQuery(), "a request is immutable");
    equal(
        "Reading the contacts",
        get.describedAs("Reading the contacts").describe(),
        "a GET is described by what it does");
    check(!get.emptyAnswerIsNormal(), "an empty answer to a GET is a lost session by default");
    check(get.expectingNoBody().emptyAnswerIsNormal(), "unless the request says otherwise");
    check(
        get.expectingNoBody().withText(QueryParameter.LIMIT, "5").emptyAnswerIsNormal(),
        "the flag survives adding parameters");
  }

  private static void xml() {
    var root =
        XmlParser.parse("<a z=\"1\" b=\"2\" m=\"3\"><b> text </b><b/><c><b>deep</b></c></a>");
    equal(
        List.of("z", "b", "m"), List.copyOf(root.attributes().keySet()), "attribute order is kept");
    equal("text", root.child("b").orElseThrow().text(), "text is stripped");
    equal(2, root.children("b").size(), "children by name");
    equal("deep", root.child("c").orElseThrow().childText("b").orElseThrow(), "nested text");
    equal(
        "text",
        root.descendant("b").orElseThrow().text(),
        "descendant search is in document order");
    check(rejects(""), "empty document");
    check(rejects("<a>"), "unclosed element");
    check(rejects("not xml"), "plain text");

    var hostile = "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><a>&e;</a>";
    var leaked = true;
    try {
      leaked = XmlParser.parse(hostile).text().contains("root:");
    } catch (MalformedXmlException _) {
      leaked = false;
    }
    check(!leaked, "external entities are not resolved");
  }

  private static void envelopes() {
    equal(
        "DynamicProperties",
        ResponseEnvelope.parse("<RimTabletResponse><DynamicProperties/></RimTabletResponse>")
            .name(),
        "plain envelope");
    equal(
        "DynamicProperties",
        ResponseEnvelope.parse("<RIM_tablet_response><DynamicProperties/></RIM_tablet_response>")
            .name(),
        "legacy root");
    equal(
        "DevicePIN",
        ResponseEnvelope.parse("<R><Rev>2</Rev><DevicePIN/></R>").name(),
        "revision marker followed by the payload");
    equal(
        "DevicePIN",
        ResponseEnvelope.parse("<R><Rev><DevicePIN/></Rev></R>").name(),
        "payload inside the revision marker");
    equal(
        "R",
        ResponseEnvelope.parse("<R/>").name(),
        "an envelope without content is its own payload");
    equal(
        ResponseElement.DEVICE_PIN,
        ResponseEnvelope.parse("<R><DevicePIN/></R>").element().orElseThrow(),
        "known element");
    check(ResponseEnvelope.parse("<R><Strange/></R>").element().isEmpty(), "unknown element");
  }

  private static void scrubbing() {
    equal("dtmauth=***; Path=/", Scrub.secrets("dtmauth=abc123; Path=/"), "cookie");
    equal(
        "/login.cgi?challenge_data=*** &x",
        Scrub.secrets("/login.cgi?challenge_data=AB12CD34 &x"),
        "answer");
    equal("err HTTP_COOKIE=***", Scrub.secrets("err HTTP_COOKIE=dtmauth=x; y=z"), "cookie echo");
    equal(
        "<PIN>***</PIN><Name>keep</Name>",
        Scrub.identifiers("<PIN>1234</PIN><Name>keep</Name>"),
        "identifiers");
    equal(
        "<Bbid x=\"1\">***</Bbid>", Scrub.identifiers("<Bbid x=\"1\">v</Bbid>"), "with attributes");
    equal("boom", Scrub.forDisplay("boom HTTP_COOKIE=dtmauth=x; y=z"), "display text");

    equal(
        "loginsession=***; path=/",
        Scrub.secrets("loginsession=abc123; path=/"),
        "cookie before the login");
    equal(
        "<Credentials User='dtm' Password='***'/>",
        Scrub.secrets("<Credentials User='dtm' Password='abc'/>"),
        "share password attribute");
    equal(
        "<Credentials Password=\"***\"/>",
        Scrub.secrets("<Credentials Password=\"abc\"/>"),
        "double quotes too");
    equal("<Pwd>***</Pwd>", Scrub.secrets("<Pwd>abc</Pwd>"), "share password element");
    equal(
        "<Imei>***</Imei>",
        Scrub.identifiers("<Imei>351234567890123</Imei>"),
        "IMEI as the device writes it");
    equal("<IMEI>***</IMEI>", Scrub.identifiers("<IMEI>1</IMEI>"), "and in capitals");
    equal("<Meid></Meid>", Scrub.identifiers("<Meid></Meid>"), "an empty element stays empty");
    equal(
        "<Scid>***</Scid><Ecid>***</Ecid>",
        Scrub.identifiers("<Scid>-1</Scid><Ecid>10504</Ecid>"),
        "chip identifiers");
    equal(
        "<User>dtm</User>",
        Scrub.identifiers("<User>dtm</User>"),
        "the share's user name is not a secret");
  }
}
