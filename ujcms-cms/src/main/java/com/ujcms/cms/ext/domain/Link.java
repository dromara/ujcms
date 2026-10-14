package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.cms.ext.domain.generated.GeneratedLink;
import com.ujcms.common.db.order.OrderEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import jakarta.validation.constraints.Pattern;
import java.util.Optional;

/**
 * 链接实体类。linkUrl / channelId / articleId 三选一（link-groups D2）：
 * 栏目、文章引用在渲染期迟绑（引用目标改名/换别名不断链）。
 *
 * @author PONY
 */
@JsonIgnoreProperties("handler")
public class Link extends GeneratedLink implements Anchor, OrderEntity {
    /**
     * 渲染URL为派生值（link-groups D1）：栏目/文章引用迟绑；普通链接经站点装配上下文路径。
     * 原始列值在 {@link #getLinkUrl()}，MyBatis 只读写该属性。
     */
    @Schema(description = "URL地址")
    @Override
    public String getUrl() {
        Channel referChannel = getChannel();
        if (referChannel != null) {
            return referChannel.getUrl();
        }
        Article referArticle = getArticle();
        if (referArticle != null) {
            return referArticle.getUrl();
        }
        return Optional.ofNullable(getLinkUrl()).map(linkUrl -> getSite().assembleLinkUrl(linkUrl)).orElse("");
    }

    @Schema(description = "链接地址")
    @Pattern(regexp = "^(http|/).*$")
    @Nullable
    @Override
    public String getLinkUrl() {
        return super.getLinkUrl();
    }

    /**
     * 链接组
     */
    @JsonIncludeProperties({"id", "name", "alias", "type"})
    private LinkGroup group = new LinkGroup();

    /**
     * 引用栏目（channelId 非空时由 Mapper 装配；目标已删时为 {@code null}，getUrl 顺延降级）
     */
    @Nullable
    @JsonIncludeProperties({"id", "name", "url"})
    private Channel channel;

    /**
     * 引用文章（articleId 非空时由 Mapper 装配）
     */
    @Nullable
    @JsonIncludeProperties({"id", "title", "url"})
    private Article article;

    @JsonIncludeProperties({"id", "name"})
    private Site site = new Site();

    public LinkGroup getGroup() {
        return group;
    }

    public void setGroup(LinkGroup group) {
        this.group = group;
    }

    @Nullable
    public Channel getChannel() {
        return channel;
    }

    public void setChannel(@Nullable Channel channel) {
        this.channel = channel;
    }

    @Nullable
    public Article getArticle() {
        return article;
    }

    public void setArticle(@Nullable Article article) {
        this.article = article;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public static final String NOT_FOUND = "Link not found. ID = ";
}
