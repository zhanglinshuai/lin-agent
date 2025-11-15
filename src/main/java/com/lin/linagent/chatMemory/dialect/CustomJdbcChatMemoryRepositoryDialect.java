package com.lin.linagent.chatMemory.dialect;

import javax.sql.DataSource;

public interface CustomJdbcChatMemoryRepositoryDialect {
    /**
     * Returns the SQL to fetch messages for a conversation, ordered by timestamp, with
     * limit.
     */
    String getSelectMessagesSql();

    /**
     * Returns the SQL to insert a message.
     */
    String getInsertMessageSql();

    /**
     * Returns the SQL to fetch conversation IDs.
     */
    String getSelectConversationIdsSql();

    /**
     * Returns the SQL to delete all messages for a conversation.
     */
    String getDeleteMessagesSql();

    /**
     * Optionally, dialect can provide more advanced SQL as needed.
     */

    /**
     * Detects the dialect from the DataSource or JDBC URL.
     */
    static CustomJdbcChatMemoryRepositoryDialect from(DataSource dataSource) {
        // Simple detection (could be improved)
        try {
            String url = dataSource.getConnection().getMetaData().getURL().toLowerCase();
            if (url.contains("postgresql")) {
                return new CustomPostgresChatMemoryRepositoryDialect();
            }
            if (url.contains("mysql")) {
                return new CustomMysqlJdbcChatMemoryRepositoryDialect();
            }
            if (url.contains("mariadb")) {
                return new CustomMysqlJdbcChatMemoryRepositoryDialect();
            }
            if (url.contains("sqlserver")) {
                return new SqlServerChatMemoryRepositoryDialect();
            }
            if (url.contains("hsqldb")) {
                return new CustomHsqldbJdbcChatMemoryRepositoryDialect();
            }
            // Add more as needed
        }
        catch (Exception ignored) {
        }
        return new CustomPostgresChatMemoryRepositoryDialect(); // default
    }
}
