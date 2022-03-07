package com.ujcms.core.domain;

import com.ujcms.core.domain.base.ArticleStatBase;

import java.io.Serializable;
import java.util.List;

/**
 * 文章浏览统计 实体类
 *
 * @author PONY
 */
public class ArticleStat extends ArticleStatBase implements Serializable {
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