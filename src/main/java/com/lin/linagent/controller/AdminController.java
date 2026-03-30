package com.lin.linagent.controller;

import com.lin.linagent.auth.AuthHelper;
import com.lin.linagent.auth.RequireAdmin;
import com.lin.linagent.common.BaseResponse;
import com.lin.linagent.common.ResultUtils;
import com.lin.linagent.domain.AdminLogEntry;
import com.lin.linagent.domain.dto.AdminConversationVO;
import com.lin.linagent.domain.dto.AdminDashboardVO;
import com.lin.linagent.domain.dto.AdminUserDeleteStateRequest;
import com.lin.linagent.domain.dto.AdminUserRoleUpdateRequest;
import com.lin.linagent.domain.dto.AdminUserVO;
import com.lin.linagent.domain.dto.KnowledgeDocumentSaveRequest;
import com.lin.linagent.domain.dto.KnowledgeIndexTaskVO;
import com.lin.linagent.domain.dto.KnowledgeDocumentVO;
import com.lin.linagent.domain.dto.KnowledgeRebuildResultVO;
import com.lin.linagent.domain.dto.KnowledgeUploadResultVO;
import com.lin.linagent.service.AdminConsoleService;
import com.lin.linagent.service.AdminLogService;
import com.lin.linagent.service.KnowledgeBaseAdminService;
import com.lin.linagent.service.KnowledgeIndexTaskService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 管理后台接口
 */
@RestController
@RequestMapping("/admin")
@RequireAdmin
public class AdminController {

    @Resource
    private AdminConsoleService adminConsoleService;

    @Resource
    private KnowledgeBaseAdminService knowledgeBaseAdminService;

    @Resource
    private KnowledgeIndexTaskService knowledgeIndexTaskService;

    @Resource
    private AdminLogService adminLogService;

    @GetMapping("/dashboard")
    public BaseResponse<AdminDashboardVO> getDashboard(HttpServletRequest request) {
        AuthHelper.getAdminLoginUser(request);
        return ResultUtils.success(adminConsoleService.buildDashboard());
    }

    @GetMapping("/logs")
    public BaseResponse<List<AdminLogEntry>> getLogs(@RequestParam(defaultValue = "80") Integer limit,
                                                     String level,
                                                     String category,
                                                     String keyword,
                                                     HttpServletRequest request) {
        AuthHelper.getAdminLoginUser(request);
        return ResultUtils.success(adminLogService.listRecent(limit == null ? 80 : limit, level, category, keyword));
    }

    @DeleteMapping("/logs")
    public BaseResponse<Boolean> clearLogs(HttpServletRequest request) {
        String operator = AuthHelper.getAdminLoginUser(request).getUserName();
        adminLogService.clear();
        adminLogService.info("admin-log", "管理员清空运行日志", "operator=" + operator);
        return ResultUtils.success(true);
    }

    @GetMapping("/users")
    public BaseResponse<List<AdminUserVO>> listUsers(String keyword, Integer userRole, Boolean includeDeleted, Boolean deleted, @RequestParam(defaultValue = "120") Integer limit, HttpServletRequest request) {
        AuthHelper.getAdminLoginUser(request);
        return ResultUtils.success(adminConsoleService.listUsers(keyword, userRole, includeDeleted, deleted, limit));
    }

    @PostMapping("/user/role")
    public BaseResponse<Boolean> updateUserRole(@RequestBody AdminUserRoleUpdateRequest request, HttpServletRequest httpServletRequest) {
        AuthHelper.getAdminLoginUser(httpServletRequest);
        if (request == null) {
            return new BaseResponse<>(40000, null, "请求不能为空");
        }
        return ResultUtils.success(adminConsoleService.updateUserRole(request.getUserId(), request.getUserRole()));
    }

    @PostMapping("/user/delete-state")
    public BaseResponse<Boolean> updateUserDeleteState(@RequestBody AdminUserDeleteStateRequest request, HttpServletRequest httpServletRequest) {
        String operatorId = AuthHelper.getAdminLoginUser(httpServletRequest).getId();
        if (request == null) {
            return new BaseResponse<>(40000, null, "请求不能为空");
        }
        return ResultUtils.success(adminConsoleService.updateUserDeleteState(operatorId, request.getUserId(), Boolean.TRUE.equals(request.getDeleted())));
    }

    @GetMapping("/conversations")
    public BaseResponse<List<AdminConversationVO>> listConversations(String keyword, String mode, Boolean pinned, @RequestParam(defaultValue = "180") Integer limit, HttpServletRequest request) {
        AuthHelper.getAdminLoginUser(request);
        return ResultUtils.success(adminConsoleService.listConversations(keyword, mode, pinned, limit));
    }

    @DeleteMapping("/conversation")
    public BaseResponse<Boolean> deleteConversation(String conversationId, String userId, HttpServletRequest request) {
        AuthHelper.getAdminLoginUser(request);
        return ResultUtils.success(adminConsoleService.deleteConversation(conversationId, userId));
    }

    @GetMapping("/knowledge/documents")
    public BaseResponse<List<KnowledgeDocumentVO>> listKnowledgeDocuments(HttpServletRequest request) {
        AuthHelper.getAdminLoginUser(request);
        return ResultUtils.success(knowledgeBaseAdminService.listDocuments());
    }

    @GetMapping("/knowledge/document")
    public BaseResponse<KnowledgeDocumentVO> getKnowledgeDocument(String fileName, HttpServletRequest request) {
        AuthHelper.getAdminLoginUser(request);
        return ResultUtils.success(knowledgeBaseAdminService.getDocument(fileName));
    }

    @PostMapping("/knowledge/document")
    public BaseResponse<KnowledgeDocumentVO> saveKnowledgeDocument(@RequestBody KnowledgeDocumentSaveRequest request, HttpServletRequest httpServletRequest) {
        AuthHelper.getAdminLoginUser(httpServletRequest);
        return ResultUtils.success(knowledgeBaseAdminService.saveDocument(request));
    }

    @PostMapping("/knowledge/upload")
    public BaseResponse<KnowledgeUploadResultVO> uploadKnowledgeDocument(@RequestParam("file") MultipartFile file,
                                                                         @RequestParam(value = "rebuildIndex", required = false) Boolean rebuildIndex,
                                                                         HttpServletRequest request) throws IOException {
        AuthHelper.getAdminLoginUser(request);
        if (file == null || file.isEmpty()) {
            return new BaseResponse<>(40000, null, "上传文件不能为空");
        }
        KnowledgeDocumentSaveRequest saveRequest = new KnowledgeDocumentSaveRequest();
        saveRequest.setFileName(file.getOriginalFilename());
        saveRequest.setContent(new String(file.getBytes(), StandardCharsets.UTF_8));
        saveRequest.setRebuildIndex(Boolean.TRUE.equals(rebuildIndex));
        if (StringUtils.isBlank(saveRequest.getFileName())) {
            saveRequest.setFileName("未命名知识库文档.md");
        }
        return ResultUtils.success(knowledgeIndexTaskService.submitUploadTask(saveRequest));
    }

    @GetMapping("/knowledge/task")
    public BaseResponse<KnowledgeIndexTaskVO> getKnowledgeTask(String taskId, HttpServletRequest request) {
        AuthHelper.getAdminLoginUser(request);
        return ResultUtils.success(knowledgeIndexTaskService.getTask(taskId));
    }

    @DeleteMapping("/knowledge/document")
    public BaseResponse<Boolean> deleteKnowledgeDocument(String fileName, Boolean rebuildIndex, HttpServletRequest request) {
        AuthHelper.getAdminLoginUser(request);
        return ResultUtils.success(knowledgeBaseAdminService.deleteDocument(fileName, Boolean.TRUE.equals(rebuildIndex)));
    }

    @PostMapping("/knowledge/rebuild")
    public BaseResponse<KnowledgeRebuildResultVO> rebuildKnowledgeIndexes(HttpServletRequest request) {
        AuthHelper.getAdminLoginUser(request);
        return ResultUtils.success(knowledgeBaseAdminService.rebuildIndexes());
    }
}
