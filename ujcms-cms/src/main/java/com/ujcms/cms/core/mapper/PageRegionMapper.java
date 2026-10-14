package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.PageRegion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 页面区域 Mapper（页面构成-结构树）
 *
 * @author PONY
 */
@Mapper
@Repository
public interface PageRegionMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(PageRegion bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(PageRegion bean);

    /**
     * 删除数据
     *
     * @param id 区域ID
     * @return 删除条数
     */
    int delete(Long id);

    /**
     * 删除站点全部区域行（站点删除级联，docs/spec/site-delete-cascade/plan.md）
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 获取数据
     *
     * @param id 区域ID
     * @return 实体对象
     */
    @Nullable
    PageRegion select(Long id);

    /**
     * 按唯一键获取区域（{@code uk_page_region_scope}）
     *
     * @param siteId     站点ID
     * @param pageRef    页面清单名称
     * @param regionName 区域插槽名称
     * @param scope      区域范围（{@code default} / {@code channel:<id>} / {@code variant:<surface>:<value>}）
     * @return 实体对象
     */
    @Nullable
    PageRegion selectByUnique(@Param("siteId") Long siteId, @Param("pageRef") String pageRef,
                              @Param("regionName") String regionName,
                              @Param("scope") String scope);

    /**
     * 本区域的全部范围行（默认行 + 覆盖行；孤立面板清理取全范围引用并集、控制台范围目录用）
     *
     * @param siteId     站点ID
     * @param pageRef    页面清单名称
     * @param regionName 区域插槽名称
     * @return 区域行列表（default 优先，其余按 scope_ 排序）
     */
    List<PageRegion> listByRegion(@Param("siteId") Long siteId, @Param("pageRef") String pageRef,
                                  @Param("regionName") String regionName);

    /**
     * 页面下已存在的覆盖范围值（去重、不含 default；构建器范围切换器目录用）
     *
     * @param siteId  站点ID
     * @param pageRef 页面清单名称
     * @return 覆盖范围值列表
     */
    List<String> listScopes(@Param("siteId") Long siteId, @Param("pageRef") String pageRef);

    /**
     * 乐观锁更新（仅结构树与修改时间；WHERE 带客户端版本号，命中后版本号自增——与
     * 乐观锁同型）。区域草稿的并发编辑必须 409 而非静默覆盖：后保存方会经
     * 孤立面板清理删除先保存方刚建的面板投放（§19.3 的 409 契约）。
     *
     * @param bean    实体对象（layoutJson / draftLayoutJson / modified 已设置）
     * @param version 客户端提交的版本号
     * @return 更新条数。为 0 表示版本冲突或记录不存在
     */
    int updateWithLock(@Param("bean") PageRegion bean, @Param("version") int version);

    /**
     * 站点内全部区域（页面目录统计 / 健康检查用）
     *
     * @param siteId 站点ID
     * @return 区域列表
     */
    List<PageRegion> listBySiteId(Long siteId);

    /**
     * 按来源标记获取本站区域列表（主题种子卸载 / 重置用。站点限定：{@code origin_} 不含站点，
     * 同名主题可装于多个站点，不限定会命中他站行）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 区域列表
     */
    List<PageRegion> listByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 按来源标记统计本站区域数（影响报告 {@code demoDataPresent}，§6.2）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 行数
     */
    int countByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 清除本站来源标记（主题卸载-保留策略：种子区域转为普通操作员区域）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 更新条数
     */
    int clearOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 写入来源散列（R4：种子重置重建结构树后按新树投影刷新——{@code updateWithLock} 刻意
     * 不含来源列，须经本专用语句写入，theme-marketplace D22）
     *
     * @param id         区域ID
     * @param originHash 结构树投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int updateOriginHash(@Param("id") Long id, @Param("originHash") String originHash);

    /**
     * 改记来源标记（D23）：{@code resetAllOrigin} 重建他主题本源区域行后改记为本主题
     * （{@code updateWithLock} 不含来源列；散列由 {@link #updateOriginHash} 另写）。
     *
     * @param id     区域ID
     * @param origin 新来源标记（{@code theme:<name>}）
     * @return 影响行数
     */
    int updateOrigin(@Param("id") Long id, @Param("origin") String origin);

    /**
     * 认领操作员行（theme-seed-export §4.2 adopt 模式，D2 唯一豁免）：写入来源标记与散列。
     * 区域行无 {@code origin_id_} 列（{@code (site, page, region)} 即唯一键）。
     * 仅无来源标记的行可认领（SQL 内附带条件）——他源行绝不认领（D3），并发下亦然。
     *
     * @param id         区域ID
     * @param origin     来源（{@code theme:<themeName>}）
     * @param originHash 结构树投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int adoptOrigin(@Param("id") Long id, @Param("origin") String origin,
                    @Param("originHash") String originHash);
}
