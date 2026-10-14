package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.DictType;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 字典类型 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface DictTypeMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(DictType bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(DictType bean);

    /**
     * 删除数据
     *
     * @param id 主键ID
     * @return 删除条数
     */
    int delete(Long id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    DictType select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo         查询条件
     * @param isQueryEntryCount 是否同时统计各类型的条目数
     * @return 数据列表
     */
    List<DictType> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                             @Param("isQueryEntryCount") boolean isQueryEntryCount);

    /**
     * 根据别名和站点ID计数
     *
     * @param alias  别名
     * @param siteId 站点ID
     * @return 数据条数
     */
    int existsByAlias(@Param("alias") String alias, @Param("siteId") @Nullable Long siteId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 按来源三元组获取字典类型（种子导入幂等键，model-seeding D8）
     *
     * @param siteId   站点ID
     * @param origin   来源（如 theme:default）
     * @param originId 来源ID（种子文件内稳定ID）
     * @return 字典类型。没有找到数据，则返回 {@code null}
     */
    @Nullable
    DictType selectByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin,
                            @Param("originId") String originId);

    /**
     * 按来源列出站点的本源字典类型（移除/导出选择用）
     *
     * @param siteId 站点ID
     * @param origin 来源
     * @return 字典类型列表
     */
    List<DictType> listByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 按来源统计站点的本源字典类型数（demoDataPresent 用）
     *
     * @param siteId 站点ID
     * @param origin 来源
     * @return 行数
     */
    int countByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 清除站点内某来源的全部来源标记（卸载 KEEP/ARCHIVE、移除守卫不过时转普通操作员行）
     *
     * @param siteId 站点ID
     * @param origin 来源
     * @return 更新条数
     */
    int clearOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 写入来源散列（R4：种子导入落库后按最终行状态——含条目——计算）
     *
     * @param id         字典类型ID
     * @param originHash 来源散列
     * @return 更新条数
     */
    int updateOriginHash(@Param("id") Long id, @Param("originHash") String originHash);

    /**
     * 认领操作员行（theme-seed-export §4.2 adopt）：仅无来源标记的<b>本站私有非 sys</b> 行——
     * 他源绝不认领（D3），全局/sys 字典绝不认领（model-seeding D8）。
     *
     * @param id         字典类型ID
     * @param origin     来源
     * @param originId   来源ID
     * @param originHash 来源散列
     * @return 更新条数（0 表示行已有来源、为全局/sys 行或不存在）
     */
    int adoptOrigin(@Param("id") Long id, @Param("origin") String origin,
                    @Param("originId") String originId, @Param("originHash") String originHash);
}