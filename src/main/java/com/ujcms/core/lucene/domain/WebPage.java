package com.ujcms.core.lucene.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ofwise.util.lucene.StringNormsField;
import com.ujcms.core.domain.Site;
import com.ujcms.core.support.Anchor;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexableField;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.apache.lucene.document.Field.Store;

/**
 * @author PONY
 */
public class WebPage implements Anchor {
    public static final String URL = "url";
    public static final String TITLE = "title";
    public static final String BODY = "body";
    public static final String IMAGE = "image";
    public static final String DISC_TYPE = "descType";
    public static final String SITE_ID = "site.id";
    public static final String SITE_NAME = "site.name";
    public static final String SITE_URL = "site.url";
    public static final String SITE_PATHS_ID = "site.paths.id";
    public static final String SITE_PATHS_NAME = "site.paths.name";
    public static final String SITE_PATHS_URL = "site.paths.url";

    protected void fillWithDocument(Document doc) {
        setUrl(doc.get(URL));
        setTitle(doc.get(TITLE));
        setBody(doc.get(BODY));
        setImage(doc.get(IMAGE));
        setDiscType(doc.get(DISC_TYPE));

        getSite().setId(doc.getField(SITE_ID).numericValue().intValue());
        getSite().setName(doc.get(SITE_NAME));
        getSite().setUrl(doc.get(SITE_URL));
        IndexableField[] pathIds = doc.getFields(SITE_PATHS_ID);
        IndexableField[] pathNames = doc.getFields(SITE_PATHS_NAME);
        IndexableField[] pathUrls = doc.getFields(SITE_PATHS_URL);
        for (int i = 0, len = pathIds.length; i < len; i++) {
            SiteBaseInner siteBaseInner = new SiteBaseInner();
            siteBaseInner.setId(pathIds[i].numericValue().intValue());
            siteBaseInner.setName(pathNames[i].stringValue());
            siteBaseInner.setUrl(pathUrls[i].stringValue());
            getSite().getPaths().add(siteBaseInner);
        }
    }

    public Document toDocument() {
        Document doc = new Document();
        doc.add(new StringField(URL, url, Store.YES));
        doc.add(new TextField(TITLE, title, Store.YES));
        if (StringUtils.isNotBlank(body)) {
            doc.add(new TextField(BODY, body, Store.YES));
        }
        if (StringUtils.isNotBlank(image)) {
            doc.add(new StringNormsField(IMAGE, image, Store.YES));
        }
        doc.add(new StringField(DISC_TYPE, discType, Store.YES));
        doc.add(new IntPoint(SITE_ID, getSite().getId()));
        doc.add(new StoredField(SITE_ID, getSite().getId()));
        doc.add(new StoredField(SITE_NAME, getSite().getName()));
        doc.add(new StoredField(SITE_URL, getSite().getUrl()));
        for (SiteBaseInner bean : site.paths) {
            doc.add(new StoredField(SITE_PATHS_ID, bean.getId()));
            doc.add(new StoredField(SITE_PATHS_NAME, bean.getName()));
            doc.add(new StoredField(SITE_PATHS_URL, bean.getUrl()));
        }
        return doc;
    }

    @Field(type = FieldType.Keyword)
    private String url = "";
    @MultiField(mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word"),
            otherFields = {
                    @InnerField(suffix = "pinyin", type = FieldType.Text, analyzer = "pinyinAnalyzer"),
            }
    )
    private String title = "";
    @Nullable
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String body;
    @Nullable
    @Field(type = FieldType.Keyword, index = false)
    private String image;
    @Field(type = FieldType.Keyword)
    private String discType = "";
    @Field(type = FieldType.Object, index = false)
    private SiteInner site = new SiteInner();
    @Nullable
    @Transient
    private String highlightTitle;
    @Nullable
    @Transient
    private String highlightBody;

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
        return title;
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

    public static class SiteBaseInner implements Anchor {
        public static SiteBaseInner of(Site site) {
            SiteBaseInner bean = new SiteBaseInner();
            bean.setId(site.getId());
            bean.setName(site.getName());
            bean.setUrl(site.getUrl());
            return bean;
        }

        @Field(type = FieldType.Integer)
        private Integer id = 0;
        @Field(type = FieldType.Keyword, index = false)
        private String name = "";
        @Field(type = FieldType.Keyword, index = false)
        private String url = "";

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
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

    public static class SiteInner extends SiteBaseInner {

        public static SiteInner of(Site site) {
            SiteInner bean = new SiteInner();
            bean.setId(site.getId());
            bean.setName(site.getName());
            bean.setUrl(site.getUrl());
            site.getPaths().forEach(it -> bean.getPaths().add(SiteBaseInner.of(it)));
            return bean;
        }

        @Field(type = FieldType.Object, index = false)
        private List<SiteBaseInner> paths = new ArrayList<>();

        public List<EsArticle.SiteBaseInner> getPaths() {
            return paths;
        }

        public void setPaths(List<SiteBaseInner> paths) {
            this.paths = paths;
        }
    }
}
