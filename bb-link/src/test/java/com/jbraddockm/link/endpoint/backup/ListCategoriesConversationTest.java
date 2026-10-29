package com.jbraddockm.link.endpoint.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListCategoriesConversation: reading the BackupList")
class ListCategoriesConversationTest {

  private static final String LIST =
      """
      <RimTabletResponse><BackupList><Status>Success</Status><Categories>\
      <Category id="app" perimetertype="personal" name="Applications" bytesize="1048576" count="12" readystate="1"/>\
      <Category id="media" perimetertype="personal" name="Media" bytesize="2097152" count="40" readystate="1"/>\
      <Category id="settings" perimetertype="personal" name="Settings" bytesize="4096" count="3" readystate="1"/>\
      </Categories></BackupList></RimTabletResponse>""";

  @Test
  @DisplayName("reads every category with its size")
  void readsCategories() {
    var step = answer(LIST);

    assertThat(step).isInstanceOf(Step.Finish.class);
    var inventory = ((Step.Finish<BackupInventory>) step).result();
    assertThat(inventory.categories())
        .extracting(AvailableCategory::category)
        .containsExactly(BackupCategory.APP, BackupCategory.MEDIA, BackupCategory.SETTINGS);
    assertThat(inventory.find(BackupCategory.MEDIA).orElseThrow().bytesize()).isEqualTo(2097152);
    assertThat(inventory.find(BackupCategory.SETTINGS).orElseThrow().ready()).isTrue();
  }

  @Test
  @DisplayName("fails when the device reports an error")
  void failsOnError() {
    var step =
        answer(
            "<RimTabletResponse><BackupList><Status>Error</Status>"
                + "<Error><ErrorIdentifier>8</ErrorIdentifier>"
                + "<ErrorDescription>invalid request</ErrorDescription></Error></BackupList>"
                + "</RimTabletResponse>");

    assertThat(step).isInstanceOf(Step.Fail.class);
  }

  @Test
  @DisplayName("fails when the answer is not XML")
  void failsOnNonXml() {
    var conversation = new ListCategoriesConversation();
    var response = new CgiResponse(200, "text/plain", "not xml", null);

    assertThat(conversation.onAnswer(response, null)).isInstanceOf(Step.Fail.class);
  }

  private static Step<BackupInventory> answer(String xml) {
    var conversation = new ListCategoriesConversation();
    conversation.start();
    return conversation.onAnswer(
        new CgiResponse(200, "text/xml", xml, null), ResponseEnvelope.parse(xml));
  }
}
