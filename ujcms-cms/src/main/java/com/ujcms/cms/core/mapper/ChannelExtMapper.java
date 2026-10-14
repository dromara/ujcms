package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ChannelExt;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 栏目扩展数据 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ChannelExtMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ChannelExt bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(ChannelExt bean);

    /**
     * 只更新静态页路径（静态化专用）
     *
     * @param id               栏目ID
     * @param staticFile       PC端静态页路径
     * @param mobileStaticFile 手机端静态页路径
     * @return 更新条数
     */
    int updateStaticFile(@Param("id") Long id, @Param("staticFile") @Nullable String staticFile,
                         @Param("mobileStaticFile") @Nullable String mobileStaticFile);

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
    ChannelExt select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<ChannelExt> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据站点ID获取全部扩展行（主题种子导出：列表查询不带 ext，导出前批量补挂）
     *
     * @param siteId 站点ID
     * @return 数据列表
     */
    List<ChannelExt> listBySiteId(Long siteId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}