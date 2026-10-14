package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.TemplateHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 模板编辑历史 Mapper（只追加——无 update 语句）
 *
 * @author PONY
 */
@Mapper
@Repository
public interface TemplateHistoryMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(TemplateHistory bean);

    /**
     * 删除数据（仅保留裁剪用）
     *
     * @param id 历史ID
     * @return 删除条数
     */
    int delete(Long id);

    /**
     * 按路径 LIKE 模式删除（站点删除级联，docs/spec/site-delete-cascade/plan.md：站点子树
     * {@code /{siteId}/%} 的历史随站点清除——恢复端点按站点子树门禁，站点没了历史永不可达）
     *
     * @param pattern LIKE 模式（调用方拼装，如 {@code /12345/%}；站点ID为数字，无需转义）
     * @return 删除条数
     */
    int deleteByPathPattern(String pattern);

    /**
     * 获取数据
     *
     * @param id 历史ID
     * @return 实体对象
     */
    @Nullable
    TemplateHistory select(Long id);

    /**
     * 路径下最新一条历史（{@code max(id_)} 子查询——免分页方言；含 {@code content_}——
     * 回填漂移检测与追加去重需要比较内容）
     *
     * @param path 存储相对路径
     * @return 最新历史条目；无则 null
     */
    @Nullable
    TemplateHistory selectLatestByPath(@Param("path") String path);

    /**
     * 路径下的全部历史（新→旧，按 {@code id_} 倒序——雪花ID单调；<b>不含</b> {@code content_}——
     * 单版本可达 1MB，列表视图免拖内容；单条内容经 {@link #select} 获取）
     *
     * @param path 存储相对路径
     * @return 历史列表
     */
    List<TemplateHistory> listByPath(@Param("path") String path);

    /**
     * 目录子树下的历史（新→旧；已删除文件的发现面——文件列表里已看不到它们；不含 {@code content_}）。
     * {@code prefix} 为已按 {@code #} 转义（{@code % _ #}）的 LIKE 前缀（服务层负责），
     * 调用方经 PageHelper 限制行数。
     *
     * @param prefix 已转义的目录前缀（不含结尾通配符）
     * @return 历史列表
     */
    List<TemplateHistory> listByPathPrefix(@Param("prefix") String prefix);
}
