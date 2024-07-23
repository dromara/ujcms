package com.ujcms.cms.core.lucene.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.commons.lucene.StringNormsField;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.SortField;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.apache.lucene.document.Field.Store;

/**
 * WEB页面基类
 *
 * @author PONY
 */
public class WebPage implements Anchor, Serializable {
    private static final long serialVersionUID = 1L;

    public static final String FIELD_URL = "url";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_BODY = "body";
    public static final String FIELD_IMAGE = "image";
    public static final String FIELD_DISC_TYPE = "discType";
    public static final String FIELD_ENABLED = "enabled";
    public static final String FIELD_SITE_ID = "site.id";
    public static final String FIELD_SITE_NAME = "site.name";
    public static final String FIELD_SITE_URL = "site.url";
    public static final String FIELD_SITE_PATHS_ID = "site.paths.id";
    public static final String FIELD_SITE_PATHS_NAME = "site.paths.name";
    public static final String FIELD_SITE_PATHS_URL = "site.paths.url";

    public static SortField.Type getSortType(String property) {
        switch (property) {
            case FIELD_URL:
            case FIELD_TITLE:
            case FIELD_DESCRIPTION:
            case FIELD_BODY:
            case FIELD_IMAGE:
            case FIELD_DISC_TYPE:
            case FIELD_ENABLED:
            case FIELD_SITE_NAME:
            case FIELD_SITE_URL:
            case FIELD_SITE_PATHS_NAME:
            case FIELD_SITE_PATHS_URL:
                return SortField.Type.STRING;
            case FIELD_SITE_ID:
            case FIELD_SITE_PATHS_ID:
                return SortField.Type.LONG;
            default:
                throw new IllegalArgumentException("Lucene order property not found: " + property);
        }
    }

    protected void fillWithDocument(Document doc) {
        setUrl(doc.get(FIELD_URL));
        setTitle(doc.get(FIELD_TITLE));
        setDescription(doc.get(FIELD_DESCRIPTION));
        setBody(doc.get(FIELD_BODY));
        setImage(doc.get(FIELD_IMAGE));
        setDiscType(doc.get(FIELD_DISC_TYPE));

        Optional.ofNullable(doc.get(FIELD_ENABLED)).map(Boolean::valueOf).ifPresent(this::setEnabled);

        getSite().setId(doc.getField(FIELD_SITE_ID).numericValue().longValue());
        getSite().setName(doc.get(FIELD_SITE_NAME));
        getSite().setUrl(doc.get(FIELD_SITE_URL));
        IndexableField[] pathIds = doc.getFields(FIELD_SITE_PATHS_ID);
        IndexableField[] pathNames = doc.getFields(FIELD_SITE_PATHS_NAME);
        IndexableField[] pathUrls = doc.getFields(FIELD_SITE_PATHS_URL);
        for (int i = 0, len = pathIds.length; i < len; i++) {
            SiteBaseInner siteBaseInner = new SiteBaseInner();
            siteBaseInner.setId(pathIds[i].numericValue().longValue());
            siteBaseInner.setName(pathNames[i].stringValue());
            siteBaseInner.setUrl(pathUrls[i].stringValue());
            getSite().getPaths().add(siteBaseInner);
        }
    }

    public Document toDocument() {
        Document doc = new Document();
        doc.add(new StringField(FIELD_URL, url, Store.YES));
        doc.add(new TextField(FIELD_TITLE, title, Store.YES));
        if (StringUtils.isNotBlank(description)) {
            doc.add(new TextField(FIELD_DESCRIPTION, description, Store.YES));
        }
        if (StringUtils.isNotBlank(body)) {
            doc.add(new TextField(FIELD_BODY, body, Store.YES));
        }
        if (StringUtils.isNotBlank(image)) {
            doc.add(new StringNormsField(FIELD_IMAGE, image, Store.YES));
        }
        doc.add(new StringField(FIELD_DISC_TYPE, discType, Store.YES));
        doc.add(new StringField(FIELD_ENABLED, getEnabled().toString(), Store.YES));

        doc.add(new LongPoint(FIELD_SITE_ID, getSite().getId()));
        doc.add(new StoredField(FIELD_SITE_ID, getSite().getId()));
        doc.add(new StoredField(FIELD_SITE_NAME, getSite().getName()));
        doc.add(new StoredField(FIELD_SITE_URL, getSite().getUrl()));
        for (SiteBaseInner bean : site.paths) {
            doc.add(new StoredField(FIELD_SITE_PATHS_ID, bean.getId()));
            doc.add(new StoredField(FIELD_SITE_PATHS_NAME, bean.getName()));
            doc.add(new StoredField(FIELD_SITE_PATHS_URL, bean.getUrl()));
        }
        return doc;
    }

    /**
     * URL地址
     */
    @Field(type = FieldType.Keyword)
    private String url = "";
    /**
     * 标题。ik_max_word 不完全包含 ik_smart，会导致某些内容无法搜索，统一使用 ik_max_word
     */
    @MultiField(mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_max_word"),
            otherFields = {
                    @InnerField(suffix = "pinyin", type = FieldType.Text, analyzer = "pinyinAnalyzer"),
            }
    )
    private String title = "";
    /**
     * 描述。ik_max_word 不完全包含 ik_smart，会导致某些内容无法搜索，统一使用 ik_max_word
     */
    @Nullable
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_max_word")
    private String description;
    /**
     * 正文。ik_max_word 不完全包含 ik_smart，会导致某些内容无法搜索，统一使用 ik_max_word
     */
    @Nullable
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_max_word")
    private String body;
    /**
     * 图片
     */
    @Nullable
    @Field(type = FieldType.Keyword, index = false)
    private String image;
    /**
     * 类型
     */
    @Field(type = FieldType.Keyword)
    private String discType = "";
    /**
     * 是否启用
     */
    @Field(type = FieldType.Boolean)
    private Boolean enabled = true;
    /**
     * 站点
     */
    @Field(type = FieldType.Object, index = false)
    private SiteInner site = new SiteInner();
    /**
     * 标题（含高亮标识）
     */
    @Nullable
    @Transient
    private String highlightTitle;
    /**
     * 正文（含高亮标识）
     */
    @Nullable
    @Transient
    private String highlightBody;
    /**
     * 得分
     */
    private double score;

    @Override
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @JsonIgnore
    @Override
    public String getName() {
        return getTitle();
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    @Nullable
    public String getBody() {
        return body;
    }

    public void setBody(@Nullable String body) {
        this.body = body;
    }

    @Nullable
    public String getImage() {
        return image;
    }

    public void setImage(@Nullable String image) {
        this.image = image;
    }

    public String getDiscType() {
        return discType;
    }

    public void setDiscType(String discType) {
        this.discType = discType;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public SiteInner getSite() {
        return site;
    }

    public void setSite(SiteInner site) {
        this.site = site;
    }

    @Nullable
    public String getHighlightTitle() {
        return highlightTitle;
    }

    public void setHighlightTitle(@Nullable String highlightTitle) {
        this.highlightTitle = highlightTitle;
    }

    @Nullable
    public String getHighlightBody() {
        return highlightBody;
    }

    public void setHighlightBody(@Nullable String highlightBody) {
        this.highlightBody = highlightBody;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    /**
     * 全文检索站点基础实体类
     */
    @Schema(name = "WebPage.SiteBaseInner", description = "文检索站点基础实体类")
    public static class SiteBaseInner implements Anchor, Serializable {
        private static final long serialVersionUID = 1L;

        public static SiteBaseInner of(Site site) {
            SiteBaseInner bean = new SiteBaseInner();
            bean.setId(site.getId());
            bean.setName(site.getName());
            bean.setUrl(site.getUrl());
            return bean;
        }

        /**
         * ID
         */
        @Field(type = FieldType.Integer)
        private Long id = 0L;
        /**
         * 名称
         */
        @Field(type = FieldType.Keyword, index = false)
        private String name = "";
        /**
         * URL地址
         */
        @Field(type = FieldType.Keyword, index = false)
        private String url = "";

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        @Override
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /**
     * 全文检索站点实体类
     */
    @Schema(name = "WebPage.SiteInner", description = "全文检索站点实体类")
    public static class SiteInner extends SiteBaseInner {
        private static final long serialVersionUID = 1L;

        public static SiteInner of(Site site) {
            SiteInner bean = new SiteInner();
            bean.setId(site.getId());
            bean.setName(site.getName());
            bean.setUrl(site.getUrl());
            site.getPaths().forEach(it -> bean.getPaths().add(SiteBaseInner.of(it)));
            return bean;
        }

        /**
         * 站点层级。从一级站点到当前站点的列表
         */
        @Field(type = FieldType.Object, index = false)
        private List<SiteBaseInner> paths = new ArrayList<>();

        public List<WebPage.SiteBaseInner> getPaths() {
            return paths;
        }

        public void setPaths(List<SiteBaseInner> paths) {
            this.paths = paths;
        }
    }
}
