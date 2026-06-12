package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.ArticleStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 文章统计 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ArticleStatMapper {
    /**
     * 文章统计
     *
     * @param type   类型 (user: 按用户统计; org: 按组织统计; channel: 按栏目统计)
     * @param siteId 站点ID
     * @param begin  开始日期
     * @param end    介绍日期
     * @return 统计结果
     */
    List<ArticleStat> statBy(@Param("type") String type,
                             @Param("siteId") @Nullable Long siteId,
                             @Param("begin") @Nullable OffsetDateTime begin,
                             @Param("end") @Nullable OffsetDateTime end);
}