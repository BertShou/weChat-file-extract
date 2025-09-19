package extract;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

/**
 * @author ShouY25713
 */
public class FileExtract {

  enum FileType {
    PDF, IMG, AUDIO, ZIP, GIF, DOC, XLSX, PPT, TEXT, SPECIAL, OTHER;
  }

  public static void main(String[] args) {
    fileExtract(args);
  }

  public static void fileExtract(String[] args) {

    System.out.println("------------------------------Start to do file extract------------------------------");

    if (args == null || args.length == 0 || args[0] == null || args[0].isEmpty()) {
      System.out.println("Please input srcPath!");
      return;
    }
    String srcPath = args[0];
    System.out.println("srcPath is :" + srcPath);
    File srcFolders = new File(srcPath);
    if (!srcFolders.exists()) {
      System.out.println("srcPath is not exists!");
      return;
    }

    HashMap<FileType, HashSet<File>> copyFiles = iniFilesMap();
    getFileArray(srcFolders, copyFiles);
    System.out.println("srcFolders PDF files count:" + copyFiles.get(FileType.PDF).size());

    // get exist files
    if (args.length < 2 || args[1] == null || args[1].isEmpty()) {
      System.out.println("Please input dstPath!");
      return;
    }
    String dstPath = args[1];
    System.out.println("dstPath is :" + dstPath);
    File dstFolders = new File(dstPath);
    if (!dstFolders.exists()) {
      System.out.println("dstPath is not exists!");
      return;
    }
    HashMap<FileType, HashSet<File>> dstFiles = iniFilesMap();
    getFileArray(dstFolders, dstFiles);
    System.out.println("dstFiles PDF files count:" + dstFiles.get(FileType.PDF).size());

    // distinct
    distinct(copyFiles, dstFiles);

    // copy
    System.out.println("------------------------------start to copy------------------------------");
    System.out.println("------------------------------copyFiles:" + copyFiles + "------------------------------");
    copyFiles(copyFiles, dstPath);
    System.out.println("------------------------------copy finish!------------------------------");
    System.out.println("------------------------------File extract finish------------------------------");
  }

  private static HashMap<FileType, HashSet<File>> iniFilesMap() {
    HashMap<FileType, HashSet<File>> copyFiles = new HashMap<>(3);
    for (FileType value : FileType.values()) {
      copyFiles.put(value, new HashSet<>());
    }
    return copyFiles;
  }

  private static void copyFiles(HashMap<FileType, HashSet<File>> copyFiles, String dstPath) {
    copyFiles.keySet().forEach(key -> {
      String filePath = dstPath + File.separator + key.name();
      File dstFilePath = new File(filePath);
      if (!dstFilePath.exists()) {
        dstFilePath.mkdirs();
      }
      copyFiles.get(key).forEach(file -> {
        try {
          File newFile = new File(dstFilePath + File.separator + file.getName());
          if (!newFile.exists()) {
            FileUtils.copyFile(file, newFile);
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      });
    });

  }

  /**
   * 过滤copyFiles中，所有已经在dstFiles中存在的文件。
   *
   * @param copyFiles 要复制的文件
   * @param dstFiles 目标文件夹里的文件
   */
  private static void distinct(HashMap<FileType, HashSet<File>> copyFiles, HashMap<FileType, HashSet<File>> dstFiles) {
    for (FileType fileType : FileType.values()) {
      copyFiles.put(fileType, new HashSet<>(
          copyFiles.get(fileType)
              .stream()
              .filter(copyFile -> isFileExistInDstFiles(copyFile, dstFiles, fileType))
              .collect(Collectors.toSet())));
    }
  }

  private static boolean isFileExistInDstFiles(File copyFile, HashMap<FileType, HashSet<File>> dstFiles, FileType pdf) {
    String srcKey = computeDedupeKey(copyFile.getName());
    return dstFiles.get(pdf).stream()
        .map(f -> computeDedupeKey(f.getName()))
        .noneMatch(dstKey -> dstKey.equals(srcKey));
  }

  /**
   * 获取目标文件下所有文件，存放在filesSet中。
   *
   * @param file 支持文件，文件夹
   * @param filesSet 所有文件
   */
  private static void getFileArray(File file, HashMap<FileType, HashSet<File>> filesSet) {
    if (file.exists()) {
      if (file.isFile()) {
        handleFile(file, filesSet);
      } else {
        File[] files = file.listFiles();
        if (null != files && files.length > 0) {
          if (Arrays.stream(files).anyMatch(File::isDirectory)) {
            for (File tempFile : files) {
              getFileArray(tempFile, filesSet);
            }
          } else {
            for (File tempFile : files) {
              handleFile(tempFile, filesSet);
            }
          }
        }
      }
    }
  }

  /**
   * 根据文件后缀处理文件
   */
  private static void handleFile(File file, HashMap<FileType, HashSet<File>> filesSet) {
    String[] fileNameArrays = file.getName().split("\\.");
    String extension = fileNameArrays[fileNameArrays.length - 1];
    switch (extension.toLowerCase()) {
      case "pdf":
        filesSet.get(FileType.PDF).add(file);
        break;
      case "png":
      case "jpg":
        filesSet.get(FileType.IMG).add(file);
        break;
      case "mp3":
      case "mp4":
      case "wav":
      case "m4a":
      case "wma":
        filesSet.get(FileType.AUDIO).add(file);
        break;
      case "zip":
      case "7z":
      case "rar":
        filesSet.get(FileType.ZIP).add(file);
        break;
      case "gif":
        filesSet.get(FileType.GIF).add(file);
        break;
      case "doc":
      case "docx":
        filesSet.get(FileType.DOC).add(file);
        break;
      case "xls":
      case "xlsx":
        filesSet.get(FileType.XLSX).add(file);
        break;
      case "ppt":
      case "pptx":
        filesSet.get(FileType.PPT).add(file);
        break;
      case "txt":
      case "md":
        filesSet.get(FileType.TEXT).add(file);
        break;
      default:
        filesSet.get(FileType.OTHER).add(file);
    }
  }

  /**
   * 遍历指定目录，找到“仅由小写字母、数字与下划线组成的文件名（不含扩展名）”的文件，
   * 将其移动到 unknown 目录（若不存在则创建）。
   * - 自动跳过 unknown 目录自身（避免自我移动）
   * - 若目标已存在同名文件，则自动追加 (n) 后缀避免覆盖
   */
  public static void moveUnknownNamedFiles(String scanDirPath, String unknownDirPath) {

    System.out.println("------------------------------Start to move unknown named files------------------------------");

    if (StringUtils.isEmpty(scanDirPath) || StringUtils.isEmpty(unknownDirPath)) {
      System.out.println("Empty path!");
      return;
    }

    File scanDir = new File(scanDirPath);
    if (!scanDir.exists() || !scanDir.isDirectory()) {
      System.out.println("scanDirPath is not exists or not a directory!");
      return;
    }

    File unknownDir = new File(unknownDirPath);
    if (!unknownDir.exists()) {
      boolean created = unknownDir.mkdirs();
      if (!created) {
        System.out.println("Create unknown dir failed!");
        return;
      }
    }

    List<File> candidates = new ArrayList<>();
    collectFilesRecursively(scanDir, candidates, unknownDir);

    int movedCount = 0;
    for (File file : candidates) {
      String name = file.getName();
      if (isUnknownStyleFileName(name)) {
        try {
          File target = resolveNonClobberTarget(unknownDir, name);
          FileUtils.moveFile(file, target);
          movedCount++;
        } catch (IOException e) {
          System.out.println("Move file failed: " + file.getAbsolutePath());
          e.printStackTrace();
        }
      }
    }

    System.out.println("Unknown named files moved: " + movedCount);
    System.out.println("------------------------------End to move unknown named files------------------------------");
  }

  private static void collectFilesRecursively(File dir, List<File> output, File excludeDir) {
    if (dir == null || !dir.exists()) {
      return;
    }
    if (isUnderDirectory(dir, excludeDir)) {
      return;
    }
    File[] files = dir.listFiles();
    if (files == null || files.length == 0) {
      return;
    }
    for (File f : files) {
      if (f.isDirectory()) {
        collectFilesRecursively(f, output, excludeDir);
      } else {
        if (!isUnderDirectory(f, excludeDir)) {
          output.add(f);
        }
      }
    }
  }

  private static boolean isUnknownStyleFileName(String fileName) {
    int idx = fileName.lastIndexOf('.');
    String base = idx >= 0 ? fileName.substring(0, idx) : fileName;
    String extLower = idx >= 0 ? fileName.substring(idx + 1).toLowerCase(Locale.ROOT) : "";
    // 排除 .gif 文件
    if ("gif".equals(extLower)) {
      return false;
    }
    String baseLower = base.toLowerCase(Locale.ROOT);
    if (!UNKNOWN_NAME_PATTERN.matcher(baseLower).matches()) {
      return false;
    }
    if (baseLower.length() < MIN_MEANINGLESS_LENGTH) {
      return false;
    }
    boolean hasDigit = baseLower.chars().anyMatch(Character::isDigit);
    boolean hasLetter = baseLower.chars().anyMatch(Character::isLetter);
    return hasDigit && hasLetter;
  }

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
   * 根据一定规则，清理目标文件下的重复文件，并把删除的文件备份到指定的文件目录。
   *
   * @param cleanFilePath 要清理的文件夹路径
   * @param deleteFildBackPath 备份文件夹路径
   */
  public static void cleanDuplicatedFiles(String cleanFilePath, String deleteFildBackPath) {

    System.out.println("------------------------------Start to do clean duplicated files------------------------------");

    if (StringUtils.isEmpty(cleanFilePath) || StringUtils.isEmpty(deleteFildBackPath)) {
      System.out.println("Empty path!");
      return;
    }

    File cleansFiles = new File(cleanFilePath);
    if (!cleansFiles.exists()) {
      System.out.println("cleanFilePath is not exists!");
      return;
    }

    // 备份目录对象（可能尚不存在），后续用于排除与保护
    File deleteFildBack = new File(deleteFildBackPath);

    HashMap<FileType, HashSet<File>> filesMap = iniFilesMap();
    getFileArray(cleansFiles, filesMap);
    System.out.println("获取目标路径下所有文件成功！");

    // 排除备份目录内的文件，避免被当作待删除文件再次处理
    for (FileType fileType : filesMap.keySet()) {
      HashSet<File> files = filesMap.get(fileType);
      if (CollectionUtils.isEmpty(files)) {
        continue;
      }
      files.removeIf(f -> isUnderDirectory(f, deleteFildBack));
    }

    System.out.println("------------------------------------------------------------");
    System.out.println("开始检查需要清理的文件...");

    List<File> toDeleteFiles = new ArrayList<>();
    for (FileType fileType : filesMap.keySet()) {
      // 当前目录下，每个文件类型下的所有文件
      HashSet<File> files = filesMap.get(fileType);
      if (CollectionUtils.isEmpty(files)) {
        System.out.println(fileType + "类型：不存在该类型文件，不用清理。");
        continue;
      }
      // 当前主要是基于文件名字的去重，所以这里传入文件名称，方便后面处理。
      List<String> fileNames = files.stream().map(File::getName).toList();
      Map<Boolean, List<File>> partitions = files.stream()
          .collect(Collectors.partitioningBy(f -> deleteCheck(f, fileNames)));
      List<File> keepFiles = partitions.get(true);
      List<File> deleteFiles = partitions.get(false);
      System.out.println(fileType + "类型：保留文件个数：" + keepFiles.size() + ", 需要删除文件个数：" + deleteFiles.size());
      toDeleteFiles.addAll(deleteFiles);
    }
    System.out.println("检查完毕，共需要删除文件个数：" + toDeleteFiles.size());
    System.out.println("------------------------------------------------------------");

    System.out.println("开始备份并删除文件...");
    if (deleteFildBack.exists()) {
      System.out.println("删除文件备份目录已存在");
    } else {
      System.out.println("删除文件备份路径不存在，开始创建该目录...");
      // mkdirs支持多级目录递归创建，自动生成所有缺失的父目录。
      boolean mkdirs = deleteFildBack.mkdirs();
      if (!mkdirs) {
        System.out.println("创建删除文件备份路径失败!");
        return;
      }
      System.out.println("创建删除文件备份路径成功!");
    }

    try {
      if (CollectionUtils.isEmpty(toDeleteFiles)) {
        System.out.println("没有需要删除的文件。");
      } else {
        for (File toDeleteFile : toDeleteFiles) {
          // 删除保护：若文件位于备份目录内，直接跳过
          if (isUnderDirectory(toDeleteFile, deleteFildBack)) {
            System.out.println("跳过备份目录内文件：" + toDeleteFile.getAbsolutePath());
            continue;
          }
          File newFile = new File(deleteFildBack + File.separator + toDeleteFile.getName());
          if (!newFile.exists()) {
            FileUtils.copyFile(toDeleteFile, newFile);
          }
          boolean delete = toDeleteFile.delete();
          if (!delete) {
            System.out.println("删除文件失败！");
          }
        }
        System.out.println("备份并删除文件完成");
      }
    } catch (IOException ioException) {
      System.out.println("备份文件失败！");
      ioException.printStackTrace();
    }
    System.out.println("------------------------------------------------------------");
    System.out.println("------------------------------End to do clean duplicated files------------------------------");
  }

  private static boolean deleteCheck(File singleFile, List<String> allFilesInSameType) {
    String fileName = singleFile.getName();
    int idx = fileName.lastIndexOf('.');
    String ext = idx >= 0 ? fileName.substring(idx) : ""; // 包含点
    String base = idx >= 0 ? fileName.substring(0, idx) : fileName;

    Matcher m = TRAILING_DUP_NUM.matcher(base);
    if (m.find()) {
      String originalBase = base.substring(0, m.start()).trim();
      String originalName = originalBase + ext;
      boolean originalExists = allFilesInSameType.stream()
          .anyMatch(fN -> !fileName.equals(fN) && fN.equalsIgnoreCase(originalName));
      // 原始文件存在，则当前为重复文件（应删除）
      return !originalExists;
    }
    // 非“末尾(数字)”样式，保留
    return true;
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

  /**
   * 判断给定文件是否位于指定目录下（含子目录）。
   * 若目录不存在或解析失败，则返回 false。
   */
  private static boolean isUnderDirectory(File file, File directory) {
    if (file == null || directory == null) {
      return false;
    }
    try {
      String filePath = file.getCanonicalPath();
      String dirPath = directory.getCanonicalPath();
      if (!dirPath.endsWith(File.separator)) {
        dirPath = dirPath + File.separator;
      }
      return filePath.startsWith(dirPath);
    } catch (IOException e) {
      return false;
    }
  }

  // 匹配文件名末尾的重复编号：(1)/(2) 或 （1）/（2）
  private static final Pattern TRAILING_DUP_NUM = Pattern.compile("\\s*[\\(（](\\d+)[\\)）]$");

  // 仅由小写字母、数字与下划线组成（至少含字母和数字各一个），用于识别“无意义”基名
  private static final Pattern UNKNOWN_NAME_PATTERN = Pattern.compile("[a-z0-9_]+");
  private static final int MIN_MEANINGLESS_LENGTH = 16; // 长度门槛，避免误判短名

}
