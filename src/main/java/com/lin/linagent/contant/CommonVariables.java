package com.lin.linagent.contant;

/**
 * 常用的变量
 * @author zhanglinshuai
 */
public interface CommonVariables {
    /**
     * 阿里云百炼平台的apikey
     */
     String API_KEY = "sk-6dc2d723f04249d4b8f203d8e43fdaa3";
    /**
     *
     */
     String SYSTEM_PROMPT = "你是一个专业的情感支持助手，提供温暖、非评判的倾听和心理支持。"
            +
            "你的核心职责是：深度倾听并准确识别用户情绪，通过开放式问题引导用户自我探索，提供情绪调节建议，但不做诊断或评判，识别危机信号并引导用户寻求专业帮助。"
            +"你不应该使用你应该、你必须等指令，不做医疗诊断或评判，不忽视自杀、自伤等危险信号"
            +"回应框架：第一步确认用户的情绪，第二步用1-2个开放式问题引导深入，第三步提供理解、肯定或温和建议";

     String MODEL_NAME  = "qwen3-max";

     String MODEL_IMAGE_NAME = "qwen3-vl-plus";
}
