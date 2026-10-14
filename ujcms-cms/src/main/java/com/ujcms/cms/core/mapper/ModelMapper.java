package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.ModelUsage;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 模型 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ModelMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Model bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Model bean);

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
    Model select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Model> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 获取全局设置模型（ID: 1）
     *
     * @return 全局设置模型
     */
    @Nullable
    Model selectConfigModel();

    /**
     * 获取用户模型（ID: 2）
     *
     * @return 用户模型
     */
    @Nullable
    Model selectUserModel();

    /**
     * 根据站点ID删除模型
     *
     * @param siteId 站点ID
     * @return 删除的条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 模型使用统计（model-management M4）：每个被引用的模型一行；未被引用的模型不出现
     *
     * @param siteId 当前站点ID（统计站点之外的引用数）
     * @return 使用统计列表
     */
    List<ModelUsage> selectUsages(@Param("siteId") Long siteId);

    /**
     * 按来源三元组获取模型（种子导入幂等键，model-seeding D1）
     *
     * @param siteId   站点ID
     * @param origin   来源（如 theme:default）
     * @param originId 来源ID（种子文件内稳定ID）
     * @return 模型。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Model selectByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin,
                         @Param("originId") String originId);

    /**
     * 按来源列出站点的本源模型（移除/导出选择用）
     *
     * @param siteId 站点ID
     * @param origin 来源
     * @return 模型列表
     */
    List<Model> listByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 按来源统计站点的本源模型数（demoDataPresent 用）
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
     * 写入来源散列（R4：种子导入落库后按最终行状态计算）
     *
     * @param id         模型ID
     * @param originHash 来源散列
     * @return 更新条数
     */
    int updateOriginHash(@Param("id") Long id, @Param("originHash") String originHash);

    /**
     * 认领操作员行（theme-seed-export §4.2 adopt）：仅无来源标记的<b>本站私有</b>行——
     * 他源绝不认领（D3），全局模型绝不认领（model-seeding D1）。
     *
     * @param id         模型ID
     * @param origin     来源
     * @param originId   来源ID
     * @param originHash 来源散列
     * @return 更新条数（0 表示行已有来源、为全局行或不存在）
     */
    int adoptOrigin(@Param("id") Long id, @Param("origin") String origin,
                    @Param("originId") String originId, @Param("originHash") String originHash);
}