package com.lin.linagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lin.linagent.domain.KnowledgeDocumentMeta;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档元数据 Mapper
 */
@Mapper
public interface KnowledgeDocumentMetaMapper extends BaseMapper<KnowledgeDocumentMeta> {

    /**
     * 统计片段总数
     * @return 总数
     */
    Integer sumSectionCount();
}
