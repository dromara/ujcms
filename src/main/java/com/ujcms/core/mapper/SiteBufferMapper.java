package com.ujcms.core.mapper;

import com.ujcms.core.domain.SiteBuffer;
import com.ofwise.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

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
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(SiteBuffer bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(SiteBuffer bean);

    /**
     * 删除数据
     *
     * @param id 主键ID
     * @return 删除条数
     */
    int delete(Integer id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    SiteBuffer select(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<SiteBuffer> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 更新站点浏览次数
     *
     * @param id          栏目ID
     * @param viewsToPlus 浏览次数
     * @return 如果站点不存在，则返回{@code 0}；否则返回{@code 1}。
     */
    int updateViews(@Param("id") Integer id, @Param("viewsToPlus") int viewsToPlus);
}