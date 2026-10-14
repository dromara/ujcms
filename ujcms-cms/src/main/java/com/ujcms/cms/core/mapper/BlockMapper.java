package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Block;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 区块 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface BlockMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Block bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Block bean);

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
    Block select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo       查询条件
     * @param itemCountSiteId 同时统计各区块在该站点的区块项数；为 {@code null} 则不统计
     * @return 数据列表
     */
    List<Block> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                          @Nullable @Param("itemCountSiteId") Long itemCountSiteId);

    /**
     * 根据别名和站点ID计数
     *
     * @param alias  别名
     * @param siteId 站点ID
     * @return 数据条数
     */
    int existsByAlias(@Param("alias") String alias, @Param("siteId") @Nullable Long siteId);

    /**
     * 根据 站点ID 删除数据
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 列出指定站点已使用的区块分类（去重，按字典序）。返回非空字符串。
     *
     * @param siteId 站点ID。{@code null} 表示全局区块。
     * @return 分类列表
     */
    List<String> listCategories(@Param("siteId") @Nullable Long siteId);

    /**
     * 检查模型是否被任何区块的 {@code block_item_model_id_} 引用。
     *
     * @param modelId 模型ID
     * @return 存在则返回 1，否则返回 0
     */
    int existsByModelId(Long modelId);

    /**
     * 按站点可见范围（本站 ∪ 全局）与别名获取区块（{@code {{block:<alias>}}} 令牌展开用，
     * theme-seed-export E4a / D24）。本站行排前——同别名时本站区块胜出。
     *
     * @param siteId 站点ID
     * @param alias  别名
     * @return 区块列表（本站优先，ID 升序）
     */
    List<Block> listByScopeSiteIdAndAlias(@Param("siteId") Long siteId, @Param("alias") String alias);

    /**
     * 按来源三元组获取本站区块定义（种子导入幂等键，E4a / D23。种子定义恒为本站私有，
     * 站点限定与内容三表同理）
     *
     * @param siteId   站点ID
     * @param origin   来源（{@code theme:<themeName>}）
     * @param originId 来源ID
     * @return 区块。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Block selectByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin,
                         @Param("originId") String originId);

    /**
     * 按来源标记获取本站区块定义列表（导出缺省选择 / 移除演示数据用，E4a）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 区块列表
     */
    List<Block> listByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 按来源标记统计本站区块定义数（影响报告 {@code demoDataPresent}，E4a）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 行数
     */
    int countByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 清除本站来源标记（移除守卫不过 / 卸载“保留”策略：主题来源区块转为普通操作员区块）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 更新条数
     */
    int clearOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 写入来源散列（R4：种子导入落库后按最终行状态计算）
     *
     * @param id         区块ID
     * @param originHash 种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int updateOriginHash(@Param("id") Long id, @Param("originHash") String originHash);

    /**
     * 认领操作员行（theme-seed-export §4.2 adopt 模式，D2 唯一豁免）：写入铸造的来源三元组。
     * 仅无来源标记的<b>本站私有</b>行可认领（SQL 内附带条件）——他源行绝不认领（D3），
     * 全局区块服务全站、绝不认领（D26），并发下亦然。
     *
     * @param id         区块ID
     * @param origin     来源（{@code theme:<themeName>}）
     * @param originId   铸造的 originId
     * @param originHash 当前种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int adoptOrigin(@Param("id") Long id, @Param("origin") String origin,
                    @Param("originId") String originId, @Param("originHash") String originHash);
}