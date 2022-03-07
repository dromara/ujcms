package com.ujcms.core.lucene;

import com.ujcms.core.lucene.domain.EsArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;

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
    void deleteById(Integer id);

    /**
     * 根据 站点 ID 删除索引
     *
     * @param siteId 站点 ID
     */
    void deleteBySiteId(Integer siteId);

    /**
     * 删除整个索引
     */
    void deleteAll();

    /**
     * EsArticle 全文检索查询
     *
     * @param q                   查询字符串
     * @param isIncludeBody       是否查询 Body
     * @param isOperatorAnd       是否每个分词都必须匹配
     * @param siteId              站点ID
     * @param isIncludeSubSite    是否包含子站点
     * @param channelId           栏目ID
     * @param isIncludeSubChannel 是否包含子詹姆
     * @param beginPublishDate    起始发布日期
     * @param endPublishDate      截止发布日期
     * @param isWithImage         是否有图片
     * @param excludeIds          需排除的文章ID
     * @param k1                  关键字1
     * @param k2                  关键字2
     * @param k3                  关键字3
     * @param k4                  关键字4
     * @param k5                  关键字5
     * @param k6                  关键字6
     * @param k7                  关键字7
     * @param k8                  关键字8
     * @param s1                  字符串1
     * @param s2                  字符串2
     * @param s3                  字符串3
     * @param s4                  字符串4
     * @param s5                  字符串5
     * @param s6                  字符串6
     * @param b1                  布尔值1
     * @param b2                  布尔值2
     * @param b3                  布尔值3
     * @param b4                  布尔值4
     * @param queryMap            查询 Map。包括 i1..i6, n1..n4, d1..d4
     * @param pageable            分页
     * @return 匹配的数据列表
     */
    Page<EsArticle> findAll(@Nullable String q, boolean isIncludeBody, boolean isOperatorAnd,
                            @Nullable Integer siteId, boolean isIncludeSubSite,
                            @Nullable Integer channelId, boolean isIncludeSubChannel,
                            @Nullable OffsetDateTime beginPublishDate, @Nullable OffsetDateTime endPublishDate,
                            @Nullable Boolean isWithImage, @Nullable Collection<Integer> excludeIds,
                            @Nullable String k1, @Nullable String k2, @Nullable String k3, @Nullable String k4,
                            @Nullable String k5, @Nullable String k6, @Nullable String k7, @Nullable String k8,
                            @Nullable String s1, @Nullable String s2, @Nullable String s3, @Nullable String s4,
                            @Nullable String s5, @Nullable String s6,
                            @Nullable Boolean b1, @Nullable Boolean b2,
                            @Nullable Boolean b3, @Nullable Boolean b4,
                            @Nullable Map<String, Object> queryMap, Pageable pageable);
}
