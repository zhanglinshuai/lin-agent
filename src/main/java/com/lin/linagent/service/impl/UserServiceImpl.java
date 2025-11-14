package com.lin.linagent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lin.linagent.domain.User;
import com.lin.linagent.service.UserService;
import com.lin.linagent.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
* @author zhanglinshuai
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-11-14 23:46:12
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

}




