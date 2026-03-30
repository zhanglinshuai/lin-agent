package com.lin.linagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lin.linagent.domain.KnowledgeDocumentMeta;
import com.lin.linagent.domain.dto.KnowledgeDocumentVO;

import java.util.List;

/**
 * 知识库文档元数据服务
 */
public interface KnowledgeDocumentMetaService extends IService<KnowledgeDocumentMeta> {

    /**
     * 同步单个文档元数据
     * @param documentVO 文档信息
     */
    void syncDocumentMeta(KnowledgeDocumentVO documentVO);

    /**
     * 全量替换元数据
     * @param documentList 文档列表
     */
    void replaceAllDocumentMeta(List<KnowledgeDocumentVO> documentList);

    /**
     * 删除指定文档元数据
     * @param fileName 文件名
     */
    void removeByFileName(String fileName);

    /**
     * 统计文档数量
     * @return 数量
     */
    int countDocumentTotal();

    /**
     * 统计片段总数
     * @return 数量
     */
    int countSectionTotal();
}
