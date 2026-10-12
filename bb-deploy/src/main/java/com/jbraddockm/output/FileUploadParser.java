package com.jbraddockm.output;

import java.util.List;

/**
 * Parses {@code -putFile}. Observed on success, with exit status 0:
 *
 * <pre>
 * Info: Sending request: PUT_FILE
 * Info: Action: Put File
 * Info: File size: 20
 * Info: Package com.example.Term49.gYABgFEqMSpja5S6kL.DWpWmq50
 * Info: Asset Path data/hello.txt
 * result::success
 * </pre>
 *
 * <p>Uploading again to the same path replaces the file with the same output. Failures are raised
 * by the tool itself: {@code Error: Cannot write to file data/: Is a directory} (exit status 2),
 * and a local file that cannot be read fails before anything is sent.
 */
final class FileUploadParser implements OutputParser<FileUpload> {

  @Override
  public FileUpload parse(List<String> lines) {
    return new FileUpload(
        OutputText.lastValueAfter(lines, "Info: Package ").orElse(null),
        OutputText.lastValueAfter(lines, "Info: Asset Path ").orElse(null),
        OutputText.lastValueAfter(lines, "Info: File size: ")
            .flatMap(OutputText::parseLong)
            .orElse(null),
        OutputText.lastResult(lines).orElse(null));
  }
}
