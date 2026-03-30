package com.lin.linagent.exception;

import com.lin.linagent.common.BaseResponse;
import com.lin.linagent.common.ResultUtils;
import com.lin.linagent.support.OperationLogHelper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Resource
    private OperationLogHelper operationLogHelper;

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e, HttpServletRequest request) {
        log.error("BusinessException", e);
        writeOperationLog(request, e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runTimeExceptionHandler(RuntimeException e, HttpServletRequest request) {
        log.error("RuntimeException", e);
        writeOperationLog(request, e);
        String message = StringUtils.defaultIfBlank(e == null ? null : e.getMessage(), "系统错误");
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, message);
    }

    /**
     * 写接口失败日志
     * @param request 请求
     * @param throwable 异常
     */
    private void writeOperationLog(HttpServletRequest request, Throwable throwable) {
        if (operationLogHelper.shouldSkipApiLog(request)) {
            return;
        }
        String summary = operationLogHelper.buildApiSummary(request, "接口调用失败", "unknown");
        String detail = operationLogHelper.buildApiDetail(
                request,
                "exception-handler",
                null,
                null,
                throwable,
                operationLogHelper.resolveDuration(request)
        );
        operationLogHelper.write(
                operationLogHelper.resolveExceptionLevel(throwable),
                operationLogHelper.resolveApiCategory(request),
                summary,
                detail
        );
    }
}
