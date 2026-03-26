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
import com.lin.linagent.agent.graph.agent.QuestionAnswerAgent;
import com.lin.linagent.app.EmotionApp;
import com.lin.linagent.app.UnifiedAssistantApp;
import com.lin.linagent.common.BaseResponse;
import com.lin.linagent.common.ResultUtils;
import com.lin.linagent.tools.FileOperationTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
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
    @GetMapping("/emotion/chat/sync")
    public String doChatWithEmotionAppSync(String message,String chatId){
        return emotionApp.doChat(message,chatId);
    }
    @GetMapping(value = "/emotion/chat/sse",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithEmotionAppSSE(String message,String chatId,String userId){
        return emotionApp.doChatByStream(message,chatId,userId);
    }
    @GetMapping("/emotion/chat/sse/emitter")
    public SseEmitter doChatWithEmotionAppSseEmitter(String message, String chatId,String userId) {
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

    @GetMapping("/assistant/chat/sse/emitter")
    public SseEmitter doChatWithUnifiedAssistant(String message, String chatId, String userId, String mode, Boolean allowFileTool, Boolean allowWebSearchTool, String uploadedFiles) {
        SseEmitter emitter = new SseEmitter(180000L);
        List<String> uploadedFileList = parseUploadedFiles(uploadedFiles);
        unifiedAssistantApp.doChatByStream(
                        message,
                        chatId,
                        userId,
                        mode,
                        Boolean.TRUE.equals(allowFileTool),
                        Boolean.TRUE.equals(allowWebSearchTool),
                        uploadedFileList
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

    @PostMapping("/assistant/file/upload")
    public BaseResponse<String> uploadAssistantFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return new BaseResponse<>(40000, null, "上传文件不能为空");
        }
        String fileName = fileOperationTool.saveUploadedFile(file.getOriginalFilename(), file.getBytes());
        return ResultUtils.success(fileName);
    }

    @GetMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter doChatWithManus(String message, String chatId, String userId){
        SseEmitter emitter = new SseEmitter(180000L);
        unifiedAssistantApp.doManusChatByStream(message, chatId, userId)
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
}
