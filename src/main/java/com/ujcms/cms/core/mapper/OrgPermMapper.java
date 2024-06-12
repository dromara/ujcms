package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.OrgPerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 组织权限 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface OrgPermMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(OrgPerm bean);

    /**
     * 根据主键获取数据
     *
     * @param orgId     组织ID
     * @param permOrgId 权限组织ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    OrgPerm select(@Param("orgId") Long orgId, @Param("permOrgId") Long permOrgId);

    /**
     * 根据 组织ID 查询 权限组织ID 列表
     *
     * @param orgId         组织ID
     * @param ancestorOrgId 祖先组织ID
     * @return 权限组织ID 列表
     */
    List<Long> listPermOrgByOrgId(@Param("orgId") Long orgId,
                                     @Nullable @Param("ancestorOrgId") Long ancestorOrgId);

    /**
     * 根据组织ID删除数据
     *
     * @param orgId 组织ID
     * @return 删除条数
     */
    int deleteByOrgId(@Param("orgId") Long orgId);

    /**
     * 根据 组织ID、权限祖先组织ID 删除数据
     *
     * @param orgId         组织ID
     * @param ancestorOrgId 权限祖先组织ID
     * @return 删除条数
     */
    int deleteByOrgIdAndAncestorOrgId(@Param("orgId") Long orgId,
                                      @Nullable @Param("ancestorOrgId") Long ancestorOrgId);

}