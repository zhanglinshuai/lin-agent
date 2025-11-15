package com.lin.linagent.chatMemory.dialect;

/**
 * 自定义 mysqlChatMemoryRepositoryDialect
 * @author zhanglinshuai
 */
public class CustomMysqlJdbcChatMemoryRepositoryDialect implements CustomJdbcChatMemoryRepositoryDialect {


    public String getSelectMessagesSql() {
        return "SELECT content,message_type,metadata FROM chat_memory WHERE conversation_id = ? ORDER BY create_time";
    }


    public String getInsertMessageSql() {
        return "INSERT INTO chat_memory (conversation_id,content,message_type,create_time,user_id,metadata) VALUES (?,?,?,?,?,?)";
    }


    public String getSelectConversationIdsSql() {
        return "SELECT DISTINCT conversation_id FROM chat_memory";
    }


    public String getDeleteMessagesSql() {
        return "DELETE FROM chat_memory WHERE conversation_id = ?";
    }
}
