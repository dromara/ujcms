package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.common.db.order.OrderEntityMapper;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 投票 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface VoteMapper extends OrderEntityMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Vote bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Vote bean);

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
    Vote select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Vote> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 投票
     *
     * @param id 投票ID
     * @return 更新条数
     */
    int cast(@Param("id") Long id);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 按来源三元组获取本站投票定义（种子导入幂等键，theme-seed-export E4b / D29。
     * 投票无别名，文章式：三元组即唯一键、恒不 linked）
     *
     * @param siteId   站点ID
     * @param origin   来源（{@code theme:<themeName>}）
     * @param originId 来源ID
     * @return 投票。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Vote selectByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin,
                        @Param("originId") String originId);

    /**
     * 按来源标记获取本站投票定义列表（导出缺省选择 / 移除演示数据用，E4b）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 投票列表
     */
    List<Vote> listByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 按来源标记统计本站投票定义数（影响报告 {@code demoDataPresent}，E4b）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 行数
     */
    int countByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 清除本站来源标记（移除选票守卫不过 / 卸载“保留”策略：主题来源投票转为普通操作员投票）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 更新条数
     */
    int clearOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 写入来源散列（R4：种子导入落库后按最终行状态计算）
     *
     * @param id         投票ID
     * @param originHash 种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int updateOriginHash(@Param("id") Long id, @Param("originHash") String originHash);

    /**
     * 认领操作员行（theme-seed-export §4.2 adopt 模式，D2 唯一豁免）：写入铸造的来源三元组。
     * 仅无来源标记的行可认领（SQL 内附带条件）——他源行绝不认领（D3）；投票恒为本站私有，
     * 无全局行之虞（D31）。
     *
     * @param id         投票ID
     * @param origin     来源（{@code theme:<themeName>}）
     * @param originId   铸造的 originId
     * @param originHash 当前种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int adoptOrigin(@Param("id") Long id, @Param("origin") String origin,
                    @Param("originId") String originId, @Param("originHash") String originHash);
}