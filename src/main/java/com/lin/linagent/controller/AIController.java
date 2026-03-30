package com.lin.linagent.controller;

import cn.hutool.json.JSON;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lin.linagent.auth.AuthHelper;
import com.lin.linagent.auth.LoginUserInfo;
import com.lin.linagent.auth.RequireLogin;
import com.lin.linagent.agent.graph.agent.QuestionAnswerAgent;
import com.lin.linagent.app.EmotionApp;
import com.lin.linagent.app.UnifiedAssistantApp;
import com.lin.linagent.common.BaseResponse;
import com.lin.linagent.common.ResultUtils;
import com.lin.linagent.domain.dto.EmotionReportRequest;
import com.lin.linagent.domain.dto.EmotionReportVO;
import com.lin.linagent.domain.dto.PdfExportRequest;
import com.lin.linagent.service.ContentSafetyService;
import com.lin.linagent.service.AssistantStreamSessionService;
import com.lin.linagent.service.PdfExportService;
import com.lin.linagent.tools.FileOperationTool;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequestMapping("/ai")
@RestController
@Slf4j
public class AIController {
    @Resource
    private EmotionApp emotionApp;

    @Resource
    private QuestionAnswerAgent questionAnswerAgent;

    @Resource
    private UnifiedAssistantApp unifiedAssistantApp;

    @Resource
    private FileOperationTool fileOperationTool;

    @Resource
    private ContentSafetyService contentSafetyService;

    @Resource
    private AssistantStreamSessionService assistantStreamSessionService;

    @Resource
    private PdfExportService pdfExportService;
    @GetMapping("/emotion/chat/sync")
    public String doChatWithEmotionAppSync(String message,String chatId){
        ContentSafetyService.SafetyDecision safetyDecision = contentSafetyService.inspectUserMessage(message);
        if (!safetyDecision.isPass()) {
            return safetyDecision.getUserMessage();
        }
        return emotionApp.doChat(message,chatId);
    }
    @GetMapping(value = "/emotion/chat/sse",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithEmotionAppSSE(String message,String chatId,String userId){
        ContentSafetyService.SafetyDecision safetyDecision = contentSafetyService.inspectUserMessage(message);
        if (!safetyDecision.isPass()) {
            return Flux.just(safetyDecision.getUserMessage());
        }
        return emotionApp.doChatByStream(message,chatId,userId);
    }
    @GetMapping("/emotion/chat/sse/emitter")
    public SseEmitter doChatWithEmotionAppSseEmitter(String message, String chatId,String userId) {
        ContentSafetyService.SafetyDecision safetyDecision = contentSafetyService.inspectUserMessage(message);
        if (!safetyDecision.isPass()) {
            SseEmitter emitter = new SseEmitter(180000L);
            try {
                emitter.send(safetyDecision.getUserMessage());
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter emitter = new SseEmitter(180000L); // 3分钟超时
        // 获取 Flux 数据流并直接订阅
        emotionApp.doChatByStream(message, chatId,userId)
                .subscribe(
                        // 处理每条消息
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        // 处理错误
                        emitter::completeWithError,
                        // 处理完成
                        emitter::complete
                );
        // 返回emitter
        return emitter;
    }

    @GetMapping(value = "/assistant/chat/sse/emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireLogin
    public SseEmitter doChatWithUnifiedAssistant(String message, String chatId, String userId, String mode, Boolean allowFileTool, Boolean allowWebSearchTool, Boolean allowKnowledgeBase, String uploadedFiles, String requestId, Boolean resume, HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter(180000L);
        List<String> uploadedFileList = parseUploadedFiles(uploadedFiles);
        LoginUserInfo loginUser = AuthHelper.getLoginUser(request);
        String effectiveRequestId = StringUtils.defaultIfBlank(requestId, chatId);
        assistantStreamSessionService.openOrResume(
                        effectiveRequestId,
                        loginUser.getId(),
                        Boolean.TRUE.equals(resume),
                        () -> unifiedAssistantApp.doChatByStream(
                                message,
                                chatId,
                                loginUser.getId(),
                                mode,
                                Boolean.TRUE.equals(allowFileTool),
                                Boolean.TRUE.equals(allowWebSearchTool),
                                Boolean.TRUE.equals(allowKnowledgeBase),
                                uploadedFileList
                        )
                )
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );
        return emitter;
    }

    @PostMapping("/emotion/report")
    @RequireLogin
    public BaseResponse<EmotionReportVO> generateEmotionReport(@RequestBody EmotionReportRequest request, HttpServletRequest httpServletRequest) {
        AuthHelper.getLoginUser(httpServletRequest);
        if (request == null) {
            return new BaseResponse<>(40000, null, "情感报告请求不能为空");
        }
        String reportSource = buildEmotionReportSource(request);
        if (StringUtils.isBlank(reportSource)) {
            return new BaseResponse<>(40000, null, "缺少可生成报告的对话内容");
        }
        String conversationId = StringUtils.defaultIfBlank(request.getConversationId(), "emotion-report");
        EmotionReportVO report = emotionApp.getEmotionReport(reportSource, conversationId);
        log.debug("情感报告生成完成，conversationId={}, report={}", conversationId, report);
        return ResultUtils.success(report);
    }

    @PostMapping("/pdf/export")
    @RequireLogin
    public ResponseEntity<byte[]> exportPdf(@RequestBody PdfExportRequest request, HttpServletRequest httpServletRequest) {
        AuthHelper.getLoginUser(httpServletRequest);
        byte[] pdfBytes = pdfExportService.generatePdf(request);
        String rawFileName = StringUtils.defaultIfBlank(request == null ? null : request.getFileName(), "export.pdf");
        String fileName = rawFileName.toLowerCase().endsWith(".pdf") ? rawFileName : rawFileName + ".pdf";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdfBytes.length))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(pdfBytes);
    }

    @PostMapping("/assistant/file/upload")
    @RequireLogin
    public BaseResponse<String> uploadAssistantFile(MultipartFile file, HttpServletRequest request) throws IOException {
        AuthHelper.getLoginUser(request);
        if (file == null || file.isEmpty()) {
            return new BaseResponse<>(40000, null, "上传文件不能为空");
        }
        String fileName = fileOperationTool.saveUploadedFile(file.getOriginalFilename(), file.getBytes());
        return ResultUtils.success(fileName);
    }

    @GetMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireLogin
    public SseEmitter doChatWithManus(String message, String chatId, String userId, HttpServletRequest request){
        SseEmitter emitter = new SseEmitter(180000L);
        LoginUserInfo loginUser = AuthHelper.getLoginUser(request);
        unifiedAssistantApp.doManusChatByStream(message, chatId, loginUser.getId())
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );
        return emitter;
    }

    /**
     *
     * @param message
     * @param threadId
     */
    @GetMapping(value = "/manus/graph")
    public String doChatWithGraph(String message, String threadId) throws GraphStateException {
        Map<String,Object> initialState = Map.of(
          "original_input",message
        );
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();
        CompiledGraph graph = questionAnswerAgent.createQuestionAnswerGraph();
        Optional<OverAllState> invoke = graph.invoke(initialState, config);
        String jsonString = invoke.toString();
        if (jsonString.startsWith("Optional[")) {
            jsonString = jsonString.substring(9, jsonString.length() - 1);
        }
        Gson gson = new Gson();
        JsonObject jsonObject  = gson.fromJson(jsonString, JsonObject.class);
        JsonObject overAllState = jsonObject.getAsJsonObject("OverAllState");
        JsonObject data = overAllState.getAsJsonObject("data");
        String optimizedSearchResult = data.get("optimized_search_result").getAsString();
        return optimizedSearchResult;
    }

    /**
     * 解析前端传入的上传文件列表
     * @param uploadedFiles JSON字符串
     * @return 文件名列表
     */
    private List<String> parseUploadedFiles(String uploadedFiles) {
        if (uploadedFiles == null || uploadedFiles.trim().isEmpty()) {
            return List.of();
        }
        try {
            Gson gson = new Gson();
            String[] files = gson.fromJson(uploadedFiles, String[].class);
            return files == null ? List.of() : java.util.Arrays.asList(files);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 构造情感报告输入文本
     * @param request 请求
     * @return 输入文本
     */
    private String buildEmotionReportSource(EmotionReportRequest request) {
        if (request == null) {
            return "";
        }
        String userMessage = StringUtils.trimToEmpty(request.getUserMessage());
        String assistantMessage = StringUtils.trimToEmpty(request.getAssistantMessage());
        StringBuilder builder = new StringBuilder();
        if (StringUtils.isNotBlank(userMessage)) {
            builder.append("用户表达：\n").append(userMessage);
        }
        if (StringUtils.isNotBlank(assistantMessage)) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("当前回复：\n").append(assistantMessage);
        }
        return builder.toString().trim();
    }
}
