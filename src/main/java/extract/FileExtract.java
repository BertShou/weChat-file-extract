package extract;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;

/**
 * @author ShouY25713
 */
public class FileExtract {

  static String srcPath = "E:\\WeChat Files\\xjdesh\\FileStorage\\File";
  static String srcPath_2022_02 = "E:\\WeChat Files\\xjdesh\\FileStorage\\File\\2022-02";
  static String dstPath = "D:\\other\\wechat_archived_file";

  enum FileType {
    GIF, PDF, OTHER;
  }

  public static void main(String[] args) {
    // get copy files
    if (args == null || args.length == 0 || args[0] == null || args[0].isEmpty()) {
      System.out.println("Please input srcPath!");
      return;
    }
    String srcPath = args[0];
    System.out.println("srcPath is :" + srcPath);
    File srcFolders = new File(srcPath);
    //    File srcFolders = new File(srcPath_2022_02);
    if (!srcFolders.exists()) {
      System.out.println("srcPath is not exists!");
      return;
    }

    HashMap<FileType, HashSet<File>> copyFiles = new HashMap<>(3);
    copyFiles.put(FileType.PDF, new HashSet<>());
    copyFiles.put(FileType.GIF, new HashSet<>());
    copyFiles.put(FileType.OTHER, new HashSet<>());
    getFileArray(srcFolders, copyFiles);
    //    System.out.println("srcFiles:" + copyFiles);

    // get exist files
    if (args[1] == null || args[1].isEmpty()) {
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
    HashMap<FileType, HashSet<File>> dstFiles = new HashMap<>(3);
    dstFiles.put(FileType.PDF, new HashSet<>());
    dstFiles.put(FileType.GIF, new HashSet<>());
    dstFiles.put(FileType.OTHER, new HashSet<>());
    getFileArray(dstFolders, dstFiles);
    //    System.out.println("dstFiles:" + dstFiles);

    // distinct
    distinct(copyFiles, dstFiles);

    // copy
    System.out.println("------------------------------start to copy------------------------------");
    System.out.println("------------------------------copyFiles:" + copyFiles + "------------------------------");
    copyFiles(copyFiles, dstPath);
    System.out.println("------------------------------copy finish!------------------------------");
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

  private static void distinct(HashMap<FileType, HashSet<File>> copyFiles, HashMap<FileType, HashSet<File>> dstFiles) {
    copyFiles.put(FileType.PDF, new HashSet<>(copyFiles.get(FileType.PDF).stream()
        .filter(
            copyFile -> dstFiles.get(FileType.PDF).stream().noneMatch(dstFile -> copyFile.getName().equals(dstFile.getName())))
        .collect(Collectors.toSet())));
    copyFiles.put(FileType.GIF, new HashSet<>(copyFiles.get(FileType.GIF).stream()
        .filter(
            copyFile -> dstFiles.get(FileType.GIF).stream().noneMatch(dstFile -> copyFile.getName().equals(dstFile.getName())))
        .collect(Collectors.toSet())));
    copyFiles.put(FileType.OTHER, new HashSet<>(copyFiles.get(FileType.OTHER).stream()
        .filter(
            copyFile -> dstFiles.get(FileType.OTHER).stream().noneMatch(dstFile -> copyFile.getName().equals(dstFile.getName())))
        .collect(Collectors.toSet())));
  }

  private static void getFileArray(File file, HashMap<FileType, HashSet<File>> copyFiles) {
    if (file.exists()) {
      if (file.isFile()) {
        handleFile(file, copyFiles);
      } else {
        File[] files = file.listFiles();
        if (null != files && files.length > 0) {
          if (Arrays.stream(files).anyMatch(File::isDirectory)) {
            for (File tempFile : files) {
              getFileArray(tempFile, copyFiles);
            }
          } else {
            for (File tempFile : files) {
              handleFile(tempFile, copyFiles);
            }
          }
        }
      }
    }
  }

  private static void handleFile(File file, HashMap<FileType, HashSet<File>> copyFiles) {
    String[] fileNameArrays = file.getName().split("\\.");
    String extension = fileNameArrays[fileNameArrays.length - 1];
    switch (extension) {
      case "pdf":
        copyFiles.get(FileType.PDF).add(file);
        break;
      case "gif":
        copyFiles.get(FileType.GIF).add(file);
        break;
      default:
        copyFiles.get(FileType.OTHER).add(file);
    }
  }

}
