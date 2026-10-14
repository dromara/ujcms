package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ArticleExt;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章扩展数据 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ArticleExtMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ArticleExt bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(ArticleExt bean);

    /**
     * 更新内容列（编辑保存专用）：不含静态页路径、流程实例ID、退回原因——这些列由各自的定向语句写入
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int updateContent(ArticleExt bean);

    /**
     * 只更新静态页路径（静态化专用）
     *
     * @param id               文章ID
     * @param staticFile       PC端静态页路径
     * @param mobileStaticFile 手机端静态页路径
     * @return 更新条数
     */
    int updateStaticFile(@Param("id") Long id, @Param("staticFile") @Nullable String staticFile,
                         @Param("mobileStaticFile") @Nullable String mobileStaticFile);

    /**
     * 只更新流程实例ID与退回原因（审批流程专用）
     *
     * @param id                文章ID
     * @param processInstanceId 流程实例ID
     * @param rejectReason      退回原因
     * @return 更新条数
     */
    int updateProcess(@Param("id") Long id, @Param("processInstanceId") @Nullable String processInstanceId,
                      @Param("rejectReason") @Nullable String rejectReason);

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
    ArticleExt select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<ArticleExt> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据栏目ID删除数据
     *
     * @param channelId 栏目ID
     * @return 被删除的数据条数
     */
    int deleteByChannelId(Long channelId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}