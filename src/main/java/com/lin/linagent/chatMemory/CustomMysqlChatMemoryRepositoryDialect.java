package com.lin.linagent.chatMemory;

import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;

public class CustomMysqlChatMemoryRepositoryDialect implements JdbcChatMemoryRepositoryDialect {

    @Override
    public String getSelectMessagesSql() {
        return "SELECT content,message_type FROM chat_memory WHERE conversation_id = ? ORDER BY create_time";
    }

    @Override
    public String getInsertMessageSql() {
        return "INSERT INTO chat_memory (conversation_id,content,message_type,create_time) VALUES (?,?,?,?)";
    }

    @Override
    public String getSelectConversationIdsSql() {
        return "SELECT DISTINCT conversation_id FROM chat_memory";
    }

    @Override
    public String getDeleteMessagesSql() {
        return "DELETE FROM chat_memory WHERE conversation_id = ?";
    }
}
