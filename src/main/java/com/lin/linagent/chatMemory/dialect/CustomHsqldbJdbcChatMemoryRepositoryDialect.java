package com.lin.linagent.chatMemory.dialect;

public class CustomHsqldbJdbcChatMemoryRepositoryDialect implements CustomJdbcChatMemoryRepositoryDialect {

	@Override
	public String getSelectMessagesSql() {
		return "SELECT content, type FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ? ORDER BY timestamp ASC";
	}

	@Override
	public String getInsertMessageSql() {
		return "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, timestamp) VALUES (?, ?, ?, ?)";
	}

	@Override
	public String getDeleteMessagesSql() {
		return "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?";
	}

	@Override
	public String getSelectConversationIdsSql() {
		return "SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY";
	}

}