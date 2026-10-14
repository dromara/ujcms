package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.common.db.order.OrderEntityMapper;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 表单类型 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface FormTypeMapper extends OrderEntityMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(FormType bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(FormType bean);

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
    FormType select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo        查询条件
     * @param isQueryFormCount 是否同时统计各表单类型的表单数
     * @return 数据列表
     */
    List<FormType> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                             @Param("isQueryFormCount") boolean isQueryFormCount);

    /**
     * 根据 模型ID 查询数据是否存在
     *
     * @param modelId 模型ID
     * @return 文章是否存在。0代表不存在，1代表存在
     */
    int existsByModelId(Long modelId);
    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 按来源三元组获取本站表单类型定义（种子导入幂等键，theme-seed-export E4e / D38。
     * 表单无别名，文章式：三元组即唯一键、恒不 linked）
     *
     * @param siteId   站点ID
     * @param origin   来源（{@code theme:<themeName>}）
     * @param originId 来源ID
     * @return 表单类型。没有找到数据，则返回 {@code null}
     */
    @Nullable
    FormType selectByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin,
                            @Param("originId") String originId);

    /**
     * 按来源标记获取本站表单类型定义列表（导出缺省选择 / 移除演示数据用，E4e）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 表单类型列表
     */
    List<FormType> listByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 按来源标记统计本站表单类型定义数（影响报告 {@code demoDataPresent}，E4e）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 行数
     */
    int countByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 清除本站来源标记（移除提交守卫不过 / 低配退化 / 卸载“保留”策略：主题来源表单转普通操作员表单）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 更新条数
     */
    int clearOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 写入来源散列（种子导入落库后按最终行状态计算）
     *
     * @param id         表单类型ID
     * @param originHash 种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int updateOriginHash(@Param("id") Long id, @Param("originHash") String originHash);

    /**
     * 认领操作员行（theme-seed-export §4.2 adopt 模式，D2 唯一豁免）：写入铸造的来源三元组。
     * 仅无来源标记的行可认领（SQL 内附带条件）——他源行绝不认领（D3）；表单类型恒为本站私有。
     *
     * @param id         表单类型ID
     * @param origin     来源（{@code theme:<themeName>}）
     * @param originId   铸造的 originId
     * @param originHash 当前种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int adoptOrigin(@Param("id") Long id, @Param("origin") String origin,
                    @Param("originId") String originId, @Param("originHash") String originHash);
}