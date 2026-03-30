package com.lin.linagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lin.linagent.domain.KnowledgeDocumentMeta;
import com.lin.linagent.domain.dto.KnowledgeDocumentVO;
import com.lin.linagent.mapper.KnowledgeDocumentMetaMapper;
import com.lin.linagent.service.KnowledgeDocumentMetaService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 知识库文档元数据服务实现
 */
@Service
public class KnowledgeDocumentMetaServiceImpl extends ServiceImpl<KnowledgeDocumentMetaMapper, KnowledgeDocumentMeta>
        implements KnowledgeDocumentMetaService {

    @Override
    public void syncDocumentMeta(KnowledgeDocumentVO documentVO) {
        if (documentVO == null || StringUtils.isBlank(documentVO.getFileName())) {
            return;
        }
        KnowledgeDocumentMeta meta = new KnowledgeDocumentMeta();
        meta.setFileName(StringUtils.trimToEmpty(documentVO.getFileName()));
        meta.setTitle(StringUtils.trimToEmpty(documentVO.getTitle()));
        meta.setFileSize(documentVO.getSize() == null ? 0L : documentVO.getSize());
        meta.setSectionCount(documentVO.getSectionCount() == null ? 0 : documentVO.getSectionCount());
        meta.setUpdateTime(StringUtils.trimToEmpty(documentVO.getUpdateTime()));
        meta.setSyncedAt(new Date());
        this.saveOrUpdate(meta);
    }

    @Override
    public void replaceAllDocumentMeta(List<KnowledgeDocumentVO> documentList) {
        this.remove(new QueryWrapper<>());
        if (documentList == null || documentList.isEmpty()) {
            return;
        }
        for (KnowledgeDocumentVO documentVO : documentList) {
            syncDocumentMeta(documentVO);
        }
    }

    @Override
    public void removeByFileName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return;
        }
        this.removeById(StringUtils.trimToEmpty(fileName));
    }

    @Override
    public int countDocumentTotal() {
        return Math.toIntExact(this.count());
    }

    @Override
    public int countSectionTotal() {
        Integer count = this.baseMapper.sumSectionCount();
        return count == null ? 0 : count;
    }
}
