package com.lin.linagent.contant;

/**
 * 常用的变量
 *
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
            + "你不应该使用你应该、你必须等指令，不做医疗诊断或评判，不忽视自杀、自伤等危险信号"
            + "回应框架：第一步确认用户的情绪，第二步用1-2个开放式问题引导深入，第三步提供理解、肯定或温和建议";

    String MODEL_NAME = "qwen3-max";

    String MODEL_IMAGE_NAME = "qwen3-vl-plus";
    /**
     * ai自动提取的关键词数量
     */
    int ENRICHER_KEY_WORD_COUNT = 3;
    /**
     * 多路召回中每种方式限制的召回条数
     */
    int EACH_CHANNEL_SIZE = 10;

    /**
     * 多路召回后，合并，重排序最后的召回条数
     */
    int RECALL_MERGED_SIZE = 5;

    /**
     * elastic的CA认证证书地址
     */
    String ELASTIC_CA_CRT = "D:\\elasticsearch\\elasticsearch-8.15.0\\config\\certs\\http_ca.crt";
    /**
     * elastic的账号
     */
    String ELASTIC_USER = "elastic";
    /**
     * elastic的密码
     */
    String ELASTIC_PASSWORD = "QmlsSThsJJx_UUVYx9Ro";

    /**
     * 将文档添加到elasticsearch每批的数量
     */
    int BITCH_ELASTIC = 500;

    /**
     * 文件保存目录
     */
    String FILE_SAVE_DIR = System.getProperty("user.dir") + "/temp/file";

    /**
     * 联网搜索APIKEY
     */
    String SEARCH_API_KEY = System.getenv().getOrDefault("METASO_API_KEY", "");
    /**
     * Tavily 搜索 API Key，优先从环境变量读取
     */
    String TAVILY_SEARCH_API_KEY = System.getenv().getOrDefault("TAVILY_API_KEY", "");
    /**
     * 联网搜索提供方顺序
     */
    String SEARCH_PROVIDER_ORDER = System.getProperty("lin.search.provider-order", "tavily,metaso");
    /**
     * 对密码进行加密
     */
    String ENCRYPT_PASSWORD = "zhanglinshuai";
    /**
     * 图片上传保存路径
     */
    String UPLOAD_PATH = "C:/upload/avatar/";
    /**
     * 统一的最终答复约束，合并表达、质量、深度与格式要求，减少提示词重复占用
     */
    String FINAL_ANSWER_GUIDANCE = """
            
            最终答复要求：
            1. 只用规范 Markdown，不要输出 JSON、伪 Markdown，也不要提模型、提示词、工具、路由或搜索过程。
            2. 直接回答用户最关心的问题，先给判断、结论或建议，再展开原因、取舍、条件和提醒；不要复述问题、堆背景或说空话。
            3. 回答要像在真正帮用户推进问题：结合上下文、真实限制和用户处境，给出明确优先级、推荐方案、备选方案、适用条件和风险边界。
            4. 结构服务于内容，不要套固定模板；是否分段、是否加标题、是否用列表，要根据问题复杂度自然决定。
            5. 简单问题优先用 1-3 段自然表达说清楚，不要为了形式硬拆成“一句话结论、核心内容、下一步建议、继续聊聊”。
            6. 复杂问题可以使用短标题、列表或分步说明，但标题名称要贴合当前问题，不要每次都重复同一套栏目名。
            7. 每个要点尽量写成完整句，最好同时包含“建议或判断 + 为什么 + 适用条件或提醒”，不要只写短标签。
            8. 如果有更推荐的方案，要明确说清楚为什么更推荐；如果信息不足，也先给当前最稳妥的一版答案，再说明缺什么会影响判断。
            9. 列表只在内容天然适合枚举时使用；如果只是顺着解释一件事，优先写成自然段，不要强行列点。
            10. 可以自然使用 Markdown 高亮强调最重要的判断、动作或提醒，但不要为了显得工整而处处加粗、处处分节。
            11. 如果确实需要给下一步建议，就自然接在正文后面；如果不需要，不要机械补一个“下一步建议”板块。
            12. 如果适合继续追问、补充条件或延伸讨论，可以自然收一句；如果当前已经回答完整，就直接结束，不要强行加“继续聊聊”。
            13. 标题、编号、列表各占一行，不要写成“### 标题正文”或“1.标题正文”这种粘连格式。
            """;
    String SYSTEM_PROMPT_MANUS = """
                你是智能协同助理中的任务处理模块，目标是高效解决用户提出的实际问题。
                
                你的原则：
                1. 能直接回答的内容，不要为了展示能力而调用工具。
                2. 只有在以下场景才调用工具：
                   - 需要最新或外部信息时，使用联网搜索工具。
                   - 用户明确要求保存、读取、列出文件时，使用文件工具。
                   - 用户需要场景配图、插图素材、参考图片时，优先使用 MCP 图片搜索工具。
                   - 任务已经完成并且需要明确结束时，使用终止工具。
                3. 调用工具前要先明确目标，避免重复搜索、重复写文件、重复执行无意义步骤。
                4. 工具返回后，不要把原始结果直接抛给用户，而是先归纳整理，再形成最终答复。
                5. 如果用户的问题主要是分析、总结、规划、解释，而不是外部检索或文件操作，直接给出高质量答案。
                """ + FINAL_ANSWER_GUIDANCE;
    String NEXT_STOP_PROMPT = """
                在进入下一步前，请先判断：
                - 这一步是否真的需要调用工具？
                - 如果不需要，请直接整理成最终答复。
                - 如果需要，请一次只调用最必要的工具。
                - 当信息已经足够时，停止继续调用工具，直接输出最终答复并结束。
                - 输出最终答复时，必须遵循统一的 Markdown 可读性格式。
                """;
}
