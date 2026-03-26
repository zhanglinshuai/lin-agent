package com.lin.linagent.tools;

import com.lin.linagent.contant.CommonVariables;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FileOperationTool {

    /**
     * 工具允许操作的根目录
     */
    private static final Path FILE_DIR = Paths.get(CommonVariables.FILE_SAVE_DIR, "file").normalize();

    /**
     * 保存上传的文本文件
     * @param originalFilename 原始文件名
     * @param content 文件内容
     * @return 保存后的文件名
     * @throws IOException 写入异常
     */
    public String saveUploadedFile(String originalFilename, byte[] content) throws IOException {
        String safeName = sanitizeFileName(originalFilename);
        if (!isSupportedTextFile(safeName)) {
            throw new IllegalArgumentException("仅支持 txt、md、json、csv 文件");
        }
        Files.createDirectories(FILE_DIR);
        Path path = resolveSafePath(safeName);
        Files.write(path, content);
        return path.getFileName().toString();
    }

    @Tool(description = "读取已生成文件的内容。只有当用户明确提到某个文件、要求查看已保存内容、或者需要继续编辑现有文档时才使用。")
    public String readFile(@ToolParam(description = "需要读取的文件名，例如“约会计划.md”") String fileName){
        try {
            Path path = resolveSafePath(fileName);
            if (!Files.exists(path)) {
                return "读取失败：文件不存在 -> " + path.getFileName();
            }
            if (!Files.isRegularFile(path)) {
                return "读取失败：目标不是文件 -> " + path.getFileName();
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return "文件名：" + path.getFileName() + "\n文件路径：" + path + "\n文件内容：\n" + content;
        } catch (Exception e) {
            return "读取文件失败：" + e.getMessage();
        }
    }

    @Tool(description = "将内容保存为 UTF-8 文本文件。只有当用户明确提出“保存成文件”“导出”“写入文档”等需求时才使用。")
    public String writeFile(
            @ToolParam(description = "保存后的文件名，例如“周末安排.md”") String fileName,
            @ToolParam(description = "需要写入文件的完整内容") String content){
        try {
            Path path = resolveSafePath(fileName);
            Files.createDirectories(FILE_DIR);
            Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
            return "文件已保存成功。\n文件名：" + path.getFileName() + "\n文件路径：" + path;
        } catch (Exception e) {
            return "写入文件失败：" + e.getMessage();
        }
    }

    @Tool(description = "列出当前可读取的已生成文件。只有当用户想查看现有文件列表、继续之前保存的内容时才使用。")
    public String listFiles() {
        try {
            Files.createDirectories(FILE_DIR);
            List<Path> files = Files.list(FILE_DIR)
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .toList();
            if (files.isEmpty()) {
                return "当前目录下还没有已保存文件。";
            }
            String fileList = files.stream()
                    .map(path -> "- " + path.getFileName())
                    .collect(Collectors.joining("\n"));
            return "当前可用文件列表：\n" + fileList;
        } catch (IOException e) {
            return "获取文件列表失败：" + e.getMessage();
        }
    }

    /**
     * 解析并校验文件路径，防止越界访问
     * @param fileName 文件名
     * @return 安全路径
     */
    private Path resolveSafePath(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        Path resolved = FILE_DIR.resolve(fileName.trim()).normalize();
        if (!resolved.startsWith(FILE_DIR)) {
            throw new IllegalArgumentException("不允许访问工作目录之外的文件");
        }
        return resolved;
    }

    /**
     * 过滤文件名中的危险字符
     * @param fileName 文件名
     * @return 安全文件名
     */
    private String sanitizeFileName(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        return normalized.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 是否为可读取文本文件
     * @param fileName 文件名
     * @return 是否支持
     */
    private boolean isSupportedTextFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".txt")
                || lower.endsWith(".md")
                || lower.endsWith(".json")
                || lower.endsWith(".csv");
    }
}
