package com.lin.linagent.chatMemory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.linagent.chatMemory.dialect.CustomJdbcChatMemoryRepositoryDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自定义的jdbcChatMemoryRepository
 */

public final class CustomJdbcChatMemoryRepository implements ChatMemoryRepository {

    /**
     * 会话同步回调
     */
    @FunctionalInterface
    public interface ConversationSyncHandler {
        void sync(String conversationId, String userId);
    }

    private final JdbcTemplate jdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    private final CustomJdbcChatMemoryRepositoryDialect dialect;

    private final ConversationSyncHandler conversationSyncHandler;

    private CustomJdbcChatMemoryRepository(JdbcTemplate jdbcTemplate, CustomJdbcChatMemoryRepositoryDialect dialect,
                                           PlatformTransactionManager txManager,
                                           ConversationSyncHandler conversationSyncHandler) {
        Assert.notNull(jdbcTemplate, "jdbcTemplate cannot be null");
        Assert.notNull(dialect, "dialect cannot be null");
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
        this.conversationSyncHandler = conversationSyncHandler;
        this.transactionTemplate = new TransactionTemplate(
                txManager != null ? txManager : new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    @Override
    public List<String> findConversationIds() {
        return this.jdbcTemplate.queryForList(dialect.getSelectConversationIdsSql(), String.class);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        return this.jdbcTemplate.query(this.dialect.getSelectMessagesSql(), new MessageRowMapper(), conversationId);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Message message = messages.get(0);
        Map<String, Object> metadata = message.getMetadata();
        String userId = (String)metadata.get("userId");
        this.saveAll(conversationId, messages, userId);
    }
    public void saveAll(String conversationId, List<Message> messages,String userId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");
        this.transactionTemplate.execute(status -> {
            deleteByConversationId(conversationId);
            this.jdbcTemplate.batchUpdate(this.dialect.getInsertMessageSql(),
                    new AddBatchPreparedStatement(conversationId, messages,userId));
            return null;
        });
        if (this.conversationSyncHandler != null) {
            this.conversationSyncHandler.sync(conversationId, userId);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        this.jdbcTemplate.update(this.dialect.getDeleteMessagesSql(), conversationId);
    }

    public static CustomJdbcChatMemoryRepository.Builder builder() {
        return new CustomJdbcChatMemoryRepository.Builder();
    }

    private record AddBatchPreparedStatement(String conversationId, List<Message> messages,
                                             AtomicLong instantSeq,String userId) implements BatchPreparedStatementSetter {

        private AddBatchPreparedStatement(String conversationId, List<Message> messages,String userId) {
            this(conversationId, messages, new AtomicLong(Instant.now().toEpochMilli()),userId);
        }

        @Override
        public void setValues(PreparedStatement ps, int i) throws SQLException {
            var message = this.messages.get(i);
            Map<String, Object> metadata = message.getMetadata();
            ps.setString(1, this.conversationId);
            ps.setString(2, message.getText());
            ps.setString(3, message.getMessageType().name());
            ps.setTimestamp(4, new Timestamp(this.instantSeq.getAndIncrement()));
            ps.setString(5, this.userId);
            Map<String,Object> setMetadata = new HashMap<>();
            setMetadata.put("userId", this.userId);
            setMetadata.putAll(metadata);
            try {
                String metadataJson  = new ObjectMapper().writeValueAsString(setMetadata);
                ps.setString(6,metadataJson);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int getBatchSize() {
            return this.messages.size();
        }
    }

    private static class MessageRowMapper implements RowMapper<Message> {

        @Override
        @Nullable
        public Message mapRow(ResultSet rs, int i) throws SQLException {
            var content = rs.getString(1);
            var type = MessageType.valueOf(rs.getString(2));
            String metadata = rs.getString(3);
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                Map<String, Object> map = objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {
                });
                String userId = (String) map.get("userId");
                String messageType = (String) map.get("messageType");
                String title = (String) map.get("title");
                String mode = (String) map.get("mode");
                return switch (type) {
                    case USER -> {
                        UserMessage userMessage = new UserMessage(content);
                        Map<String, Object> userMessageMetadata = userMessage.getMetadata();
                        userMessageMetadata.put("userId",userId);
                        userMessageMetadata.put("messageType",messageType);
                        userMessageMetadata.put("title",title);
                        userMessageMetadata.put("mode",mode);
                        yield userMessage;
                    }
                    case ASSISTANT -> new AssistantMessage(content);
                    case SYSTEM -> new SystemMessage(content);
                    // The content is always stored empty for ToolResponseMessages.
                    // If we want to capture the actual content, we need to extend
                    // AddBatchPreparedStatement to support it.
                    case TOOL -> new ToolResponseMessage(List.of());
                };
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public static final class Builder {

        private JdbcTemplate jdbcTemplate;

        private CustomJdbcChatMemoryRepositoryDialect dialect;

        private DataSource dataSource;

        private PlatformTransactionManager platformTransactionManager;

        private ConversationSyncHandler conversationSyncHandler;

        private static final Logger logger = LoggerFactory.getLogger(CustomJdbcChatMemoryRepository.Builder.class);

        private Builder() {
        }

        public Builder jdbcTemplate(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
            return this;
        }

        public Builder dialect(CustomJdbcChatMemoryRepositoryDialect dialect) {
            this.dialect = dialect;
            return this;
        }

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder transactionManager(PlatformTransactionManager txManager) {
            this.platformTransactionManager = txManager;
            return this;
        }

        public Builder conversationSyncHandler(ConversationSyncHandler conversationSyncHandler) {
            this.conversationSyncHandler = conversationSyncHandler;
            return this;
        }

        public CustomJdbcChatMemoryRepository build() {
            DataSource effectiveDataSource = resolveDataSource();
            CustomJdbcChatMemoryRepositoryDialect effectiveDialect = resolveDialect(effectiveDataSource);
            return new CustomJdbcChatMemoryRepository(resolveJdbcTemplate(), effectiveDialect,
                    this.platformTransactionManager, this.conversationSyncHandler);
        }

        private JdbcTemplate resolveJdbcTemplate() {
            if (this.jdbcTemplate != null) {
                return this.jdbcTemplate;
            }
            if (this.dataSource != null) {
                return new JdbcTemplate(this.dataSource);
            }
            throw new IllegalArgumentException("DataSource must be set (either via dataSource() or jdbcTemplate())");
        }

        private DataSource resolveDataSource() {
            if (this.dataSource != null) {
                return this.dataSource;
            }
            if (this.jdbcTemplate != null && this.jdbcTemplate.getDataSource() != null) {
                return this.jdbcTemplate.getDataSource();
            }
            throw new IllegalArgumentException("DataSource must be set (either via dataSource() or jdbcTemplate())");
        }

        private CustomJdbcChatMemoryRepositoryDialect resolveDialect(DataSource dataSource) {
            if (this.dialect == null) {
                try {
                    return CustomJdbcChatMemoryRepositoryDialect.from(dataSource);
                } catch (Exception ex) {
                    throw new IllegalStateException("Could not detect dialect from datasource", ex);
                }
            } else {
                warnIfDialectMismatch(dataSource, this.dialect);
                return this.dialect;
            }
        }

        /**
         * Logs a warning if the explicitly set dialect differs from the dialect detected
         * from the DataSource.
         */
        private void warnIfDialectMismatch(DataSource dataSource, CustomJdbcChatMemoryRepositoryDialect explicitDialect) {
            try {
                JdbcChatMemoryRepositoryDialect detected = JdbcChatMemoryRepositoryDialect.from(dataSource);
                if (!detected.getClass().equals(explicitDialect.getClass())) {
                    logger.warn("Explicitly set dialect {} will be used instead of detected dialect {} from datasource",
                            explicitDialect.getClass().getSimpleName(), detected.getClass().getSimpleName());
                }
            } catch (Exception ex) {
                logger.debug("Could not detect dialect from datasource", ex);
            }
        }

    }
}
