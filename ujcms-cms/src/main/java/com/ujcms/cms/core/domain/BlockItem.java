package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.generated.GeneratedBlockItem;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.common.db.order.OrderEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 区块项实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class BlockItem extends GeneratedBlockItem implements Anchor, OrderEntity {

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
        Model model = getBlockItemModel();
        if (model != null) {
            urls.addAll(model.getUrlsFromMap(getCustoms()));
        }
        return urls;
    }

    @Schema(description = "转向链接")
    @Pattern(regexp = "^$|^(http|/).*$")
    @Nullable
    @Override
    public String getLinkUrl() {
        return super.getLinkUrl();
    }

    /**
     * 自定义字段
     */
    @Nullable
    private transient Map<String, Object> customs;

    @Schema(description = "自定义字段")
    public Map<String, Object> getCustoms() {
        if (customs != null) {
            return customs;
        }
        customs = new HashMap<>(16);
        Model model = getBlockItemModel();
        if (model == null) {
            return customs;
        }
        customs.putAll(model.assembleMap(getMainsJson()));
        customs.putAll(model.assembleMap(getClobsJson()));
        return customs;
    }

    public void setCustoms(Map<String, Object> customs) {
        this.customs = customs;
    }

    public void disassembleCustoms(Model model, HtmlSanitizeService sanitizeService) {
        Map<String, Object> map = model.sanitizeMap(getCustoms(), sanitizeService);
        setCustoms(map);
        model.disassembleMap(map, this::setMainsJson, this::setClobsJson);
    }

    /**
     * 获取区块项模型。BlockItem 自身不持有模型 FK；模型定义在父 Block 上，所有同一 Block 下的
     * BlockItem 共享同一模型。返回 {@code null} 表示该 Block 未配置自定义模型。
     */
    @JsonIgnore
    @Nullable
    public Model getBlockItemModel() {
        return getBlock().getBlockItemModel();
    }

    @Nullable
    @JsonIncludeProperties({"id", "title"})
    private Article article;
    @JsonIncludeProperties({"id", "name", "blockItemModelId"})
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
