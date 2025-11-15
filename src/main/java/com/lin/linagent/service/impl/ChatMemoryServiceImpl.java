package com.lin.linagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lin.linagent.domain.ChatMemory;
import com.lin.linagent.domain.User;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import com.lin.linagent.mapper.UserMapper;
import com.lin.linagent.service.ChatMemoryService;
import com.lin.linagent.mapper.ChatMemoryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
* @author zhanglinshuai
* @description 针对表【chat_memory(存储消息)】的数据库操作Service实现
* @createDate 2025-11-15 15:39:48
*/
@Service
public class ChatMemoryServiceImpl extends ServiceImpl<ChatMemoryMapper, ChatMemory>
    implements ChatMemoryService{
    
    @Resource
    private UserMapper userMapper;
    
    @Resource
    private ChatMemoryMapper chatMemoryMapper;
    
    

    @Override
    public List<ChatMemory> getUserConversationList(String username) {
        if(username.length()>10){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户名不符合规定");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userName",username);
        User user = userMapper.selectOne(queryWrapper);
        if(user==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在");
        }
        String id = user.getId();
        //根据用户的id，在chat_memory中查询用户id的conversationId
        List<ChatMemory> chatMemoryList = chatMemoryMapper.selectList(new QueryWrapper<ChatMemory>().eq("user_id", id));
        if(chatMemoryList.isEmpty()){
            return new ArrayList<>();
        }
        //按照创建时间进行排序
        chatMemoryList.sort(Comparator.comparing(ChatMemory::getCreate_time).reversed());

        return chatMemoryList;
    }

}




