package com.lin.linagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lin.linagent.domain.AdminLogEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理日志 Mapper
 */
@Mapper
public interface AdminLogEntryMapper extends BaseMapper<AdminLogEntry> {
}
