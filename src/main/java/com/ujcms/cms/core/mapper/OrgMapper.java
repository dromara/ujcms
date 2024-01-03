package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Org;
import com.ujcms.commons.db.tree.TreeEntityMapper;
import com.ujcms.commons.query.QueryInfo;
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
     * 根据主键获取引用对象（不包括关联对象属性）
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Org selectRefer(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo          查询条件
     * @param ancestorId         上级ID
     * @param isQueryHasChildren 是否查询包含子组织
     * @return 数据列表
     */
    List<Org> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                        @Nullable @Param("ancestorId") Integer ancestorId,
                        @Param("isQueryHasChildren") boolean isQueryHasChildren);

    /**
     * 根据父组织ID获取子组织列表
     *
     * @param parentId 父组织ID
     * @return 子组织列表
     */
    List<Org> listChildren(Integer parentId);
}