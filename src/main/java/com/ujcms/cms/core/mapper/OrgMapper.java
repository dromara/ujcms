package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Org;
import com.ujcms.util.db.tree.TreeEntityMapper;
import com.ujcms.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 组织 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface OrgMapper extends TreeEntityMapper<Org> {
    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @param parentId  上级ID
     * @return 数据列表
     */
    List<Org> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                        @Nullable @Param("parentId") Integer parentId);

    /**
     * 根据父组织ID获取子组织列表
     *
     * @param parentId 父组织ID
     * @return 子组织列表
     */
    List<Org> listChildren(Integer parentId);
}