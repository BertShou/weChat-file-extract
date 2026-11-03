package extract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class FileExtractTest {

  @Test
  public void testScanArchiveClassifiesFilesByHash() throws IOException {
    Path workDir = Files.createTempDirectory("file-extract-scan-test");
    try {
      Path sourceDir = Files.createDirectories(workDir.resolve("source"));
      Path newSourceDir = Files.createDirectories(workDir.resolve("new-source"));
      Path archiveDir = Files.createDirectories(workDir.resolve("archive"));
      Path report = workDir.resolve("scan.csv");

      Path archivedCopy = Files.createDirectories(archiveDir.resolve("PDF")).resolve("already.pdf");
      Files.writeString(sourceDir.resolve("already.pdf"), "same content", StandardCharsets.UTF_8);
      Files.writeString(archivedCopy, "same content", StandardCharsets.UTF_8);

      Path conflictingCopy = Files.createDirectories(archiveDir.resolve("TEXT")).resolve("conflict.txt");
      Files.writeString(sourceDir.resolve("conflict.txt"), "source content", StandardCharsets.UTF_8);
      Files.writeString(conflictingCopy, "different content", StandardCharsets.UTF_8);
      Files.writeString(newSourceDir.resolve("new.bin"), "new content", StandardCharsets.UTF_8);

      FileExtract.scanArchive(
          List.of(sourceDir.toString(), newSourceDir.toString()),
          archiveDir.toString(),
          report.toString());

      String reportContent = Files.readString(report, StandardCharsets.UTF_8);
      Assert.assertTrue(reportContent.startsWith("\uFEFFstatus,"));
      Assert.assertTrue(reportContent.contains("ALREADY_ARCHIVED"));
      Assert.assertTrue(reportContent.contains("NAME_CONFLICT"));
      Assert.assertTrue(reportContent.contains("NOT_ARCHIVED"));
      Assert.assertTrue(reportContent.contains(newSourceDir.resolve("new.bin").toRealPath().toString()));
      Assert.assertTrue(Files.exists(sourceDir.resolve("already.pdf")));
      Assert.assertTrue(Files.exists(archivedCopy));
    } finally {
      deleteTempDirectory(workDir);
    }
  }

  @Test
  public void testProcessPendingFilesKeepsNewestAndBacksUpOlderVersions() throws IOException {
    Path workDir = Files.createTempDirectory("file-extract-process-test");
    try {
      Path sourceDir = Files.createDirectories(workDir.resolve("source"));
      Path archiveDir = Files.createDirectories(workDir.resolve("archive"));
      Path backupDir = Files.createDirectories(workDir.resolve("backup"));
      Path report = workDir.resolve("process.csv");

      Path sourcePdf = Files.createDirectories(sourceDir.resolve("2024-01")).resolve("same.pdf");
      Path archivePdf = Files.createDirectories(archiveDir.resolve("PDF")).resolve("same.pdf");
      Files.writeString(sourcePdf, "new pdf", StandardCharsets.UTF_8);
      Files.writeString(archivePdf, "old pdf", StandardCharsets.UTF_8);
      Files.setLastModifiedTime(sourcePdf, FileTime.fromMillis(2000));
      Files.setLastModifiedTime(archivePdf, FileTime.fromMillis(1000));
      Assert.assertTrue(sourcePdf.toFile().setReadOnly());
      Assert.assertTrue(archivePdf.toFile().setReadOnly());

      Path sourceDoc = Files.createDirectories(sourceDir.resolve("2024-02")).resolve("same.docx");
      Path archiveDoc = Files.createDirectories(archiveDir.resolve("DOC")).resolve("same.docx");
      Files.writeString(sourceDoc, "old doc", StandardCharsets.UTF_8);
      Files.writeString(archiveDoc, "new doc", StandardCharsets.UTF_8);
      Files.setLastModifiedTime(sourceDoc, FileTime.fromMillis(1000));
      Files.setLastModifiedTime(archiveDoc, FileTime.fromMillis(2000));
      Assert.assertTrue(sourceDoc.toFile().setReadOnly());

      Path oldText = Files.createDirectories(sourceDir.resolve("2024-03")).resolve("report.txt");
      Path newText = Files.createDirectories(sourceDir.resolve("2024-04")).resolve("report(1).txt");
      Path oldArchiveText =
          Files.createDirectories(archiveDir.resolve("TEXT")).resolve("report(2).txt");
      Files.writeString(oldText, "old text", StandardCharsets.UTF_8);
      Files.writeString(newText, "new text", StandardCharsets.UTF_8);
      Files.writeString(oldArchiveText, "older archived text", StandardCharsets.UTF_8);
      Files.setLastModifiedTime(oldText, FileTime.fromMillis(3000));
      Files.setLastModifiedTime(newText, FileTime.fromMillis(4000));
      Files.setLastModifiedTime(oldArchiveText, FileTime.fromMillis(2000));

      Path uniqueFile = Files.createDirectories(sourceDir.resolve("2024-05")).resolve("unique.bin");
      Files.writeString(uniqueFile, "unique", StandardCharsets.UTF_8);

      Path sourceTie = Files.createDirectories(sourceDir.resolve("2024-06")).resolve("tie.pdf");
      Path archiveTie = Files.createDirectories(archiveDir.resolve("PDF")).resolve("tie.pdf");
      Files.writeString(sourceTie, "identical tied content", StandardCharsets.UTF_8);
      Files.writeString(archiveTie, "identical tied content", StandardCharsets.UTF_8);
      Files.setLastModifiedTime(sourceTie, FileTime.fromMillis(5000));
      Files.setLastModifiedTime(archiveTie, FileTime.fromMillis(5000));

      FileExtract.processPendingFiles(
          sourceDir.toString(), archiveDir.toString(), backupDir.toString(), report.toString());

      Assert.assertEquals("new pdf", Files.readString(archivePdf, StandardCharsets.UTF_8));
      Assert.assertEquals("new doc", Files.readString(archiveDoc, StandardCharsets.UTF_8));
      Assert.assertEquals(
          "new text",
          Files.readString(archiveDir.resolve("TEXT").resolve("report(1).txt"), StandardCharsets.UTF_8));
      Assert.assertEquals(
          "unique",
          Files.readString(archiveDir.resolve("OTHER").resolve("unique.bin"), StandardCharsets.UTF_8));
      Assert.assertEquals(
          "identical tied content",
          Files.readString(archiveTie, StandardCharsets.UTF_8));
      Assert.assertEquals("old pdf", Files.readString(backupDir.resolve("same.pdf"), StandardCharsets.UTF_8));
      Assert.assertEquals("old doc", Files.readString(backupDir.resolve("same.docx"), StandardCharsets.UTF_8));
      Assert.assertEquals("old text", Files.readString(backupDir.resolve("report.txt"), StandardCharsets.UTF_8));
      Assert.assertEquals(
          "older archived text",
          Files.readString(backupDir.resolve("report(2).txt"), StandardCharsets.UTF_8));
      Assert.assertEquals(
          "identical tied content",
          Files.readString(backupDir.resolve("tie.pdf"), StandardCharsets.UTF_8));
      Assert.assertFalse(Files.exists(oldArchiveText));
      try (var sourceEntries = Files.list(sourceDir)) {
        Assert.assertEquals(0, sourceEntries.count());
      }
      Assert.assertTrue(Files.readString(report, StandardCharsets.UTF_8).startsWith("\uFEFFstate,"));
    } finally {
      deleteTempDirectory(workDir);
    }
  }

  @Test
  public void testProcessAbortsOnNewestTimestampConflict() throws IOException {
    Path workDir = Files.createTempDirectory("file-extract-process-tie-test");
    try {
      Path sourceDir = Files.createDirectories(workDir.resolve("source"));
      Path archiveDir = Files.createDirectories(workDir.resolve("archive"));
      Path backupDir = Files.createDirectories(workDir.resolve("backup"));
      Path sourceFile = Files.writeString(sourceDir.resolve("conflict.pdf"), "source version");
      Path archiveFile = Files.writeString(
          Files.createDirectories(archiveDir.resolve("PDF")).resolve("conflict.pdf"),
          "archive version");
      Files.setLastModifiedTime(sourceFile, FileTime.fromMillis(5000));
      Files.setLastModifiedTime(archiveFile, FileTime.fromMillis(5000));

      try {
        FileExtract.processPendingFiles(
            sourceDir.toString(),
            archiveDir.toString(),
            backupDir.toString(),
            workDir.resolve("process.csv").toString());
        Assert.fail("Expected a timestamp conflict to stop processing.");
      } catch (IOException expected) {
        Assert.assertTrue(Files.exists(sourceFile));
        Assert.assertTrue(Files.exists(archiveFile));
        try (var backupFiles = Files.list(backupDir)) {
          Assert.assertEquals(0, backupFiles.count());
        }
      }
    } finally {
      deleteTempDirectory(workDir);
    }
  }

  private static void deleteTempDirectory(Path root) throws IOException {
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        if (Files.isRegularFile(path) && !path.toFile().setWritable(true)) {
          throw new IOException("Could not clear the read-only attribute: " + path);
        }
        Files.deleteIfExists(path);
      }
    }
  }
}