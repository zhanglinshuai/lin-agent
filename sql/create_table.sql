-- auto-generated definition
create table admin_log_entry
(
    id               varchar(64)                        not null
        primary key,
    level            varchar(16)                        not null comment '日志级别',
    category         varchar(64)                        not null comment '日志分类',
    summary          varchar(255)                       not null comment '日志摘要',
    detail           longtext                           null comment '日志详情',
    created_at       datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    created_at_label varchar(32)                        null comment '创建时间文本',
    operator         varchar(255)                       null comment '操作人'
)
    comment '管理后台日志表';

create index idx_admin_log_entry_category
    on admin_log_entry (category);

create index idx_admin_log_entry_created_at
    on admin_log_entry (created_at);

create index idx_admin_log_entry_level
    on admin_log_entry (level);

-- auto-generated definition
create table chat_memory
(
    id              bigint auto_increment
        primary key,
    conversation_id varchar(255)                        not null comment '会话id',
    content         longtext                            not null comment '消息内容',
    message_type    varchar(50)                         not null comment '消息类型',
    create_time     timestamp default CURRENT_TIMESTAMP not null comment '创建时间',
    user_id         varchar(255)                        null comment '当前会话的用户id',
    metadata        longtext                            null comment '元数据'
)
    comment '存储消息';

create index idx_chat_memory_conversation_id
    on chat_memory (conversation_id);

create index idx_chat_memory_create_time
    on chat_memory (create_time);

create index idx_chat_memory_user_id
    on chat_memory (user_id);

-- auto-generated definition
create table conversation_info
(
    id              bigint auto_increment
        primary key,
    conversation_id varchar(255)                           not null comment '会话id',
    user_id         varchar(255)                           not null comment '用户id',
    title           varchar(255) default '新对话'          not null comment '会话标题',
    mode            varchar(64)                            null comment '会话模式',
    tag             varchar(128)                           null comment '会话标签',
    pinned          tinyint      default 0                 not null comment '是否置顶',
    summary         varchar(255)                           null comment '会话摘要',
    last_message    varchar(512)                           null comment '最近消息',
    message_count   int          default 0                 not null comment '消息数量',
    create_time     datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    last_time       datetime     default CURRENT_TIMESTAMP not null comment '最近消息时间',
    update_time     datetime     default CURRENT_TIMESTAMP not null comment '更新时间',
    constraint uk_conversation_user
        unique (conversation_id, user_id)
)
    comment '会话信息表';

create index idx_conversation_last_time
    on conversation_info (last_time);

create index idx_conversation_mode
    on conversation_info (mode);

create index idx_conversation_pinned
    on conversation_info (pinned);

create index idx_conversation_user_id
    on conversation_info (user_id);
-- auto-generated definition
create table knowledge_document_meta
(
    file_name     varchar(255)                       not null comment '知识库文件名'
        primary key,
    title         varchar(255)                       null comment '知识库标题',
    file_size     bigint   default 0                 not null comment '文件大小',
    section_count int      default 0                 not null comment '片段数量',
    update_time   varchar(32)                        null comment '文档更新时间',
    synced_at     datetime default CURRENT_TIMESTAMP not null comment '最近同步时间'
)
    comment '知识库文档元数据表';

create index idx_knowledge_document_meta_synced_at
    on knowledge_document_meta (synced_at);-- auto-generated definition
create table knowledge_document_meta
(
    file_name     varchar(255)                       not null comment '知识库文件名'
        primary key,
    title         varchar(255)                       null comment '知识库标题',
    file_size     bigint   default 0                 not null comment '文件大小',
    section_count int      default 0                 not null comment '片段数量',
    update_time   varchar(32)                        null comment '文档更新时间',
    synced_at     datetime default CURRENT_TIMESTAMP not null comment '最近同步时间'
)
    comment '知识库文档元数据表';

-- auto-generated definition
create table user
(
    id               varchar(255)                       not null
        primary key,
    userName         varchar(255)                       null comment '用户名称',
    userPhone        varchar(255)                       null comment '用户手机号',
    verificationCode varchar(255)                       null comment '验证码',
    userPassword     varchar(255)                       null comment '用户密码',
    userAvatar       varchar(512)                       null comment '用户头像',
    userRole         int      default 0                 not null comment '用户角色：0-普通用户，1-管理员',
    createTime       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    isDelete         int      default 0                 not null comment '是否删除 1-删除 0-不删除',
    updateTime       datetime default CURRENT_TIMESTAMP not null comment '更新时间'
)
    comment '用户表';

create index idx_user_name
    on user (userName);

create index idx_user_role
    on user (userRole);

