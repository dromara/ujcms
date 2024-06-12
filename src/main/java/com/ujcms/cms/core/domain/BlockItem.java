package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.base.BlockItemBase;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.commons.db.order.OrderEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 区块项实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class BlockItem extends BlockItemBase implements Anchor, OrderEntity, Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "URL地址")
    @Override
    public String getUrl() {
        Article referArticle = getArticle();
        if (referArticle != null) {
            return referArticle.getUrl();
        }
        return Optional.ofNullable(getLinkUrl()).map(linkUrl -> getSite().assembleLinkUrl(linkUrl)).orElse("");
    }

    @JsonIgnore
    @Override
    public String getName() {
        return getTitle();
    }

    /**
     * 获取所有字段中的附件
     *
     * @return 附件列表
     */
    @JsonIgnore
    public List<String> getAttachmentUrls() {
        List<String> urls = new ArrayList<>();
        Optional.ofNullable(getImage()).ifPresent(urls::add);
        Optional.ofNullable(getMobileImage()).ifPresent(urls::add);
        return urls;
    }

    @Schema(description = "转向链接")
    @Pattern(regexp = "^(http|/).*$")
    @Nullable
    @Override
    public String getLinkUrl() {
        return super.getLinkUrl();
    }

    @Nullable
    @JsonIncludeProperties({"id", "title"})
    private Article article;
    @JsonIncludeProperties({"id", "name"})
    private Block block = new Block();
    @JsonIncludeProperties({"id", "name"})
    private Site site = new Site();

    @Nullable
    public Article getArticle() {
        return article;
    }

    public void setArticle(@Nullable Article article) {
        this.article = article;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public static final String NOT_FOUND = "BlockItem not found. ID: ";
}