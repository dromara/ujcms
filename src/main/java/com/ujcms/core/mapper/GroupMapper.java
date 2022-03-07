package com.ujcms.core.mapper;

import com.ujcms.core.domain.Group;
import com.ujcms.core.domain.Role;
import com.ofwise.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户组 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface GroupMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Group bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Group bean);

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
    Group select(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Group> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据栏目ID获取列表
     *
     * @param channelId 栏目ID
     * @return 数据列表
     */
    List<Role> listByChannelId(Integer channelId);
}