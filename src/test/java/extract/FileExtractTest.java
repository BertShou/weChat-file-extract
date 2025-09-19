package extract;

import org.junit.Test;

public class FileExtractTest {

  static String srcPath = "C:\\WeChat Files\\xjdesh\\FileStorage\\File";
  static String srcPath_2025_04 = "C:\\WeChat Files\\xjdesh\\FileStorage\\File\\2025-04";
  static String srcPath_wx = "C:\\WeChat Files\\xjdesh\\FileStorage\\File";
  static String dstPath = "C:\\other_info\\wechat_archived_file";
  static String deleteFildBackupPath = "C:\\other_info\\deleteFildBackup";
  static String unknownFildPath = "C:\\other_info\\wechat_archived_file\\UNKNOWN";

  @Test
  public void testFileExtract() {
    String[] args = { srcPath, dstPath };
    FileExtract.fileExtract(args);
  }

  @Test
  public void testCleanDuplicatedFiles() {
    FileExtract.cleanDuplicatedFiles(dstPath, deleteFildBackupPath);
    //    FileExtract.cleanDuplicatedFiles(srcPath_wx, deleteFildBackupPath);
  }

  @Test
  public void testMoveUnknownNamedFiles() {
    FileExtract.moveUnknownNamedFiles(dstPath, unknownFildPath);
  }
}