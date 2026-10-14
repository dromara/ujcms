package com.ujcms.cms.core.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Objects;

/**
 * 文章翻译等价集成员（multilingual P5，translation-links.md §2；过期锚点见 translation-workflow.md）。
 * {@code articleId} 为主键——一篇文章至多属于一个翻译组；{@code groupId} 为雪花ID虚拟组（无父表）。
 * 语言/站点不在成员行上冗余，由成员文章推导。
 *
 * <p>过期锚点：{@code contentDate} 为本成员最后一次“原创”内容修改时间（对端译文应吸收该修改）；
 * {@code syncedDate} 为本成员已吸收对端原创内容至此时间。判定见 {@link #isStale}。
 *
 * @author PONY
 */
public class ArticleI18n implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 数据库表名
     */
    public static final String TABLE_NAME = "article_i18n";

    /**
     * 文章ID
     */
    @NotNull
    @Schema(description = "文章ID")
    private Long articleId = 0L;

    /**
     * 翻译组ID
     */
    @NotNull
    @Schema(description = "翻译组ID")
    private Long groupId = 0L;

    /**
     * 最后原创内容修改时间（对端应吸收）
     */
    @NotNull
    @Schema(description = "最后原创内容修改时间")
    private OffsetDateTime contentDate = OffsetDateTime.now();

    /**
     * 已吸收对端原创内容至此时间
     */
    @NotNull
    @Schema(description = "已吸收对端原创内容至此时间")
    private OffsetDateTime syncedDate = OffsetDateTime.now();

    public ArticleI18n() {
    }

    public ArticleI18n(Long articleId, Long groupId) {
        this.articleId = articleId;
        this.groupId = groupId;
    }

    public ArticleI18n(Long articleId, Long groupId, OffsetDateTime contentDate, OffsetDateTime syncedDate) {
        this.articleId = articleId;
        this.groupId = groupId;
        this.contentDate = contentDate;
        this.syncedDate = syncedDate;
    }

    /**
     * 过期判定：存在对端成员的 {@code contentDate} 晚于本成员 {@code syncedDate}（严格大于，同刻不算过期）。
     * 状态无关——对端被下线/转草稿不清除已产生的过期标志。
     *
     * @param self      本成员行
     * @param groupRows 本组全部成员行（可含 self，自身会被跳过）
     */
    public static boolean isStale(ArticleI18n self, Collection<ArticleI18n> groupRows) {
        for (ArticleI18n peer : groupRows) {
            if (!Objects.equals(peer.getArticleId(), self.getArticleId())
                    && peer.getContentDate().isAfter(self.getSyncedDate())) {
                return true;
            }
        }
        return false;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public OffsetDateTime getContentDate() {
        return contentDate;
    }

    public void setContentDate(OffsetDateTime contentDate) {
        this.contentDate = contentDate;
    }

    public OffsetDateTime getSyncedDate() {
        return syncedDate;
    }

    public void setSyncedDate(OffsetDateTime syncedDate) {
        this.syncedDate = syncedDate;
    }
}
