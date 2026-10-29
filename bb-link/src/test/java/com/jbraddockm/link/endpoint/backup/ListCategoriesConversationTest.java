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

  private static Step<BackupInventory> answer(String xml) {
    var conversation = new ListCategoriesConversation();
    conversation.start();
    return conversation.onAnswer(
        new CgiResponse(200, "text/xml", xml, null), ResponseEnvelope.parse(xml));
  }

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
  @DisplayName("reads the rev2 sub-packages of the app category")
  void readsSubPackages() {
    var rev2 =
        """
        <RimTabletResponse><Rev>2</Rev><BackupList><Status>Success</Status><Categories>\
        <Category id="app" perimetertype="personal" name="Application Data" bytesize="247677952" count="232" readystate="1">\
        <SubCategories>\
        <SubCategory pkgid="systemapps" type="system" detail="default" count="228" bytesize="243062784" name="System Applications"/>\
        <SubCategory pkgid="gYABgKAOw1czN6neiAT72SGO-ns" version="10.3.3.213" type="data" count="1" bytesize="1263616" name="BlackBerry Runtime"/>\
        <SubCategory pkgid="andrBgJqi6F824h1CwDnH9pryMe8" type="bin" count="1" bytesize="3219456" name=""/>\
        </SubCategories></Category>\
        <Category id="settings" perimetertype="personal" name="Settings" bytesize="4096" count="3" readystate="1"/>\
        </Categories></BackupList></RimTabletResponse>""";

    var inventory = ((Step.Finish<BackupInventory>) answer(rev2)).result();

    var app = inventory.find(BackupCategory.APP).orElseThrow();
    assertThat(app.subPackages())
        .extracting(SubPackage::type)
        .containsExactly(PackageType.SYSTEM, PackageType.DATA, PackageType.BIN);
    assertThat(app.subPackages().get(1).pkgid()).isEqualTo("gYABgKAOw1czN6neiAT72SGO-ns");
    assertThat(app.subPackages().get(1).bytesize()).isEqualTo(1263616);
    assertThat(inventory.find(BackupCategory.SETTINGS).orElseThrow().subPackages()).isEmpty();
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
}
