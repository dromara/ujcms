package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.RoleChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色栏目权限 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface RoleChannelMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(RoleChannel bean);

    /**
     * 删除数据
     *
     * @param channelId 栏目ID
     * @param roleId    角色ID
     * @return 删除条数
     */
    int delete(@Param("channelId") Long channelId, @Param("roleId") Long roleId);

    /**
     * 根据 角色ID 查询栏目ID列表
     *
     * @param roleId 角色ID
     * @param siteId 站点ID
     * @return 栏目ID列表
     */
    List<Long> listChannelByRoleId(@Param("roleId") Long roleId, @Nullable @Param("siteId") Long siteId);

    /**
     * 根据 栏目ID 查询角色ID列表
     *
     * @param channelId 栏目ID
     * @param siteId    站点ID
     * @return 角色ID列表
     */
    List<Long> listRoleByChannelId(@Param("channelId") Long channelId, @Nullable @Param("siteId") Long siteId);

    /**
     * 根据 栏目ID 删除数据
     *
     * @param channelId 栏目ID
     * @return 删除条数
     */
    int deleteByChannelId(@Param("channelId") Long channelId);

    /**
     * 根据 角色ID 删除数据
     *
     * @param roleId 角色ID
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteByRoleId(@Param("roleId") Long roleId, @Nullable @Param("siteId") Long siteId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}