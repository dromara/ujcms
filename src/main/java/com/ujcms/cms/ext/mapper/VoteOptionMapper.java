package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.VoteOption;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 投票选项 实体类
 *
 * @author PONY
 */
@Mapper
@Repository
public interface VoteOptionMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(VoteOption bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(VoteOption bean);

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
    VoteOption select(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<VoteOption> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据投票ID获取列表
     *
     * @param voteId 投票ID
     * @return 数据列表
     */
    List<VoteOption> listByVoteId(Long voteId);

    /**
     * 投票
     *
     * @param voteId    投票ID
     * @param optionIds 选项ID列表
     * @return 更新条数
     */
    int cast(@Param("voteId") Long voteId, @Param("optionIds") List<Long> optionIds);

    /**
     * 根据投票ID删除数据
     *
     * @param voteId 投票ID
     * @param ids    不包含的id
     * @return 删除条数
     */
    int deleteByVoteId(@Param("voteId") Long voteId, @Param("ids") List<Long> ids);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(@Param("siteId") Long siteId);
}