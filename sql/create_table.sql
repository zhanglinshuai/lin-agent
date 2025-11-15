-- auto-generated definition
create table chat_memory
(
    id              bigint auto_increment
        primary key,
    conversation_id varchar(255) not null comment '会话id',
    content         text         not null comment '消息内容',
    message_type    varchar(50)  not null comment '消息类型',
    create_time     timestamp    not null comment '创建时间',
    user_id         varchar(255) null comment '当前会话的用户id',
    metadata        text         null comment '元数据'
)
    comment '存储消息';

create table user
(
    id               varchar(255)                       not null
        primary key,
    userName         varchar(255)                       null comment '用户名称',
    userPhone        varchar(255)                       null comment '用户手机号',
    verificationCode varchar(255)                       null comment '验证码',
    userPassword     varchar(255)                       null comment '用户密码',
    createTime       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    isDelete         int      default 0                 not null comment '是否删除 1-删除 0-不删除',
    updateTime       datetime default CURRENT_TIMESTAMP not null comment '更新时间'
);


