package com.ujcms.core.mapper;

import com.ujcms.core.domain.Org;
import com.ofwise.util.query.QueryInfo;
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
public interface OrgMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Org bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Org bean);

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
    Org select(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Org> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据父组织ID获取子组织列表
     *
     * @param parentId 父组织ID
     * @return 子组织列表
     */
    List<Org> listChildren(Integer parentId);

    /**
     * 更新树形结构深度
     *
     * @param id    节点ID
     * @param depth 深度
     * @return 更新条数
     */
    int updateDepth(@Param("id") Integer id, @Param("depth") short depth);
}