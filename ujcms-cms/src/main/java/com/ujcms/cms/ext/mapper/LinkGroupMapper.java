package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.LinkGroup;
import com.ujcms.common.db.order.OrderEntityMapper;
import com.ujcms.common.query.QueryInfo;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 链接组 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface LinkGroupMapper extends OrderEntityMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(LinkGroup bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(LinkGroup bean);

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
    @Override
    LinkGroup select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo        查询条件
     * @param isQueryLinkCount 是否同时统计各链接组的链接数
     * @return 数据列表
     */
    List<LinkGroup> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                              @Param("isQueryLinkCount") boolean isQueryLinkCount);

    /**
     * 根据站点ID和别名获取列表
     *
     * @param siteId 站点ID
     * @param alias  别名
     * @return 数据列表
     */
    List<LinkGroup> listByAlias(@Param("siteId") Long siteId, @Param("alias") String alias);

    /**
     * 检查别名是否存在
     *
     * @param alias  别名
     * @param siteId 站点ID
     * @return 数据条数
     */
    int existsByAlias(@Param("alias") String alias, @Param("siteId") Long siteId);

    /**
     * 删除站点链接组（站点删除级联）
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 按来源三元组获取本站链接组（种子导入幂等键，theme-seed-export E4f / D42。
     * 区块式：别名撞本站可见集 → linked 恒不收编）
     *
     * @param siteId   站点ID
     * @param origin   来源（{@code theme:<themeName>}）
     * @param originId 来源ID
     * @return 链接组。没有找到数据，则返回 {@code null}
     */
    @Nullable
    LinkGroup selectByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin,
                             @Param("originId") String originId);

    /**
     * 按来源标记获取本站链接组列表（导出缺省选择 / 移除演示数据用，E4f）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 链接组列表
     */
    List<LinkGroup> listByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 按来源标记统计本站链接组数（影响报告 {@code demoDataPresent}，E4f）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 行数
     */
    int countByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 清除本站来源标记（移除散列守卫不过 / 卸载“保留”策略：主题来源链接组转为普通操作员链接组）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 更新条数
     */
    int clearOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 写入来源散列（种子导入落库后按最终行状态计算）
     *
     * @param id         链接组ID
     * @param originHash 种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int updateOriginHash(@Param("id") Long id, @Param("originHash") String originHash);

    /**
     * 认领操作员行（theme-seed-export §4.2 adopt 模式，D2 唯一豁免）：写入铸造的来源三元组。
     * 仅无来源标记的行可认领（SQL 内附带条件）——他源行绝不认领（D3）；链接组恒为
     * 本站私有，无全局行之虞（D44）。
     *
     * @param id         链接组ID
     * @param origin     来源（{@code theme:<themeName>}）
     * @param originId   铸造的 originId
     * @param originHash 当前种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int adoptOrigin(@Param("id") Long id, @Param("origin") String origin,
                    @Param("originId") String originId, @Param("originHash") String originHash);
}
