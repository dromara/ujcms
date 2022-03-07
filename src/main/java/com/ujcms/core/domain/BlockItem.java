package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.core.domain.base.BlockItemBase;
import com.ujcms.core.support.Anchor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 区块项 实体类
 *
 * @author PONY
 */
public class BlockItem extends BlockItemBase implements Anchor, Serializable {
    @Nullable
    @Override
    public String getUrl() {
        if (getArticle() != null) {
            return getArticle().getUrl();
        }
        return getLinkUrl();
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

    @Nullable
    @JsonIncludeProperties({"id", "title"})
    private Article article;
    @JsonIncludeProperties({"id", "name"})
    private Block block = new Block();

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
}