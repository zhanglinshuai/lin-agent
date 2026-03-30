package com.lin.linagent.mapper;

import com.lin.linagent.domain.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author zhanglinshuai
* @description 针对表【user】的数据库操作Mapper
* @createDate 2025-11-14 23:46:12
* @Entity generator.domain.User
*/
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 统计正常用户数量
     * @return 数量
     */
    Long countActiveUsers();
}




