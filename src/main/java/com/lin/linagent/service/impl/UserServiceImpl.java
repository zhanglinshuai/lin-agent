package com.lin.linagent.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lin.linagent.contant.CommonVariables;
import com.lin.linagent.domain.User;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import com.lin.linagent.service.UserService;
import com.lin.linagent.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.lin.linagent.contant.CommonVariables.UPLOAD_PATH;

/**
* @author zhanglinshuai
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-11-14 23:46:12
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Resource
    private UserMapper userMapper;


    @Override
    public String userRegister(String username, String password, String checkPassword) {
        if(StringUtils.isAnyBlank(username,password,checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户名或密码不能为空");
        }
        username = username.trim();
        if(username.length()>10){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户名过长");
        }
        if(password.length()<6 || checkPassword.length()<6){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码过短");
        }
        if(!password.equals(checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"两次输入密码不一致");
        }
        //用户名是否重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userName",username);
        Long count = userMapper.selectCount(queryWrapper);
        if(count>0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户名已存在");
        }
        //对密码进行加密
        String encryptedPassword = getEncryptedPassword(password);
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUserName(username);
        user.setUserPassword(encryptedPassword);
        int insert = userMapper.insert(user);
        if(insert!=1){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"插入数据失败");
        }
        return user.getId();
    }

    @Override
    public String userLogin(String username, String password) {
        if(StringUtils.isAnyBlank(username,password)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号或密码为空");
        }
        username = username.trim();
        if(username.length()>10){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户名过长");
        }
        if(password.length()<6){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码过短");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userName",username);
        queryWrapper.eq("userPassword",getEncryptedPassword(password));
        User user = this.getOne(queryWrapper);
        if(user==null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"用户不存在，请先去注册");
        }

        return user.getId();
    }

    @Override
    public User getUserInfo(String userId) {
        if(userId==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id",userId);
        User user = this.getOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在");
        }

        return user;
    }

    @Override
    public User updateUserInfo(User newUser) {
        if(newUser==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"新用户信息为空");
        }
        String userId = newUser.getId();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id",userId);
        User user = userMapper.selectOne(queryWrapper);
        if(user==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在");
        }
        //判断前后用户信息是否相同，如果相同就不用更新直接返回就可以
        if(newUser.equals(user)){
            return user;
        }
        int update = userMapper.updateById(newUser);
        if (update != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"更新失败");
        }
        return newUser;
    }

    @Override
    public String uploadAvatar(MultipartFile file,String userId) {
        if(file.isEmpty()){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"上传文件为空");
        }
        String contentType = file.getContentType();
        if(contentType==null || !contentType.startsWith("image/")){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"上传的文件只能是图片类型");
        }
        // 3. 生成文件名：userId + UUID
        String suffix = Objects.requireNonNull(file.getOriginalFilename())
                .substring(file.getOriginalFilename().lastIndexOf("."));

        String fileName = "avatar_" + userId + "_" + UUID.randomUUID() + suffix;

        // 4. 创建保存目录
        File dir = new File(UPLOAD_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File dest = new File(dir, fileName);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            throw new RuntimeException("头像上传失败",e);
        }
        String fileUrl = UPLOAD_PATH + fileName;
        User user = userMapper.selectById(userId);
        if(user==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在");
        }
        //设置avatar字段
        String publicPath = "/static/avatar/" + fileName;
        user.setUserAvatar(publicPath);
        userMapper.updateById(user);
        return publicPath;
    }


    /**
     * 对密码进行加密
     * @param password
     * @return
     */
    public String getEncryptedPassword(String password){
        return DigestUtils.md5DigestAsHex((CommonVariables.ENCRYPT_PASSWORD+password).getBytes());
    }
}




