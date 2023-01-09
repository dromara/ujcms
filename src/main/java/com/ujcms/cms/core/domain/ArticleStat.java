package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.ArticleStatBase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * 文章浏览统计实体类
 *
 * @author PONY
 */
@Schema(name = "Article.ArticleStat")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ArticleStat extends ArticleStatBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public ArticleStat() {
    }

    public ArticleStat(Integer articleId, int statDay, int views) {
        setArticleId(articleId);
        setStatDay(statDay);
        setViews(views);
    }

    /**
     * 获取指定统计日期的浏览次数
     *
     * @param historyList 历史统计列表
     * @param statDay     统计日期
     * @return 浏览次数
     */
    public static int getExpiredViews(List<ArticleStat> historyList, int statDay) {
        for (ArticleStat stat : historyList) {
            if (stat.getStatDay() == statDay) {
                return stat.getViews();
            }
        }
        return 0;
    }
}