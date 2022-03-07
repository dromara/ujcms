package com.ujcms.core.mapper;

import com.ujcms.core.domain.ChannelGroup;
import com.ofwise.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 栏目与用户组关联 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ChannelGroupMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ChannelGroup bean);

    /**
     * 删除数据
     *
     * @param groupId   用户组ID
     * @param channelId 栏目ID
     * @return 删除条数
     */
    int delete(@Param("groupId") Integer groupId, @Param("channelId") Integer channelId);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<ChannelGroup> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据 栏目ID 删除数据
     *
     * @param channelId 栏目ID
     * @return 删除条数
     */
    int deleteByChannelId(@Param("channelId") Integer channelId);
}