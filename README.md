## WeChat File Extract

用于核对和归档微信接收的文件：

- `scan` 按 SHA-256 对比源目录与归档目录，只生成报告，不修改文件。
- `process` 按文件名去重键选择最新版本，备份并验证旧版本后再删除。
- 最新文件按扩展名归入 PDF、IMG、AUDIO、ZIP、GIF、DOC、XLSX、PPT、TEXT 或 OTHER 子目录。

### 环境要求

- JDK 21
- Maven 3.6+

### 构建

```bash
mvn -q -DskipTests package
```

构建完成后会在 `target/` 目录下生成可运行的 fat-jar：`fileExtract-jar-with-dependencies.jar`。

### 使用

0) 只读核对已有归档

```bash
java -jar target/fileExtract-jar-with-dependencies.jar scan "<源目录绝对路径>" "<归档目录绝对路径>"
```

默认将 CSV 清单写入 `target/wechat-file-scan.csv`。扫描按 SHA-256 标记已归档文件；同名但内容不同的文件会标记为冲突。该命令不会移动、复制或删除源文件和归档文件。也可以添加第三个参数指定报告路径。

需要扫描多个源目录时，可重复使用 `--source`：

```bash
java -jar target/fileExtract-jar-with-dependencies.jar scan --source "<源目录1>" --source "<源目录2>" --archive "<归档目录>" --report "target\wechat-file-scan.csv"
```

需要处理待归档文件时：

```bash
java -jar target/fileExtract-jar-with-dependencies.jar process "<源目录>" "<归档目录>" "<重复文件备份目录>" ["<处理报告CSV路径>"]
```

按去重键分组（忽略大小写及末尾的 `(数字)`/`（数字）），保留最后修改时间最新的版本；旧版本先备份并校验哈希，再删除源文件或较旧的归档副本。默认报告写入 `
target/wechat-file-process.csv`。若最新时间相同但内容不同，处理会停止，不会删除文件。

#### 当前微信文件工作流

- 当前新文件源目录使用 `C:\xwechat_files\<account>\msg\file`；不要扫描整个 `C:\xwechat_files` 根目录。
- `msg\attach` 中的 `.dat` 附件和 `msg\video` 不在当前归档范围内。
- 旧目录 `C:\WeChat Files\<wxid>\FileStorage\File` 已清理；后续只需扫描新的 `msg\file` 目录。

推荐流程：

1. 运行 `scan`，只读生成 CSV。`ALREADY_ARCHIVED` 表示归档中存在哈希一致的文件；`NAME_CONFLICT` 和 `NOT_ARCHIVED` 是待处理项。
2. 检查扫描报告后，运行 `process`。该命令按文件最后修改时间（UTC）选择最新版本，将旧版本备份并校验哈希，确认归档副本后删除源文件；必要时也会移除较旧的归档副本及空父目录。
3. 若同一去重键下最新时间并列且内容不同，`process` 会停止并保留文件，需人工判断。复制、备份、校验或删除失败也会显式报错。

### 文件类型识别

- PDF: .pdf
- IMG: .png .jpg
- AUDIO: .mp3 .mp4 .wav .m4a .wma
- ZIP: .zip .7z .rar
- GIF: .gif
- DOC: .doc .docx
- XLSX: .xls .xlsx
- PPT: .ppt .pptx
- TEXT: .txt .md
- OTHER: 其它未覆盖后缀或无后缀文件

### 注意事项

- Windows 路径包含空格时请使用引号包裹。
- `process` 会先完成备份和 SHA-256 校验，再删除源文件；源目录根目录保留，只清理空的子目录。
- 扫描与处理报告均使用带 UTF-8 BOM 的 CSV，可直接用 Excel 打开。

### 许可证

本项目仅供学习与个人使用，请在遵守相关法律与使用条款的前提下使用。
