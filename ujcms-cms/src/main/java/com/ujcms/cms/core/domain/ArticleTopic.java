package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.generated.GeneratedArticleTopic;

/**
 * 文章专题关联实体类
 *
 * @author PONY
 */
public class ArticleTopic extends GeneratedArticleTopic {

    public ArticleTopic() {
    }

    public ArticleTopic(Long articleId, Long topicId, Integer order) {
        setArticleId(articleId);
        setTopicId(topicId);
        setOrder(order);
    }

    @JsonIncludeProperties({"id", "title", "channel", "status"})
    private Article article = new Article();
    @JsonIncludeProperties({"id", "title", "alias"})
    private Topic topic = new Topic();

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }
}
