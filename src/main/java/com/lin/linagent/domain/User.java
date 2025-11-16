package com.lin.linagent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import java.util.Objects;

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
     * 用户头像
     */
    @TableField("userAvatar")
    private String userAvatar;

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


    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", userName='" + userName + '\'' +
                ", userPhone='" + userPhone + '\'' +
                ", verificationCode='" + verificationCode + '\'' +
                ", userAvatar='" + userAvatar + '\'' +
                ", userPassword='" + userPassword + '\'' +
                ", createTime=" + createTime +
                ", isDelete=" + isDelete +
                ", updateTime=" + updateTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(getId(), user.getId()) && Objects.equals(getUserName(), user.getUserName()) && Objects.equals(getUserPhone(), user.getUserPhone()) && Objects.equals(getVerificationCode(), user.getVerificationCode()) && Objects.equals(getUserAvatar(), user.getUserAvatar()) && Objects.equals(getUserPassword(), user.getUserPassword()) && Objects.equals(getCreateTime(), user.getCreateTime()) && Objects.equals(getIsDelete(), user.getIsDelete()) && Objects.equals(getUpdateTime(), user.getUpdateTime());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getUserName(), getUserPhone(), getVerificationCode(), getUserAvatar(), getUserPassword(), getCreateTime(), getIsDelete(), getUpdateTime());
    }
}