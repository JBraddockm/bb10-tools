package com.jbraddockm.link.endpoint.pairing.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The folder of a pairing: the authority that this computer made, its private key, the authority
 * that the device answered with, and a file that says which pairing it is. It is the folder of the
 * device, {@code <devices>/<PIN>/}, where the files of the certificate are too.
 *
 * <p>A pairing is written in two steps: the key first, under a temporary name, and the rest, with
 * the key moved into place, once the device has taken the authority. An authority that the device
 * never took is never kept, and a key is never without its authority.
 */
public final class PairingDirectory {

  /** The name of the file that holds the authority of this computer. */
  public static final String AUTHORITY_FILE = "authority_cert.pem";

  /** The name of the file that holds the private key of that authority: a credential. */
  public static final String KEY_FILE = "authority_key.pem";

  /** The name of the file that holds the authority that the device answered with. */
  public static final String DEVICE_AUTHORITY_FILE = "device_authority.pem";

  /** The name of the file that records the identifier and the names. */
  public static final String METADATA_FILE = "pairing.json";

  private final Path directory;

  private PairingDirectory(Path directory) {
    this.directory = directory;
  }

  /**
   * The folder of the pairing with a device.
   *
   * @param devices the folder that holds one folder for each device
   * @param pin the PIN of the device, in letters and digits
   * @return the folder, which may not exist yet
   * @throws IllegalArgumentException if the PIN is not letters and digits
   */
  public static PairingDirectory of(Path devices, String pin) {
    if (!pin.matches("[A-Za-z0-9]{1,16}")) {
      throw new IllegalArgumentException("not a PIN: " + pin);
    }
    return new PairingDirectory(devices.resolve(pin));
  }

  /**
   * The folder.
   *
   * @return its path
   */
  public Path directory() {
    return directory;
  }

  /**
   * The file of this computer's authority.
   *
   * @return its path
   */
  public Path authorityFile() {
    return directory.resolve(AUTHORITY_FILE);
  }

  /**
   * The file of the private key.
   *
   * @return its path
   */
  public Path keyFile() {
    return directory.resolve(KEY_FILE);
  }

  /**
   * The file of the device's authority.
   *
   * @return its path
   */
  public Path deviceAuthorityFile() {
    return directory.resolve(DEVICE_AUTHORITY_FILE);
  }

  /**
   * The file that says which pairing it is.
   *
   * @return its path
   */
  public Path metadataFile() {
    return directory.resolve(METADATA_FILE);
  }

  /**
   * Writes a per-service identity into the device folder: {@code <name>_cert.pem} (the certificate,
   * or a chain) and {@code <name>_key.pem} (its private key, owner-only), both moved into place
   * atomically. Used for the tunnel and Blend identities made at pairing.
   *
   * @param name the file base name, e.g. {@code tunnel} or {@code blend}
   * @param certificatePem the certificate (or chain), as PEM
   * @param key the private key
   * @throws IOException if a file cannot be written or moved
   */
  public void writeServiceIdentity(String name, String certificatePem, PrivateKey key)
      throws IOException {
    var certificateFile = directory.resolve(name + "_cert.pem");
    var keyFile = directory.resolve(name + "_key.pem");
    var certificateTemporary = SecureFiles.withSuffix(certificateFile);
    SecureFiles.writePrivateKey(SecureFiles.withSuffix(keyFile), key);
    Files.writeString(certificateTemporary, certificatePem, StandardCharsets.UTF_8);
    SecureFiles.move(SecureFiles.withSuffix(keyFile), keyFile);
    SecureFiles.move(certificateTemporary, certificateFile);
  }

  /**
   * The file of a service identity's certificate, e.g. {@code tunnel_cert.pem}.
   *
   * @param name the base name
   * @return its path
   */
  public Path serviceCertificateFile(String name) {
    return directory.resolve(name + "_cert.pem");
  }

  /**
   * The files of a pairing that are there.
   *
   * @return the paths, in a fixed order
   */
  public List<Path> existingFiles() {
    return Stream.of(authorityFile(), keyFile(), deviceAuthorityFile(), metadataFile())
        .filter(Files::exists)
        .toList();
  }

  /**
   * Reads what the pairing says of itself.
   *
   * @return the pairing, or nothing if there is no file
   * @throws IOException if the file cannot be read
   * @throws IllegalArgumentException if it is not a pairing file
   */
  public Optional<StoredPairing> metadata() throws IOException {
    if (!Files.exists(metadataFile())) {
      return Optional.empty();
    }
    return Optional.of(
        StoredPairing.fromJsonText(Files.readString(metadataFile(), StandardCharsets.UTF_8)));
  }

  /**
   * Starts a pairing: makes the folder, for its owner only, and writes the private key under a
   * temporary name, with permissions for its owner only from the first moment.
   *
   * @param keys the key pair of the authority
   * @param replace whether a pairing that is there may be replaced
   * @return what to commit, or to close without committing
   * @throws FilesExistException if files are there and {@code replace} is not set
   * @throws IOException if the key cannot be written
   */
  public Pending prepare(KeyPair keys, boolean replace) throws IOException {
    var existing = existingFiles();
    if (!existing.isEmpty() && !replace) {
      throw new FilesExistException("a pairing with this device", directory, existing);
    }
    SecureFiles.createPrivateDirectory(directory);
    SecureFiles.writePrivateKey(SecureFiles.withSuffix(keyFile()), keys.getPrivate());
    return new Pending();
  }

  /** A pairing that waits for the device, and is taken back if it is not committed. */
  public final class Pending implements AutoCloseable {

    private boolean committed;

    private Pending() {}

    /**
     * Writes the authorities and the file of the pairing, then moves all four files into place, the
     * key first.
     *
     * @param authorityPem this computer's authority, as PEM
     * @param deviceAuthorityPem the device's authority, as PEM
     * @param stored what the pairing says of itself
     * @throws IOException if a file cannot be written or moved
     */
    public void commit(String authorityPem, String deviceAuthorityPem, StoredPairing stored)
        throws IOException {
      var authorityTemporary = SecureFiles.withSuffix(authorityFile());
      var deviceTemporary = SecureFiles.withSuffix(deviceAuthorityFile());
      var metadataTemporary = SecureFiles.withSuffix(metadataFile());
      Files.writeString(authorityTemporary, authorityPem, StandardCharsets.UTF_8);
      Files.writeString(deviceTemporary, deviceAuthorityPem, StandardCharsets.UTF_8);
      Files.writeString(metadataTemporary, stored.toJsonText(), StandardCharsets.UTF_8);
      SecureFiles.move(SecureFiles.withSuffix(keyFile()), keyFile());
      SecureFiles.move(authorityTemporary, authorityFile());
      SecureFiles.move(deviceTemporary, deviceAuthorityFile());
      SecureFiles.move(metadataTemporary, metadataFile());
      committed = true;
    }

    /** Takes back the temporary files, unless the pairing was committed. */
    @Override
    public void close() {
      if (committed) {
        return;
      }
      SecureFiles.deleteTemporaries(
          List.of(keyFile(), authorityFile(), deviceAuthorityFile(), metadataFile()));
    }
  }
}
