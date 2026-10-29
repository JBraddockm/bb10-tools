package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
import com.jbraddockm.link.transport.cgi.ResponseElement;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseFailures;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.XmlConversation;
import com.jbraddockm.link.transport.xml.XmlElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Asks the device for the categories it can back up: a form post of {@code query=list&opt=rev2}, and
 * the reading of the {@code BackupList} answer. The richer {@code rev2} list carries sub-packages;
 * those are ignored, as selection here is by whole category.
 */
final class ListCategoriesConversation extends XmlConversation<BackupInventory> {

  @Override
  public Step<BackupInventory> start() {
    return new Step.Send<>(
        CgiRequest.Form.to(Endpoint.BACKUP)
            .withText(FormField.QUERY, "list")
            .withText(FormField.OPT, "rev2")
            .describedAs("Listing the backup categories"));
  }

  @Override
  protected Step<BackupInventory> onResponse(ResponseEnvelope response) {
    var failure = ResponseFailures.reportedError(response);
    if (failure.isPresent()) {
      return new Step.Fail<>(failure.get());
    }
    if (response.element().orElse(null) != ResponseElement.BACKUP_LIST) {
      return new Step.Fail<>(ResponseFailures.notExpected(response, ResponseElement.BACKUP_LIST));
    }
    var categories = new ArrayList<AvailableCategory>();
    for (var element :
        response.payload().at("Categories").map(XmlElement::children).orElse(List.of())) {
      if (!element.name().equals("Category")) {
        continue;
      }
      var id = element.attribute("id").orElse("");
      BackupCategory.fromWire(id)
          .ifPresent(
              category ->
                  categories.add(
                      new AvailableCategory(
                          category,
                          longAttribute(element, "bytesize"),
                          (int) longAttribute(element, "count"),
                          element.attribute("readystate").orElse("").strip().equals("1"),
                          element.attribute("perimetertype").orElse(null))));
    }
    return new Step.Finish<>(new BackupInventory(categories));
  }

  private static long longAttribute(XmlElement element, String name) {
    return element
        .attribute(name)
        .map(String::strip)
        .flatMap(
            text -> {
              try {
                return Optional.of(Long.parseLong(text));
              } catch (NumberFormatException _) {
                return Optional.<Long>empty();
              }
            })
        .orElse(0L);
  }
}
