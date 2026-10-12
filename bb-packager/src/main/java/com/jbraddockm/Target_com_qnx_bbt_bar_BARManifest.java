package com.jbraddockm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.awt.Point;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@TargetClass(className = "com.qnx.bbt.bar.BARManifest")
final class Target_com_qnx_bbt_bar_BARManifest {

  @Substitute
  private static Point getImageSize(File file) {
    try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
      if (in.readLong() != 0x89504E470D0A1A0AL) { // PNG signature
        return new Point(-1, -1);
      }
      in.readInt(); // IHDR chunk length
      if (in.readInt() != 0x49484452) { // "IHDR"
        return new Point(-1, -1);
      }
      int width = in.readInt();
      int height = in.readInt();
      return new Point(width, height);
    } catch (IOException e) {
      return new Point(-1, -1);
    }
  }
}
