package com.ujcms.cms.ext.domain;

import static com.ujcms.cms.core.support.Constants.MAPPER;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.ext.domain.generated.GeneratedComment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * 评论实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"imagesJson", "handler", "ip"})
public class Comment extends GeneratedComment {
    /**
     * 评论图片列表
     */
    @Nullable
    private transient List<String> images;

    @Schema(description = "评论图片列表")
    public List<String> getImages() {
        if (images == null) {
            String json = getImagesJson();
            if (StringUtils.isBlank(json)) {
                return Collections.emptyList();
            }
            try {
                images = MAPPER.readValue(json, new TypeReference<List<String>>() {
                });
            } catch (JsonProcessingException e) {
                return Collections.emptyList();
            }
        }
        return images;
    }

    public void setImages(List<String> images) {
        try {
            setImagesJson(MAPPER.writeValueAsString(images));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of List<String>", e);
        }
        this.images = images;
    }

    @Override
    public void setImagesJson(@Nullable String imagesJson) {
        super.setImagesJson(imagesJson);
        // Entities.copy 只拷贝 imagesJson（简单类型），须同时失效缓存，避免 getImages 返回旧值
        this.images = null;
    }

    /**
     * 获取评论中的附件URL
     *
     * @return 附件URL列表
     */
    @JsonIgnore
    public List<String> getAttachmentUrls() {
        return getImages();
    }

    /**
     * 站点
     */
    @JsonIncludeProperties({"id", "name", "url"})
    private Site site = new Site();
    /**
     * 所属文章
     */
    @JsonIncludeProperties({"id", "title", "url"})
    private Article article = new Article();
    /**
     * 评论用户
     */
    @JsonIncludeProperties({"id", "username", "nickname"})
    private User user = new User();

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    /**
     * 状态：已审核
     */
    public static final short STATUS_AUDITED = 0;
    /**
     * 状态：未审核
     */
    public static final short STATUS_UNREVIEWED = 1;
    /**
     * 状态：已屏蔽
     */
    public static final short STATUS_BLOCKED = 2;

    public static final String NOT_FOUND = "Comment not found. ID: ";
}
