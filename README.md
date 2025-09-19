## WeChat File Extract

一个用于整理与去重微信文件的命令行工具：
- 按文件类型（PDF/IMG/AUDIO/ZIP/GIF/DOC/XLSX/PPT/TEXT/OTHER）分类收集源目录下的所有文件
- 将文件复制到目标目录的对应子目录中（例如: dst/PDF, dst/IMG ...）
- 去重规则：若目标目录已存在“去重键”相同的文件（忽略文件名末尾的 (数字)/(中文括号数字) 后缀），则跳过复制
- 支持清理重复命名的冗余文件，并将被删除的文件先备份到指定目录

### 环境要求
- JDK 21
- Maven 3.6+

### 构建
```bash
mvn -q -DskipTests package
```
构建完成后会在 `target/` 目录下生成可运行的 fat-jar：`fileExtract-jar-with-dependencies.jar`。

### 使用
1) 文件抽取与分类复制
```bash
java -jar target/fileExtract-jar-with-dependencies.jar "<源目录绝对路径>" "<目标目录绝对路径>"
```
示例（Windows PowerShell）：
```bash
java -jar target/fileExtract-jar-with-dependencies.jar "C:\WeChat Files\<wxid>\FileStorage\File" "C:\other_info\wechat_archived_file"
```

运行后效果：
- 从源目录递归扫描所有文件
- 按扩展名识别类型并汇总
- 去重（忽略文件名末尾的 (数字)/(中文括号数字) 后缀）
- 复制到目标目录下对应类型的子目录

2) 清理重复文件（备份后删除）

该能力通过代码方法提供：`FileExtract.cleanDuplicatedFiles(cleanFilePath, deleteFildBackPath)`。

你可以直接运行测试用例（推荐先备份重要数据）：
```bash
mvn -q -Dtest=extract.FileExtractTest#testCleanDuplicatedFiles test
```
或在你自己的代码中调用：
```java
FileExtract.cleanDuplicatedFiles("<需要清理的目录>", "<删除文件的备份目录>");
```

清理规则（默认开启大小写不敏感匹配）：
- 匹配形如：`文件名(1).ext`、`文件名（2）.ext` 的重复命名文件
- 若同目录下存在对应的原始文件 `文件名.ext`，则视当前文件为重复，会先备份再删除

 3) 移动“无意义文件名”的文件到 unknown 目录

  规则：仅当“文件基名”（不含扩展名）满足以下条件才会被移动：
  - 只包含小写字母、数字、下划线
  - 长度 >= 16
  - 同时包含字母与数字
  - 自动排除 .gif 文件
  示例：`291a038e14de4c06c33284b7ee17d565_34f3d76c3f17aa4b62171afabb175d01_8.xlsx`

  通过测试执行（示例）：
  ```bash
  mvn -q -Dtest=extract.FileExtractTest#testMoveUnknownNamedFiles test
  ```

  在你的代码中调用：
  ```java
  FileExtract.moveUnknownNamedFiles("<需要扫描的目录>", "<unknown 目录>");
  ```

  注意：程序会自动跳过 unknown 目录自身并避免重复移动；若 unknown 目录下已存在同名文件，将自动以 (n) 结尾重命名以避免覆盖。

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
- 强烈建议在执行任何“清理/删除”操作前做好数据备份
- Windows 路径包含空格时请使用引号包裹
- 去重仅以“文件名完全一致”为准，不做内容哈希比对
- 复制阶段的去重在“类型内”进行（同类型同名文件会被跳过）
 - 清理重复文件时：程序会自动排除备份目录内的文件，并保护性跳过位于备份目录下的任何文件，避免误删
 - 建议将备份目录设置为清理目录之外的路径（不要嵌套在需要清理的目录中）
 - 移动 unknown 文件时会忽略 .gif 文件

### 许可证
本项目仅供学习与个人使用，请在遵守相关法律与使用条款的前提下使用。


