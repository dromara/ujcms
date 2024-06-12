package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.SiteBuffer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 站点缓冲 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SiteBufferMapper {
    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(SiteBuffer bean);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    SiteBuffer select(Long id);

    /**
     * 批量更新浏览次数
     *
     * @param list 待更新列表
     * @return 更新条数
     */
    int updateBatch(List<SiteBuffer> list);

    /**
     * 更新浏览统计
     *
     * @param yesterday 如果出现最高访问量，此日期作为最高访问量的日期。应为昨天的日期。
     * @return 更新条数
     */
    int updateStat(@Param("yesterday") OffsetDateTime yesterday);
}