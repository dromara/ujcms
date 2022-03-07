package com.ujcms.core.mapper;

import com.ujcms.core.domain.Site;
import com.ofwise.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 站点 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SiteMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Site bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Site bean);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    int delete(Integer id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Site select(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @param customsCondition 自定义字段查询条件
     * @return 数据列表
     */
    List<Site> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                         @Nullable @Param("customsCondition") List<QueryInfo.WhereCondition> customsCondition);

    /**
     * 根据父站点ID获取子站点列表
     *
     * @param parentId 父站点ID
     * @return 子站点列表
     */
    List<Site> listChildren(Integer parentId);

    /**
     * 根据域名查找站点
     *
     * @param domain 域名
     * @return 站点
     */
    @Nullable
    Site findByDomain(String domain);

    /**
     * 根据子目录查找站点
     *
     * @param subDir 别名
     * @return 站点
     */
    @Nullable
    Site findBySubDir(String subDir);

    /**
     * 更新树形结构深度
     *
     * @param id    节点ID
     * @param depth 深度
     * @return 更新条数
     */
    int updateDepth(@Param("id") Integer id, @Param("depth") short depth);
}