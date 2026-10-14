package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.ArticleI18n;
import com.ujcms.cms.core.mapper.ArticleI18nMapper;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 文章翻译过期锚点维护（translation-workflow.md）。
 *
 * <p>只依赖 Mapper，不依赖 {@link ArticleI18nService}/{@link ArticleService}——
 * ArticleService 的编辑保存钩子经由本组件写锚点，避免服务间循环依赖。
 *
 * <p>分类规则：编辑保存时若本成员“已过期”、处于非正常状态（草稿/待审/已退回等）或内容未变，
 * 视为追赶保存（仅推进 syncedDate）；否则视为原创编辑（双锚点同步推进）。
 * 非正常状态豁免是关键——译文草稿的首次翻译保存不得反向把源文标为“可能过期”；
 * 非公开内容不产生对端应吸收的原创修改。内容未变豁免（2026-09-05）：新增译文后顺手点击源文“保存”
 * 曾把刚建的全部译文标为“可能过期”——没有修改就没有对端需要吸收的内容。
 *
 * @author PONY
 */
@Service
public class ArticleI18nAnchorService {
    private final ArticleI18nMapper mapper;

    public ArticleI18nAnchorService(ArticleI18nMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 编辑保存钩子（仅 ArticleService 的完整编辑保存路径调用——即唯一推进 modified_ 的路径）。
     * 未入组文章为空操作（先经缓存读判定，不产生写、不刷新共享缓存）。
     *
     * @param articleId      文章ID
     * @param normalStatus   保存后是否为 isNormal 状态（已发布/已归档）
     * @param contentChanged 内容是否与保存前不同（{@code Article.isSameContent}）；未变的保存按追赶处理
     * @param saveTime       保存时间（传入 modified_ 同值，原创编辑时 content_date_ == modified_）
     */
    public void trackEditorialSave(Long articleId, boolean normalStatus, boolean contentChanged,
                                   OffsetDateTime saveTime) {
        ArticleI18n self = mapper.select(articleId);
        if (self == null) {
            return;
        }
        List<ArticleI18n> groupRows = mapper.listByGroupIds(List.of(self.getGroupId()));
        if (!normalStatus || !contentChanged || ArticleI18n.isStale(self, groupRows)) {
            mapper.updateSyncedDate(articleId, saveTime);
        } else {
            mapper.updateAnchors(articleId, saveTime, saveTime);
        }
    }

    /**
     * 显式标记已同步（AI 预翻译等非人工编辑路径调用；v1 无控制台入口）。未入组为空操作
     *
     * @param articleId  文章ID
     * @param syncedDate 已同步至此时间
     */
    public void markSynced(Long articleId, OffsetDateTime syncedDate) {
        mapper.updateSyncedDate(articleId, syncedDate);
    }

    /**
     * 建链钳制（translation-workflow.md）：加入既有组的成员，其 content_date_ 取
     * min(自身 modified_, 既有成员最小 synced_date_)——否则近期编辑过的新成员一入组
     * 就会把既有成员全部标为“可能过期”，与操作者“关联=断言当前一致”的意图相悖；
     * 取 min 同时保留既有成员之间已经产生的过期状态。
     *
     * @param existingGroupId 既有组ID；{@code null}（新建组）时直接返回 joiningModified
     * @param joiningModified 加入成员文章的 modified_
     * @return 加入成员应使用的 content_date_
     */
    public OffsetDateTime clampJoinContentDate(@Nullable Long existingGroupId, OffsetDateTime joiningModified) {
        if (existingGroupId == null) {
            return joiningModified;
        }
        OffsetDateTime result = joiningModified;
        for (ArticleI18n member : mapper.listByGroupIds(List.of(existingGroupId))) {
            if (member.getSyncedDate().isBefore(result)) {
                result = member.getSyncedDate();
            }
        }
        return result;
    }
}
