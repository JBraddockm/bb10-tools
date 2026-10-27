package com.jbraddockm.link.endpoint.properties;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the device reports about itself.
 *
 * <p>The fields that identify one device or its owner are the serial number, the IMEI and the
 * BlackBerry IDs of the perimeters.
 *
 * @param deviceName the name the owner gave the device; may be {@code null}
 * @param modelName the model name; may be {@code null}
 * @param hardwareId the hardware identifier as the device writes it, for example {@code 0x0a0b0c0d}
 * @param imei the IMEI; may be {@code null}
 * @param batteryLevel the charge in percent; may be {@code null}
 * @param batteryHealth the battery's state of health in percent; may be {@code null}
 * @param hasPower whether the device reports external power; may be {@code null}
 * @param freeApplicationSpace the bytes available for applications; may be {@code null}
 * @param boardSerialNumber the board serial number; may be {@code null}
 * @param refurbDate the refurbishment date text; may be {@code null}
 * @param policyRestrictions the policy restrictions text; may be {@code null}
 * @param perimeters the perimeters of the device
 * @param packages the installed packages in the order the device lists them
 * @param removedPackages packages that were removed but whose data the device keeps
 */
public record DeviceProperties(
    String deviceName,
    String modelName,
    String hardwareId,
    String imei,
    Integer batteryLevel,
    Integer batteryHealth,
    Boolean hasPower,
    Long freeApplicationSpace,
    String boardSerialNumber,
    String refurbDate,
    String policyRestrictions,
    List<Perimeter> perimeters,
    List<InstalledPackage> packages,
    List<InstalledPackage> removedPackages)
    implements CommandResult {

  /** Takes immutable copies of the lists. */
  public DeviceProperties {
    perimeters = List.copyOf(perimeters);
    packages = List.copyOf(packages);
    removedPackages = List.copyOf(removedPackages);
  }

  /**
   * Finds the operating system package.
   *
   * @return the first package of type {@link PackageType#OPERATING_SYSTEM}, or empty
   */
  public Optional<InstalledPackage> operatingSystem() {
    return firstOf(PackageType.OPERATING_SYSTEM);
  }

  /**
   * Finds the radio package.
   *
   * @return the first package of type {@link PackageType#RADIO}, or empty
   */
  public Optional<InstalledPackage> radio() {
    return firstOf(PackageType.RADIO);
  }

  private Optional<InstalledPackage> firstOf(PackageType type) {
    return packages.stream().filter(candidate -> candidate.type() == type).findFirst();
  }

  private static Map<String, Object> versionOf(Optional<InstalledPackage> installed) {
    return installed
        .map(found -> Json.object("name", found.name(), "version", found.version()))
        .orElse(null);
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "device",
        Json.object(
            "name", deviceName,
            "model", modelName,
            "hardwareId", hardwareId,
            "imei", imei,
            "serialNumber", boardSerialNumber,
            "batteryLevel", batteryLevel,
            "batteryHealth", batteryHealth,
            "hasPower", hasPower,
            "freeApplicationSpace", freeApplicationSpace,
            "refurbDate", refurbDate,
            "policyRestrictions", policyRestrictions,
            "os", versionOf(operatingSystem()),
            "radio", versionOf(radio())),
        "perimeters",
        perimeters.stream().map(Perimeter::toJson).toList(),
        "packages",
        packages.stream().map(InstalledPackage::toJson).toList(),
        "removedPackages",
        removedPackages.stream().map(InstalledPackage::toJson).toList());
  }
}
