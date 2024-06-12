package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.OrgChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 组织栏目权限 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface OrgChannelMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(OrgChannel bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(OrgChannel bean);

    /**
     * 根据主键获取数据
     *
     * @param orgId     组织ID
     * @param channelId 栏目ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    OrgChannel select(@Param("orgId") Long orgId, @Param("channelId") Long channelId);

    /**
     * 根据 组织ID 查询栏目ID列表
     *
     * @param orgId 组织ID
     * @param siteId 站点ID
     * @return 栏目ID列表
     */
    List<Long> listChannelByOrgId(@Param("orgId") Long orgId, @Nullable @Param("siteId") Long siteId);

    /**
     * 根据 栏目ID 查询组织ID列表
     *
     * @param channelId 栏目ID
     * @param siteId    站点ID
     * @return 角色ID列表
     */
    List<Long> listOrgByChannelId(@Param("channelId") Long channelId, @Nullable @Param("siteId") Long siteId);

    /**
     * 根据 组织ID 删除数据
     *
     * @param orgId  组织ID
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteByOrgId(@Param("orgId") Long orgId, @Nullable @Param("siteId") Long siteId);

    /**
     * 根据 栏目ID 删除数据
     *
     * @param channelId 栏目ID
     * @return 删除条数
     */
    int deleteByChannelId(@Param("channelId") Long channelId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}