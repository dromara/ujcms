package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 投票 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface VoteMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Vote bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Vote bean);

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
    Vote select(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Vote> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 投票
     *
     * @param id 投票ID
     * @return 更新条数
     */
    int cast(@Param("id") Integer id);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Integer siteId);
}