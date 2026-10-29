package com.jbraddockm.link.endpoint.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BackupCategory: wire names, mode value and selection")
class BackupCategoryTest {

  @Test
  @DisplayName("joins the mode in the device's own order, whatever order it was given")
  void modeValueIsInDeviceOrder() {
    assertThat(BackupCategory.modeValue(List.of(BackupCategory.SETTINGS, BackupCategory.APP)))
        .isEqualTo("app_settings");
    assertThat(
            BackupCategory.modeValue(
                List.of(BackupCategory.MEDIA, BackupCategory.SETTINGS, BackupCategory.APP)))
        .isEqualTo("app_media_settings");
  }

  @Test
  @DisplayName("names the archive entry for each category")
  void tarEntryName() {
    assertThat(BackupCategory.SETTINGS.tarEntryName()).isEqualTo("Archive/settings.tar");
  }

  @Test
  @DisplayName("selects known names, in device order and without duplicates")
  void selectsKnownNames() {
    assertThat(BackupCategory.select(List.of("settings", "app", "settings")))
        .containsExactly(BackupCategory.APP, BackupCategory.SETTINGS);
  }

  @Test
  @DisplayName("refuses an unknown category name")
  void refusesUnknown() {
    assertThatThrownBy(() -> BackupCategory.select(List.of("contacts")))
        .isInstanceOf(InvalidArgumentException.class);
  }
}
