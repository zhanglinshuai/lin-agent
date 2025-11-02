-- auto-generated definition
create table chat_memory
(
    id              bigint auto_increment
        primary key,
    conversation_id varchar(255) not null comment '会话id',
    content         text         not null comment '消息内容',
    message_type    varchar(50)  not null comment '消息类型',
    create_time     timestamp    not null comment '创建时间'
)
    comment '存储消息';
