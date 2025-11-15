package com.lin.linagent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 
 * @TableName user
 */
@TableName(value ="user")
@Data
public class User {
    /**
     * 
     */
    @TableId
    private String id;

    /**
     * 用户名称
     */
    @TableField("userName")
    private String userName;

    /**
     * 用户手机号
     */
    @TableField("userPhone")
    private String userPhone;

    /**
     * 验证码
     */
    @TableField("verificationCode")
    private String verificationCode;

    /**
     * 用户密码
     */
    @TableField("userPassword")
    private String userPassword;

    /**
     * 创建时间
     */
    @TableField("createTime")
    private Date createTime;

    /**
     * 是否删除 1-删除 0-不删除
     */
    @TableField("isDelete")
    private Integer isDelete;

    /**
     * 更新时间
     */
    @TableField("updateTime")
    private Date updateTime;
}