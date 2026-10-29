package com.jbraddockm.link.endpoint.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PackageType: wire names, csv and selection")
class PackageTypeTest {

  @Test
  @DisplayName("joins the csv in the device's own order, whatever order it was given")
  void csvInDeviceOrder() {
    assertThat(PackageType.csv(List.of(PackageType.DATA, PackageType.SYSTEM)))
        .isEqualTo("system,data");
    assertThat(PackageType.csv(List.of(PackageType.BIN, PackageType.DATA, PackageType.SYSTEM)))
        .isEqualTo("system,data,bin");
  }

  @Test
  @DisplayName("selects known names, in device order and without duplicates")
  void selectsKnownNames() {
    assertThat(PackageType.select(List.of("data", "system", "data")))
        .containsExactly(PackageType.SYSTEM, PackageType.DATA);
  }

  @Test
  @DisplayName("refuses an unknown package type")
  void refusesUnknown() {
    assertThatThrownBy(() -> PackageType.select(List.of("media")))
        .isInstanceOf(InvalidArgumentException.class);
  }
}
