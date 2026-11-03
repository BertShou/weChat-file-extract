package extract;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;

/**
 * @author ShouY25713
 */
public class FileExtract {

  enum FileType {
    PDF, IMG, AUDIO, ZIP, GIF, DOC, XLSX, PPT, TEXT, OTHER;
  }

  public static void main(String[] args) throws IOException {
    if (args == null || args.length == 0) {
      throw new IllegalArgumentException(
          "Usage: scan <sourceDir> <archiveDir> [report.csv] | "
              + "process <sourceDir> <archiveDir> <backupDir> [report.csv]");
    }
    if ("scan".equalsIgnoreCase(args[0])) {
      executeScanCommand(args);
    } else if ("process".equalsIgnoreCase(args[0])) {
      executeProcessCommand(args);
    } else {
      throw new IllegalArgumentException(
          "Unknown command: " + args[0] + ". Use scan or process.");
    }
  }

  private static void executeScanCommand(String[] args) throws IOException {
    String defaultReport = Paths.get("target", "wechat-file-scan.csv").toString();
    if (args.length > 1 && args[1].startsWith("--")) {
      List<String> sourcePaths = new ArrayList<>();
      String archivePath = null;
      String reportPath = defaultReport;
      boolean reportSpecified = false;
      for (int i = 1; i < args.length; i++) {
        switch (args[i]) {
          case "--source":
            if (++i >= args.length) {
              throw new IllegalArgumentException("--source requires a directory path.");
            }
            sourcePaths.add(args[i]);
            break;
          case "--archive":
            if (archivePath != null || ++i >= args.length) {
              throw new IllegalArgumentException("--archive must be specified once with a directory path.");
            }
            archivePath = args[i];
            break;
          case "--report":
            if (reportSpecified || ++i >= args.length) {
              throw new IllegalArgumentException("--report must be specified at most once with a file path.");
            }
            reportPath = args[i];
            reportSpecified = true;
            break;
          default:
            throw new IllegalArgumentException("Unknown scan option: " + args[i]);
        }
      }
      if (sourcePaths.isEmpty() || StringUtils.isEmpty(archivePath)) {
        throw new IllegalArgumentException(
            "Usage: scan --source <dir> [--source <dir>...] --archive <dir> [--report <csv>]");
      }
      scanArchive(sourcePaths, archivePath, reportPath);
      return;
    }

    if (args.length < 3 || args.length > 4) {
      throw new IllegalArgumentException(
          "Usage: scan <sourceDir> <archiveDir> [report.csv] or "
              + "scan --source <dir> [--source <dir>...] --archive <dir> [--report <csv>]");
    }
    scanArchive(
        List.of(args[1]),
        args[2],
        args.length == 4 ? args[3] : defaultReport);
  }

  private static void executeProcessCommand(String[] args) throws IOException {
    if (args.length < 4 || args.length > 5) {
      throw new IllegalArgumentException(
          "Usage: process <sourceDir> <archiveDir> <backupDir> [report.csv]");
    }
    String reportPath = args.length == 5
        ? args[4]
        : Paths.get("target", "wechat-file-process.csv").toString();
    processPendingFiles(args[1], args[2], args[3], reportPath);
  }

  public static void scanArchive(String sourcePath, String archivePath, String reportPath)
      throws IOException {
    if (StringUtils.isEmpty(sourcePath)) {
      throw new IllegalArgumentException("sourcePath must not be empty.");
    }
    scanArchive(List.of(sourcePath), archivePath, reportPath);
  }

  public static void scanArchive(List<String> sourcePaths, String archivePath, String reportPath)
      throws IOException {
    if (sourcePaths == null || sourcePaths.isEmpty()
        || sourcePaths.stream().anyMatch(StringUtils::isEmpty)
        || StringUtils.isEmpty(archivePath) || StringUtils.isEmpty(reportPath)) {
      throw new IllegalArgumentException("sourcePaths, archivePath, and reportPath must not be empty.");
    }

    Path archiveDir = Paths.get(archivePath).toRealPath();
    if (!Files.isDirectory(archiveDir)) {
      throw new IllegalArgumentException("archivePath must be a directory.");
    }
    List<Path> sourceDirs = new ArrayList<>();
    for (String sourcePath : sourcePaths) {
      Path sourceDir = Paths.get(sourcePath).toRealPath();
      if (!Files.isDirectory(sourceDir)) {
        throw new IllegalArgumentException("sourcePath must be a directory: " + sourceDir);
      }
      if (pathsOverlap(sourceDir, archiveDir)
          || sourceDirs.stream().anyMatch(existingDir -> pathsOverlap(sourceDir, existingDir))) {
        throw new IllegalArgumentException("Source and archive directories must not overlap: " + sourceDir);
      }
      sourceDirs.add(sourceDir);
    }

    Path report = Paths.get(reportPath).toAbsolutePath().normalize();
    if (report.startsWith(archiveDir)
        || sourceDirs.stream().anyMatch(sourceDir -> report.startsWith(sourceDir))) {
      throw new IllegalArgumentException("The scan report must be outside all source and archive directories.");
    }

    List<Path> archiveFiles = getRegularFiles(archiveDir);
    Map<String, List<ScanEntry>> archiveByHash = new HashMap<>();
    Map<String, List<ScanEntry>> archiveByName = new HashMap<>();
    for (Path file : archiveFiles) {
      ScanEntry entry = scanEntry(file);
      archiveByHash.computeIfAbsent(entry.sha256(), key -> new ArrayList<>()).add(entry);
      archiveByName.computeIfAbsent(entry.dedupeKey(), key -> new ArrayList<>()).add(entry);
    }

    List<SourceFile> sourceFiles = new ArrayList<>();
    for (Path sourceDir : sourceDirs) {
      for (Path file : getRegularFiles(sourceDir)) {
        sourceFiles.add(new SourceFile(sourceDir, file));
      }
    }
    sourceFiles.sort(Comparator.comparing(sourceFile -> sourceFile.path().toString()));
    int alreadyArchived = 0;
    int nameConflicts = 0;
    int notArchived = 0;
    Files.createDirectories(report.getParent());
    Path tempReport = report.resolveSibling(report.getFileName() + ".tmp");
    try (BufferedWriter writer = Files.newBufferedWriter(tempReport, StandardCharsets.UTF_8)) {
      writer.write('\uFEFF');
      writer.write(
          "status,source_root,source_path,archive_hash_match_paths,same_name_archive_paths,size_bytes,last_modified_utc,sha256");
      writer.newLine();
      for (SourceFile sourceFile : sourceFiles) {
        Path file = sourceFile.path();
        ScanEntry entry = scanEntry(file);
        List<ScanEntry> hashMatches = archiveByHash.getOrDefault(entry.sha256(), List.of());
        List<ScanEntry> nameMatches = archiveByName.getOrDefault(entry.dedupeKey(), List.of());
        String status;
        if (!hashMatches.isEmpty()) {
          status = "ALREADY_ARCHIVED";
          alreadyArchived++;
        } else if (!nameMatches.isEmpty()) {
          status = "NAME_CONFLICT";
          nameConflicts++;
        } else {
          status = "NOT_ARCHIVED";
          notArchived++;
        }

        writer.write(String.join(",",
            csvValue(status),
            csvValue(sourceFile.root().toString()),
            csvValue(file.toString()),
            csvValue(hashMatches.stream().map(match -> match.path().toString())
                .collect(Collectors.joining(" | "))),
            csvValue(nameMatches.stream().map(match -> match.path().toString())
                .collect(Collectors.joining(" | "))),
            csvValue(Long.toString(entry.size())),
            csvValue(Instant.ofEpochMilli(entry.lastModifiedMillis()).toString()),
            csvValue(entry.sha256())));
        writer.newLine();
      }
    }
    try {
      Files.move(tempReport, report, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(tempReport, report, StandardCopyOption.REPLACE_EXISTING);
    }

    System.out.println("Source roots: " + sourceDirs.size());
    for (Path sourceDir : sourceDirs) {
      long rootFileCount = sourceFiles.stream().filter(file -> file.root().equals(sourceDir)).count();
      System.out.println("Source files under " + sourceDir + ": " + rootFileCount);
    }
    System.out.println("Source files: " + sourceFiles.size());
    System.out.println("Archive files: " + archiveFiles.size());
    System.out.println("Already archived (SHA-256 match): " + alreadyArchived);
    System.out.println("Same-name conflicts (different content): " + nameConflicts);
    System.out.println("Not found in archive: " + notArchived);
    System.out.println("Report: " + report);
  }

  public static void processPendingFiles(
      String sourcePath, String archivePath, String backupPath, String reportPath) throws IOException {
    if (StringUtils.isEmpty(sourcePath) || StringUtils.isEmpty(archivePath)
        || StringUtils.isEmpty(backupPath) || StringUtils.isEmpty(reportPath)) {
      throw new IllegalArgumentException("sourcePath, archivePath, backupPath, and reportPath must not be empty.");
    }

    Path sourceRoot = Paths.get(sourcePath).toRealPath();
    Path archiveRoot = Paths.get(archivePath).toRealPath();
    Path backupCandidate = Paths.get(backupPath).toAbsolutePath().normalize();
    Path report = Paths.get(reportPath).toAbsolutePath().normalize();
    if (!Files.isDirectory(sourceRoot) || !Files.isDirectory(archiveRoot)) {
      throw new IllegalArgumentException("sourcePath and archivePath must be directories.");
    }
    if (pathsOverlap(sourceRoot, archiveRoot) || pathsOverlap(sourceRoot, backupCandidate)
        || pathsOverlap(archiveRoot, backupCandidate)) {
      throw new IllegalArgumentException("Source, archive, and backup directories must not overlap.");
    }
    Files.createDirectories(backupCandidate);
    Path backupRoot = backupCandidate.toRealPath();
    if (pathsOverlap(sourceRoot, backupRoot) || pathsOverlap(archiveRoot, backupRoot)
        || report.startsWith(sourceRoot) || report.startsWith(archiveRoot)
        || report.startsWith(backupRoot)) {
      throw new IllegalArgumentException("The report and backup paths must be outside source and archive directories.");
    }

    List<ProcessGroup> groups = planPendingFiles(sourceRoot, archiveRoot);
    if (groups.isEmpty()) {
      System.out.println("No source files to process.");
      return;
    }

    Map<ProcessFile, Path> backups = new HashMap<>();
    for (ProcessGroup group : groups) {
      for (ProcessFile candidate : group.candidates()) {
        if (!candidate.equals(group.winner())) {
          backups.put(candidate, copyToBackup(candidate, backupRoot));
        }
      }
    }
    for (ProcessGroup group : groups) {
      if (!group.winner().inArchive()) {
        copyFileVerified(group.winner().path(), group.archivePath(), group.winner().sha256());
      }
    }
    verifyProcessGroups(groups, backups);

    List<ProcessOperation> operations = processOperations(groups, backups);
    writeProcessReport(report, operations, "VERIFIED_READY_TO_DELETE");

    List<ProcessFile> toDelete = new ArrayList<>();
    for (ProcessGroup group : groups) {
      for (ProcessFile candidate : group.candidates()) {
        if (!candidate.inArchive() || !candidate.path().equals(group.archivePath())) {
          makeWritable(candidate.path());
          toDelete.add(candidate);
        }
      }
    }

    int sourceDeleted = 0;
    int archiveDuplicatesDeleted = 0;
    int emptyDirectoriesRemoved = 0;
    List<String> deleteFailures = new ArrayList<>();
    List<ProcessFile> deletedFiles = new ArrayList<>();
    for (ProcessFile candidate : toDelete) {
      try {
        Files.delete(candidate.path());
        deletedFiles.add(candidate);
        if (candidate.inArchive()) {
          archiveDuplicatesDeleted++;
        } else {
          sourceDeleted++;
        }
      } catch (IOException e) {
        deleteFailures.add(candidate.path() + ": " + e.getMessage());
      }
    }
    for (ProcessFile candidate : deletedFiles) {
      Path root = candidate.inArchive() ? archiveRoot : sourceRoot;
      emptyDirectoriesRemoved += pruneEmptyParents(candidate.path(), root);
    }

    String result = deleteFailures.isEmpty() ? "COMPLETE" : "PARTIAL";
    writeProcessReport(report, operations, result);
    long sourceWinners = groups.stream().filter(group -> !group.winner().inArchive()).count();
    long archiveWinners = groups.size() - sourceWinners;
    System.out.println("Source files processed: "
        + groups.stream().flatMap(group -> group.candidates().stream())
        .filter(candidate -> !candidate.inArchive()).count());
    System.out.println("Name groups: " + groups.size());
    System.out.println("Latest source versions archived: " + sourceWinners);
    System.out.println("Latest archive versions retained: " + archiveWinners);
    System.out.println("Older versions backed up: " + backups.size());
    System.out.println("Source files deleted: " + sourceDeleted);
    System.out.println("Older archive copies deleted: " + archiveDuplicatesDeleted);
    System.out.println("Empty parent directories removed: " + emptyDirectoriesRemoved);
    System.out.println("Report: " + report);
    if (!deleteFailures.isEmpty()) {
      deleteFailures.stream().limit(20).forEach(System.err::println);
      throw new IOException("Processing completed with " + deleteFailures.size() + " undeleted files.");
    }
  }

  private static List<ProcessGroup> planPendingFiles(Path sourceRoot, Path archiveRoot)
      throws IOException {
    Map<String, List<ProcessFile>> filesByName = new HashMap<>();
    HashSet<String> sourceNames = new HashSet<>();
    for (Path path : getRegularFiles(sourceRoot)) {
      ProcessFile source = processFile(path, false);
      sourceNames.add(source.dedupeKey());
      filesByName.computeIfAbsent(source.dedupeKey(), key -> new ArrayList<>()).add(source);
    }
    if (sourceNames.isEmpty()) {
      return List.of();
    }

    for (Path path : getRegularFiles(archiveRoot)) {
      String dedupeKey = computeDedupeKey(path.getFileName().toString());
      if (sourceNames.contains(dedupeKey)) {
        filesByName.get(dedupeKey).add(processFile(path, true));
      }
    }

    List<String> sortedNames = new ArrayList<>(sourceNames);
    sortedNames.sort(String::compareTo);
    List<ProcessGroup> groups = new ArrayList<>();
    for (String dedupeKey : sortedNames) {
      List<ProcessFile> candidates = filesByName.get(dedupeKey);
      long newestTime = candidates.stream()
          .mapToLong(ProcessFile::lastModifiedMillis)
          .max()
          .orElseThrow();
      List<ProcessFile> newestFiles = candidates.stream()
          .filter(candidate -> candidate.lastModifiedMillis() == newestTime)
          .toList();
      HashSet<String> newestHashes = new HashSet<>();
      newestFiles.forEach(candidate -> newestHashes.add(candidate.sha256()));
      if (newestHashes.size() > 1) {
        throw new IOException(
            "Different contents have the same newest timestamp for duplicate name: " + dedupeKey);
      }

      Comparator<ProcessFile> pathOrder = Comparator.comparing(candidate -> candidate.path().toString());
      ProcessFile winner = newestFiles.stream()
          .filter(ProcessFile::inArchive)
          .min(pathOrder)
          .orElseGet(() -> newestFiles.stream().min(pathOrder).orElseThrow());
      Path archiveWinner = winner.inArchive()
          ? winner.path()
          : archiveRoot.resolve(fileTypeFor(winner.path().toFile()).name()).resolve(winner.path().getFileName());
      groups.add(new ProcessGroup(dedupeKey, List.copyOf(candidates), winner, archiveWinner));
    }
    return groups;
  }

  private static ProcessFile processFile(Path path, boolean inArchive) throws IOException {
    ScanEntry entry = scanEntry(path);
    return new ProcessFile(
        entry.path(),
        inArchive,
        entry.dedupeKey(),
        entry.sha256(),
        entry.size(),
        entry.lastModifiedMillis());
  }

  private static Path copyToBackup(ProcessFile file, Path backupRoot) throws IOException {
    Path existing = backupRoot.resolve(file.path().getFileName());
    if (Files.isRegularFile(existing, LinkOption.NOFOLLOW_LINKS)
        && file.sha256().equals(sha256(existing))) {
      return existing;
    }
    Path target = resolveNonClobberTarget(backupRoot.toFile(), file.path().getFileName().toString()).toPath();
    copyFileVerified(file.path(), target, file.sha256());
    return target;
  }

  private static void copyFileVerified(Path source, Path target, String expectedHash) throws IOException {
    if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
        && expectedHash.equals(sha256(target))) {
      return;
    }
    Files.createDirectories(target.getParent());
    Path temp = Files.createTempFile(target.getParent(), "file-extract-", ".tmp");
    try {
      Files.copy(
          source,
          temp,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES);
      verifyFileHash(temp, expectedHash);
      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
        makeWritable(target);
      }
      try {
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private static void verifyProcessGroups(
      List<ProcessGroup> groups, Map<ProcessFile, Path> backups) throws IOException {
    for (ProcessGroup group : groups) {
      verifyFileHash(group.archivePath(), group.winner().sha256());
      for (ProcessFile candidate : group.candidates()) {
        if (!candidate.equals(group.winner())) {
          Path backup = backups.get(candidate);
          if (backup == null) {
            throw new IOException("Missing backup for older version: " + candidate.path());
          }
          verifyFileHash(backup, candidate.sha256());
        }
      }
    }
  }

  private static void verifyFileHash(Path path, String expectedHash) throws IOException {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
        || !expectedHash.equals(sha256(path))) {
      throw new IOException("Hash verification failed: " + path);
    }
  }

  private static void makeWritable(Path path) throws IOException {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !path.toFile().setWritable(true)) {
      throw new IOException("Could not clear the read-only attribute: " + path);
    }
  }

  private static int pruneEmptyParents(Path path, Path root) throws IOException {
    int removed = 0;
    Path directory = path.getParent();
    while (directory != null && !directory.equals(root) && directory.startsWith(root)) {
      if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        break;
      }
      try (Stream<Path> entries = Files.list(directory)) {
        if (entries.findAny().isPresent()) {
          break;
        }
      }
      Files.delete(directory);
      removed++;
      directory = directory.getParent();
    }
    return removed;
  }

  private static List<ProcessOperation> processOperations(
      List<ProcessGroup> groups, Map<ProcessFile, Path> backups) {
    List<ProcessOperation> operations = new ArrayList<>();
    for (ProcessGroup group : groups) {
      for (ProcessFile candidate : group.candidates()) {
        String action = candidate.equals(group.winner())
            ? "KEEP_LATEST"
            : candidate.inArchive() ? "BACKUP_OLDER_ARCHIVE" : "BACKUP_OLDER_SOURCE";
        operations.add(new ProcessOperation(
            group.dedupeKey(),
            candidate,
            action,
            group.archivePath(),
            backups.get(candidate)));
      }
    }
    return operations;
  }

  private static void writeProcessReport(
      Path report, List<ProcessOperation> operations, String state) throws IOException {
    Files.createDirectories(report.getParent());
    Path temp = report.resolveSibling(report.getFileName() + ".tmp");
    try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
      writer.write('\uFEFF');
      writer.write(
          "state,dedupe_key,candidate_kind,action,original_path,kept_archive_path,backup_path,size_bytes,last_modified_utc,sha256");
      writer.newLine();
      for (ProcessOperation operation : operations) {
        ProcessFile file = operation.file();
        writer.write(String.join(",",
            csvValue(state),
            csvValue(operation.dedupeKey()),
            csvValue(file.inArchive() ? "ARCHIVE" : "SOURCE"),
            csvValue(operation.action()),
            csvValue(file.path().toString()),
            csvValue(operation.archivePath().toString()),
            csvValue(operation.backupPath() == null ? "" : operation.backupPath().toString()),
            csvValue(Long.toString(file.size())),
            csvValue(Instant.ofEpochMilli(file.lastModifiedMillis()).toString()),
            csvValue(file.sha256())));
        writer.newLine();
      }
    }
    try {
      Files.move(temp, report, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temp, report, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static boolean pathsOverlap(Path first, Path second) {
    return first.equals(second) || first.startsWith(second) || second.startsWith(first);
  }

  private static List<Path> getRegularFiles(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      return paths
          .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
          .sorted(Comparator.comparing(Path::toString))
          .collect(Collectors.toList());
    }
  }

  private static ScanEntry scanEntry(Path path) throws IOException {
    BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    String hash = sha256(path);
    BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (before.size() != after.size()
        || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
      throw new IOException("File changed while scanning: " + path);
    }
    return new ScanEntry(
        path,
        computeDedupeKey(path.getFileName().toString()),
        hash,
        after.size(),
        after.lastModifiedTime().toMillis());
  }

  private static String sha256(Path path) throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable.", e);
    }

    try (InputStream input = Files.newInputStream(path)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static String csvValue(String value) {
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  private record ScanEntry(
      Path path, String dedupeKey, String sha256, long size, long lastModifiedMillis) {
  }

  private record SourceFile(Path root, Path path) {
  }

  private record ProcessFile(
      Path path, boolean inArchive, String dedupeKey, String sha256, long size, long lastModifiedMillis) {
  }

  private record ProcessGroup(
      String dedupeKey, List<ProcessFile> candidates, ProcessFile winner, Path archivePath) {
  }

  private record ProcessOperation(
      String dedupeKey, ProcessFile file, String action, Path archivePath, Path backupPath) {
  }

  private static FileType fileTypeFor(File file) {
    String[] fileNameArrays = file.getName().split("\\.");
    String extension = fileNameArrays[fileNameArrays.length - 1];
    switch (extension.toLowerCase(Locale.ROOT)) {
      case "pdf":
        return FileType.PDF;
      case "png":
      case "jpg":
        return FileType.IMG;
      case "mp3":
      case "mp4":
      case "wav":
      case "m4a":
      case "wma":
        return FileType.AUDIO;
      case "zip":
      case "7z":
      case "rar":
        return FileType.ZIP;
      case "gif":
        return FileType.GIF;
      case "doc":
      case "docx":
        return FileType.DOC;
      case "xls":
      case "xlsx":
        return FileType.XLSX;
      case "ppt":
      case "pptx":
        return FileType.PPT;
      case "txt":
      case "md":
        return FileType.TEXT;
      default:
        return FileType.OTHER;
    }
  }

  /**
   * 遍历指定目录，找到“仅由小写字母、数字与下划线组成的文件名（不含扩展名）”的文件，
   * 将其移动到 unknown 目录（若不存在则创建）。
   * - 自动跳过 unknown 目录自身（避免自我移动）
   * - 若目标已存在同名文件，则自动追加 (n) 后缀避免覆盖
   */
  private static File resolveNonClobberTarget(File targetDir, String originalName) {
    File target = new File(targetDir, originalName);
    if (!target.exists()) {
      return target;
    }
    int idx = originalName.lastIndexOf('.');
    String base = idx >= 0 ? originalName.substring(0, idx) : originalName;
    String ext = idx >= 0 ? originalName.substring(idx) : "";
    int counter = 1;
    while (true) {
      File candidate = new File(targetDir, base + "(" + counter + ")" + ext);
      if (!candidate.exists()) {
        return candidate;
      }
      counter++;
    }
  }

  /**
   * 计算去重键：移除文件名末尾的中/英文括号数字后缀，扩展名与整体转为小写。
   * 示例：
   *  - "name(1).pdf" => "name.pdf"
   *  - "《剧本🚃》（备注）(2).PDF" => "《剧本🚃》（备注）.pdf"
   */
  private static String computeDedupeKey(String fileName) {
    int idx = fileName.lastIndexOf('.');
    String ext = idx >= 0 ? fileName.substring(idx).toLowerCase(Locale.ROOT) : "";
    String base = idx >= 0 ? fileName.substring(0, idx) : fileName;
    String normalizedBase = TRAILING_DUP_NUM.matcher(base).replaceFirst("").trim();
    return (normalizedBase + ext).toLowerCase(Locale.ROOT);
  }

  // 匹配文件名末尾的重复编号：(1)/(2) 或 （1）/（2）
  private static final Pattern TRAILING_DUP_NUM = Pattern.compile("\\s*[\\(（](\\d+)[\\)）]$");

}
