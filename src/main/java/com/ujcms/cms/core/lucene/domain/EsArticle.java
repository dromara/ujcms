package com.ujcms.cms.core.lucene.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.support.Anchor;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.SortField;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.apache.lucene.document.Field.Store;

/**
 * 全文检索文章实体类
 *
 * @author PONY
 */
@Document(indexName = "#{@props.esArticle}")
@Setting(settingPath = "/elasticsearch/article-setting.json")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EsArticle extends WebPageWithCustoms implements Serializable {
    private static final long serialVersionUID = 1L;

    public EsArticle() {
        setDiscType("Article");
    }

    public static final String FIELD_ID = "id";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_PUBLISH_DATE = "publishDate";
    public static final String FIELD_CHANNEL_ID = "channel.id";
    public static final String FIELD_CHANNEL_NAME = "channel.name";
    public static final String FIELD_CHANNEL_URL = "channel.url";
    public static final String FIELD_CHANNEL_PATHS_ID = "channel.paths.id";
    public static final String FIELD_CHANNEL_PATHS_NAME = "channel.paths.name";
    public static final String FIELD_CHANNEL_PATHS_URL = "channel.paths.url";

    public static SortField.Type getSortType(String property) {
        switch (property) {
            case FIELD_ID:
            case FIELD_PUBLISH_DATE:
            case FIELD_CHANNEL_ID:
            case FIELD_CHANNEL_PATHS_ID:
                return SortField.Type.LONG;
            case FIELD_STATUS:
                return SortField.Type.INT;
            default:
                return WebPageWithCustoms.getSortType(property);
        }
    }

    public static EsArticle of(org.apache.lucene.document.Document doc) {
        EsArticle bean = new EsArticle();
        bean.fillWithDocument(doc);
        return bean;
    }

    @Override
    protected void fillWithDocument(org.apache.lucene.document.Document doc) {
        super.fillWithDocument(doc);
        setId(doc.getField(FIELD_ID).numericValue().longValue());
        setStatus(doc.getField(FIELD_STATUS).numericValue().shortValue());
        long publishMilli = doc.getField(FIELD_PUBLISH_DATE).numericValue().longValue();
        setPublishDate(OffsetDateTime.ofInstant(Instant.ofEpochMilli(publishMilli), ZoneId.systemDefault()));

        getChannel().setId(doc.getField(FIELD_CHANNEL_ID).numericValue().longValue());
        getChannel().setName(doc.get(FIELD_CHANNEL_NAME));
        getChannel().setUrl(doc.get(FIELD_CHANNEL_URL));
        IndexableField[] pathIds = doc.getFields(FIELD_CHANNEL_PATHS_ID);
        IndexableField[] pathNames = doc.getFields(FIELD_CHANNEL_PATHS_NAME);
        IndexableField[] pathUrls = doc.getFields(FIELD_CHANNEL_PATHS_URL);
        for (int i = 0, len = pathIds.length; i < len; i++) {
            ChannelBaseInner channelBaseInner = new ChannelBaseInner();
            channelBaseInner.setId(pathIds[i].numericValue().longValue());
            channelBaseInner.setName(pathNames[i].stringValue());
            channelBaseInner.setUrl(pathUrls[i].stringValue());
            getChannel().getPaths().add(channelBaseInner);
        }
    }

    @Override
    public org.apache.lucene.document.Document toDocument() {
        org.apache.lucene.document.Document doc = super.toDocument();
        doc.add(new LongPoint(FIELD_ID, id));
        doc.add(new StringField(FIELD_ID, String.valueOf(id), Store.NO));
        doc.add(new StoredField(FIELD_ID, id));

        doc.add(new IntPoint(FIELD_STATUS, status));
        doc.add(new StoredField(FIELD_STATUS, status));

        long publishMilli = publishDate.toInstant().toEpochMilli();
        doc.add(new LongPoint(FIELD_PUBLISH_DATE, publishMilli));
        doc.add(new StoredField(FIELD_PUBLISH_DATE, publishMilli));
        doc.add(new NumericDocValuesField(FIELD_PUBLISH_DATE, publishMilli));

        doc.add(new LongPoint(FIELD_CHANNEL_ID, getChannel().getId()));
        doc.add(new StoredField(FIELD_CHANNEL_ID, getChannel().getId()));
        doc.add(new StoredField(FIELD_CHANNEL_NAME, getChannel().getName()));
        doc.add(new StoredField(FIELD_CHANNEL_URL, getChannel().getUrl()));
        for (ChannelBaseInner bean : getChannel().getPaths()) {
            doc.add(new LongPoint(FIELD_CHANNEL_PATHS_ID, bean.getId()));
            doc.add(new StoredField(FIELD_CHANNEL_PATHS_ID, bean.getId()));
            doc.add(new StoredField(FIELD_CHANNEL_PATHS_NAME, bean.getName()));
            doc.add(new StoredField(FIELD_CHANNEL_PATHS_URL, bean.getUrl()));
        }
        return doc;
    }

    public static EsArticle of(Article article) {
        EsArticle bean = new EsArticle();
        bean.setId(article.getId());
        bean.setStatus(article.getStatus());
        bean.setEnabled(article.getChannel().getAllowSearch());
        bean.setTitle(article.getTitle());
        bean.setDescription(article.getSeoDescription());

        StringBuilder body = new StringBuilder();
        Optional.ofNullable(article.getSubtitle()).filter(StringUtils::isNotBlank).ifPresent(body::append);
        Optional.ofNullable(article.getFullTitle()).filter(StringUtils::isNotBlank).ifPresent(body::append);
        Optional.ofNullable(article.getSeoDescription()).filter(StringUtils::isNotBlank).ifPresent(body::append);
        body.append(article.getPlainText());
        bean.setBody(body.toString());

        bean.setUrl(article.getUrl());
        bean.setPublishDate(article.getPublishDate());
        bean.setImage(article.getImage());
        bean.setChannel(ChannelInner.of(article.getChannel()));
        bean.setSite(SiteInner.of(article.getSite()));
        return bean;
    }

    /**
     * ID
     */
    @Id
    @Field
    private Long id = 0L;
    /**
     * 状态
     */
    @Field(type = FieldType.Short)
    private Short status = 0;
    /**
     * 发布日期
     */
    @Field(type = FieldType.Date)
    private OffsetDateTime publishDate = OffsetDateTime.now();
    /**
     * 栏目
     */
    @Field(type = FieldType.Object, index = false)
    private ChannelInner channel = new ChannelInner();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Short getStatus() {
        return status;
    }

    public void setStatus(Short status) {
        this.status = status;
    }

    public OffsetDateTime getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(OffsetDateTime publishDate) {
        this.publishDate = publishDate;
    }

    public ChannelInner getChannel() {
        return channel;
    }

    public void setChannel(ChannelInner channel) {
        this.channel = channel;
    }

    /**
     * 全文检索栏目基础实体类
     */
    @Schema(name = "EsArticle.ChannelBaseInner", description = "全文检索栏目基础实体类")
    public static class ChannelBaseInner implements Anchor, Serializable {
        private static final long serialVersionUID = 1L;

        public static ChannelBaseInner of(Channel channel) {
            ChannelBaseInner bean = new ChannelInner();
            bean.setId(channel.getId());
            bean.setName(channel.getName());
            bean.setUrl(channel.getUrl());
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
     * 全文检索栏目实体类
     */
    @Schema(name = "EsArticle.ChannelInner", description = "全文检索栏目实体类")
    public static class ChannelInner extends ChannelBaseInner implements Serializable {
        private static final long serialVersionUID = 1L;

        public static ChannelInner of(Channel channel) {
            ChannelInner bean = new ChannelInner();
            bean.setId(channel.getId());
            bean.setName(channel.getName());
            bean.setUrl(channel.getUrl());
            channel.getPaths().forEach(it -> bean.getPaths().add(ChannelBaseInner.of(it)));
            return bean;
        }

        /**
         * 栏目层级。从一级栏目到当前栏目的列表
         */
        @Field(type = FieldType.Object, index = false)
        private List<ChannelBaseInner> paths = new ArrayList<>();

        public List<ChannelBaseInner> getPaths() {
            return paths;
        }

        public void setPaths(List<ChannelBaseInner> paths) {
            this.paths = paths;
        }
    }
}
