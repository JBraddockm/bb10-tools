package com.jbraddockm.link.endpoint.pairing.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The folder of one device's identity: {@code <devices>/<PIN>/}, with the certificate, the key and
 * the identity file.
 *
 * <p>An identity is written in two steps, so that a failed enrolment never destroys one that works:
 * {@link #prepare} puts the key next to its place under another name, and {@link Pending#commit}
 * writes the certificate and then moves everything into place. A {@code Pending} that is closed
 * without a commit takes back what it wrote.
 */
public final class IdentityDirectory {

  /** The name of the certificate file; the name that the scripts look for. */
  public static final String CERTIFICATE_FILE = "client_cert.pem";

  /** The name of the key file. */
  public static final String KEY_FILE = "client_key.pem";

  /** The name of the file that records the identifier and the name. */
  public static final String METADATA_FILE = "identity.json";

  private final Path directory;

  private IdentityDirectory(Path directory) {
    this.directory = directory;
  }

  /**
   * The folder of a device.
   *
   * @param devices the folder that holds one folder for each device
   * @param pin the PIN of the device, the name of the folder
   * @return the directory; nothing is created yet
   * @throws IllegalArgumentException if the PIN is not a safe folder name
   */
  public static IdentityDirectory of(Path devices, String pin) {
    if (!pin.matches("[A-Za-z0-9]{1,16}")) {
      throw new IllegalArgumentException("not a PIN: " + pin);
    }
    return new IdentityDirectory(devices.resolve(pin));
  }

  /**
   * The folder itself.
   *
   * @return its path
   */
  public Path directory() {
    return directory;
  }

  /**
   * The certificate file.
   *
   * @return its path
   */
  public Path certificateFile() {
    return directory.resolve(CERTIFICATE_FILE);
  }

  /**
   * The key file.
   *
   * @return its path
   */
  public Path keyFile() {
    return directory.resolve(KEY_FILE);
  }

  /**
   * The identity file.
   *
   * @return its path
   */
  public Path metadataFile() {
    return directory.resolve(METADATA_FILE);
  }

  /**
   * The files of an identity that are there.
   *
   * @return the paths, in the order certificate, key, identity file
   */
  public List<Path> existingFiles() {
    return Stream.of(certificateFile(), keyFile(), metadataFile()).filter(Files::exists).toList();
  }

  /**
   * Reads the identity file.
   *
   * @return what it says, or empty if there is no file
   * @throws IOException if it cannot be read
   * @throws IllegalArgumentException if it is not as {@link StoredIdentity#toJsonText()} writes it
   */
  public Optional<StoredIdentity> metadata() throws IOException {
    if (!Files.exists(metadataFile())) {
      return Optional.empty();
    }
    return Optional.of(
        StoredIdentity.fromJsonText(Files.readString(metadataFile(), StandardCharsets.UTF_8)));
  }

  /**
   * Starts writing an identity: the folder is made and the key is written under a temporary name.
   *
   * @param keys the key pair; only the private key is written
   * @param identity what the identity file will say
   * @param replace whether an identity that is there may be replaced
   * @return the pending identity; close it, and commit it before that if all went well
   * @throws FilesExistException if files are there and {@code replace} is not set
   * @throws IOException if a file or the folder cannot be written
   */
  public Pending prepare(KeyPair keys, StoredIdentity identity, boolean replace)
      throws IOException {
    var existing = existingFiles();
    if (!existing.isEmpty() && !replace) {
      throw new FilesExistException("an identity for this device", directory, existing);
    }
    SecureFiles.createPrivateDirectory(directory);
    SecureFiles.writePrivateKey(SecureFiles.withSuffix(keyFile()), keys.getPrivate());
    return new Pending(identity);
  }

  /** An identity that has its key on disk and waits for its certificate. */
  public final class Pending implements AutoCloseable {

    private final StoredIdentity identity;
    private boolean committed;

    private Pending(StoredIdentity identity) {
      this.identity = identity;
    }

    /**
     * Writes the certificate and the identity file, then moves all three files into place. The key
     * goes first, so that a certificate is never without its key.
     *
     * @param certificatePem the certificate, as PEM
     * @throws IOException if a file cannot be written or moved
     */
    public void commit(String certificatePem) throws IOException {
      commit(certificatePem, identity);
    }

    /**
     * Writes the certificate and an identity file that says something else than the one that the
     * identity was prepared with.
     *
     * @param certificatePem the certificate, as PEM
     * @param written what the identity file will say
     * @throws IOException if a file cannot be written or moved
     */
    public void commit(String certificatePem, StoredIdentity written) throws IOException {
      var certificateTemporary = SecureFiles.withSuffix(certificateFile());
      var metadataTemporary = SecureFiles.withSuffix(metadataFile());
      Files.writeString(certificateTemporary, certificatePem, StandardCharsets.UTF_8);
      Files.writeString(metadataTemporary, written.toJsonText(), StandardCharsets.UTF_8);
      SecureFiles.move(SecureFiles.withSuffix(keyFile()), keyFile());
      SecureFiles.move(certificateTemporary, certificateFile());
      SecureFiles.move(metadataTemporary, metadataFile());
      committed = true;
    }

    /** Takes back the temporary files, unless the identity was committed. */
    @Override
    public void close() {
      if (committed) {
        return;
      }
      SecureFiles.deleteTemporaries(List.of(keyFile(), certificateFile(), metadataFile()));
    }
  }
}
