package com.lin.linagent.aspect;

import com.lin.linagent.support.OperationLogHelper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 统一操作日志切面
 */
@Slf4j
@Aspect
@Component
public class OperationAuditAspect {

    @Resource
    private OperationLogHelper operationLogHelper;

    /**
     * 记录控制器正常返回的操作日志
     * @param joinPoint 切点
     * @return 返回值
     * @throws Throwable 异常
     */
    @Around("execution(public * com.lin.linagent.controller..*(..))")
    public Object auditControllerOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        HttpServletRequest request = resolveRequest(joinPoint.getArgs());
        if (request != null) {
            request.setAttribute(OperationLogHelper.REQUEST_START_TIME_ATTRIBUTE, startTime);
        }
        if (operationLogHelper.shouldSkipApiLog(request)) {
            return joinPoint.proceed();
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Map<String, Object> parameters = operationLogHelper.extractParameters(signature.getParameterNames(), joinPoint.getArgs());
        Object result = joinPoint.proceed();
        String level = operationLogHelper.resolveResponseLevel(result);
        String summary = operationLogHelper.buildApiSummary(
                request,
                operationLogHelper.isSuccessResponse(result) ? "接口调用成功" : "接口调用失败",
                signature.getDeclaringType().getSimpleName() + "." + signature.getName()
        );
        String detail = operationLogHelper.buildApiDetail(
                request,
                signature.getDeclaringType().getSimpleName() + "." + signature.getName(),
                parameters,
                result,
                null,
                System.currentTimeMillis() - startTime
        );
        operationLogHelper.write(level, operationLogHelper.resolveApiCategory(request), summary, detail);
        return result;
    }

    /**
     * 记录定时任务日志
     * @param joinPoint 切点
     * @return 返回值
     * @throws Throwable 异常
     */
    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object auditScheduledOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String taskName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        Map<String, Object> parameters = operationLogHelper.extractParameters(signature.getParameterNames(), joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            operationLogHelper.write(
                    "INFO",
                    "scheduler",
                    operationLogHelper.buildSchedulerSummary(taskName, true),
                    operationLogHelper.buildSchedulerDetail(taskName, parameters, result, null, System.currentTimeMillis() - startTime)
            );
            return result;
        } catch (Throwable throwable) {
            operationLogHelper.write(
                    operationLogHelper.resolveExceptionLevel(throwable),
                    "scheduler",
                    operationLogHelper.buildSchedulerSummary(taskName, false),
                    operationLogHelper.buildSchedulerDetail(taskName, parameters, null, throwable, System.currentTimeMillis() - startTime)
            );
            throw throwable;
        }
    }

    /**
     * 解析请求对象
     * @param args 参数
     * @return 请求
     */
    private HttpServletRequest resolveRequest(Object[] args) {
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof HttpServletRequest request) {
                    return request;
                }
            }
        }
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
