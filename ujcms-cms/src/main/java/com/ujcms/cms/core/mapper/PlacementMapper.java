package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Placement;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 投放 Mapper（页面构成）
 *
 * @author PONY
 */
@Mapper
@Repository
public interface PlacementMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Placement bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Placement bean);

    /**
     * 删除数据
     *
     * @param id 投放ID
     * @return 删除条数
     */
    int delete(Long id);

    /**
     * 删除站点全部投放行（站点删除级联，docs/spec/site-delete-cascade/plan.md）
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 获取数据
     *
     * @param id 投放ID
     * @return 实体对象
     */
    @Nullable
    Placement select(Long id);

    /**
     * 查询数据列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Placement> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 页面内全部投放（按插槽名排序）
     *
     * @param siteId  站点ID
     * @param pageRef 页面清单名称
     * @return 投放列表
     */
    List<Placement> listByPage(@Param("siteId") Long siteId, @Param("pageRef") String pageRef);

    /**
     * 站点内全部投放（页面目录的填充状态统计用）
     *
     * @param siteId 站点ID
     * @return 投放列表
     */
    List<Placement> listBySiteId(Long siteId);

    /**
     * 按来源标记获取投放（种子导入幂等：select-then-insert，§6.3）。
     * 站点限定：{@code origin_} 不含站点，同名主题可装于多个站点，不限定会命中他站行。
     *
     * <p>同一 {@code (site, origin, originId)} 可短暂对应两行：区域草稿保存对已发布面板写时复制
     * （visual-builder shell-polish D20），副本携带同一来源三元组直到发布/丢弃清理其一。按 ID 升序
     * 取首行 = 最早创建的（已发布）那行，种子同步/重置在此窗口内不再因多行结果失败。
     *
     * @param siteId   站点ID
     * @param origin   来源（{@code theme:<themeName>}）
     * @param originId  种子文件内的稳定ID
     * @return 实体对象
     */
    @Nullable
    default Placement selectByOrigin(Long siteId, String origin, String originId) {
        List<Placement> rows = listByOriginId(siteId, origin, originId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 按来源标记获取投放列表（ID 升序；见 {@link #selectByOrigin}）
     *
     * @param siteId   站点ID
     * @param origin   来源（{@code theme:<themeName>}）
     * @param originId  种子文件内的稳定ID
     * @return 投放列表
     */
    List<Placement> listByOriginId(@Param("siteId") Long siteId, @Param("origin") String origin,
                                   @Param("originId") String originId);

    /**
     * 按来源标记获取本站投放列表（卸载数据策略：归档 / 删除主题来源投放）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 投放列表
     */
    List<Placement> listByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 按来源标记统计本站投放数（影响报告 {@code demoDataPresent}，§6.2）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 行数
     */
    int countByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 清除本站来源标记（卸载“保留”策略：主题来源投放转为普通操作员投放）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 更新条数
     */
    int clearOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 清除单行来源三元组（种子重置 D22：本源面板已被操作员移至他处——重置在种子位置重建新面板，
     * 被移走的行剥离来源转为操作员投放，绝不搬回、绝不产生 {@code (origin, originId)} 重复）
     *
     * @param id 投放ID
     * @return 更新条数
     */
    int clearOriginById(Long id);

    /**
     * 写入来源散列（R4：种子导入落库后，以持久化的最终行状态计算——服务层保存会清洗板块字段值，
     * 散列必须与行内实际存储一致才能据以重导出比对）
     *
     * @param id         投放ID
     * @param originHash 种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int updateOriginHash(@Param("id") Long id, @Param("originHash") String originHash);

    /**
     * 认领操作员行（theme-seed-export §4.2 adopt 模式，D2 唯一豁免）：写入铸造的来源三元组。
     * 仅无来源标记的行可认领（SQL 内附带条件）——他源行绝不认领（D3），并发下亦然。
     *
     * @param id         投放ID
     * @param origin     来源（{@code theme:<themeName>}）
     * @param originId   位置合成 originId（{@code <page>#<region>#<rowId>#<col>#<panel>}）
     * @param originHash 当前种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int adoptOrigin(@Param("id") Long id, @Param("origin") String origin,
                    @Param("originId") String originId, @Param("originHash") String originHash);
}
