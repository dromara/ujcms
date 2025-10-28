package com.ujcms.cms.core.lucene;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import com.ujcms.cms.core.lucene.domain.EsArticle;

/**
 * 文章全文检索接口
 *
 * @author PONY
 */
public interface ArticleLucene {
    /**
     * 保存 EsArticle
     *
     * @param entity 实体对象
     */
    void save(EsArticle entity);

    /**
     * 更新 EsArticle
     *
     * @param entity 实体对象
     */
    void update(EsArticle entity);

    /**
     * 删除 EsArticle
     *
     * @param id 文章 ID
     */
    void deleteById(Long id);

    /**
     * 根据 站点 ID 删除索引
     *
     * @param siteId 站点 ID
     */
    void deleteBySiteId(Long siteId);

    /**
     * 删除整个索引
     */
    void deleteAll();

    /**
     * EsArticle 全文检索查询
     *
     * @param args     查询参数
     * @param pageable 分页
     * @return 匹配的数据列表
     */
    Page<EsArticle> findAll(ArticleLuceneArgs args, @Nullable Map<String, Object> queryMap, Pageable pageable);
}
