package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedArticleExt;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.BeanUtils;

/**
 * 文章扩展数据实体类
 *
 * @author PONY
 */
@Schema(name = "Article.ArticleExt")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"imageListJson", "fileListJson", "handler"})
public class ArticleExt extends GeneratedArticleExt {
    /**
     * 复制出另一篇文章用的扩展数据（扩展栏目、推送的子行，译文草稿）：内容原样带走，只属于原文章自己的状态清零——
     * 静态页路径、流程实例ID、退回原因、各项计数。
     * <p>
     * 这些带到另一篇文章上都是错的，静态页路径尤其危险：子行重新生成或被删除时，会按这个路径删掉原文章的静态页
     * （docs/spec/article-management/plan.md Phase 1.3）。ID 一并清空，由插入时回填
     */
    public ArticleExt copyForChild() {
        ArticleExt child = new ArticleExt();
        BeanUtils.copyProperties(this, child);
        child.setId(null);
        child.setStaticFile(null);
        child.setMobileStaticFile(null);
        child.setProcessInstanceId(null);
        child.setRejectReason(null);
        child.setComments(0);
        child.setDownloads(0);
        child.setFavorites(0);
        child.setUps(0);
        child.setDowns(0);
        child.setViews(0L);
        child.setDayViews(0);
        child.setWeekViews(0);
        child.setMonthViews(0);
        child.setQuarterViews(0);
        child.setYearViews(0L);
        return child;
    }
}