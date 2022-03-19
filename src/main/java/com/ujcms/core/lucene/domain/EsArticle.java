package com.ujcms.core.lucene.domain;

import com.ujcms.core.domain.Article;
import com.ujcms.core.domain.Channel;
import com.ujcms.core.support.Anchor;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexableField;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.apache.lucene.document.Field.Store;

/**
 * @author PONY
 */
@Document(indexName = "#{@props.esArticle}")
@Setting(settingPath = "/elasticsearch/article-setting.json")
public class EsArticle extends WebPageWithCustoms {
    public EsArticle() {
        setDiscType("Article");
    }

    public static final String ID = "id";
    public static final String PUBLISH_DATE = "publishDate";
    public static final String CHANNEL_ID = "channel.id";
    public static final String CHANNEL_NAME = "channel.name";
    public static final String CHANNEL_URL = "channel.url";
    public static final String CHANNEL_PATHS_ID = "channel.paths.id";
    public static final String CHANNEL_PATHS_NAME = "channel.paths.name";
    public static final String CHANNEL_PATHS_URL = "channel.paths.url";

    public static EsArticle of(org.apache.lucene.document.Document doc) {
        EsArticle bean = new EsArticle();
        bean.fillWithDocument(doc);
        return bean;
    }

    @Override
    protected void fillWithDocument(org.apache.lucene.document.Document doc) {
        super.fillWithDocument(doc);
        setId(doc.getField(ID).numericValue().intValue());
        long publishMilli = doc.getField(PUBLISH_DATE).numericValue().longValue();
        setPublishDate(OffsetDateTime.ofInstant(Instant.ofEpochMilli(publishMilli), ZoneId.systemDefault()));

        getChannel().setId(doc.getField(CHANNEL_ID).numericValue().intValue());
        getChannel().setName(doc.get(CHANNEL_NAME));
        getChannel().setUrl(doc.get(CHANNEL_URL));
        IndexableField[] pathIds = doc.getFields(CHANNEL_PATHS_ID);
        IndexableField[] pathNames = doc.getFields(CHANNEL_PATHS_NAME);
        IndexableField[] pathUrls = doc.getFields(CHANNEL_PATHS_URL);
        for (int i = 0, len = pathIds.length; i < len; i++) {
            ChannelBaseInner channelBaseInner = new ChannelBaseInner();
            channelBaseInner.setId(pathIds[i].numericValue().intValue());
            channelBaseInner.setName(pathNames[i].stringValue());
            channelBaseInner.setUrl(pathUrls[i].stringValue());
            getChannel().getPaths().add(channelBaseInner);
        }
    }

    @Override
    public org.apache.lucene.document.Document toDocument() {
        org.apache.lucene.document.Document doc = super.toDocument();
        doc.add(new IntPoint(ID, id));
        doc.add(new StringField(ID, String.valueOf(id), Store.NO));
        doc.add(new StoredField(ID, id));
        long publishMilli = publishDate.toInstant().toEpochMilli();
        doc.add(new LongPoint(PUBLISH_DATE, publishMilli));
        doc.add(new StoredField(PUBLISH_DATE, publishMilli));
        doc.add(new NumericDocValuesField(PUBLISH_DATE, publishMilli));

        doc.add(new IntPoint(CHANNEL_ID, getChannel().getId()));
        doc.add(new StoredField(CHANNEL_ID, getChannel().getId()));
        doc.add(new StoredField(CHANNEL_NAME, getChannel().getName()));
        doc.add(new StoredField(CHANNEL_URL, getChannel().getUrl()));
        for (ChannelBaseInner bean : channel.paths) {
            doc.add(new StoredField(CHANNEL_PATHS_ID, bean.getId()));
            doc.add(new StoredField(CHANNEL_PATHS_NAME, bean.getName()));
            doc.add(new StoredField(CHANNEL_PATHS_URL, bean.getUrl()));
        }
        return doc;
    }

    public static EsArticle of(Article article) {
        EsArticle bean = new EsArticle();
        bean.setId(article.getId());
        bean.setTitle(article.getTitle());
        StringBuilder bodyBuff = new StringBuilder();
        if (StringUtils.isNotBlank(article.getSeoDescription())) {
            bodyBuff.append(article.getSeoDescription());
        }
        bodyBuff.append(article.getPlainText());
        bean.setBody(bodyBuff.toString());
        bean.setUrl(article.getUrl());
        bean.setPublishDate(article.getPublishDate());
        bean.setImage(article.getImage());
        bean.setChannel(ChannelInner.of(article.getChannel()));
        bean.setSite(SiteInner.of(article.getSite()));
        return bean;
    }

    @Id
    @Field
    private Integer id = 0;
    @Field(type = FieldType.Date)
    private OffsetDateTime publishDate = OffsetDateTime.now();
    @Field(type = FieldType.Object, index = false)
    private ChannelInner channel = new ChannelInner();

    public int getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public static class ChannelBaseInner implements Anchor {
        public static ChannelBaseInner of(Channel channel) {
            ChannelBaseInner bean = new ChannelInner();
            bean.setId(channel.getId());
            bean.setName(channel.getName());
            bean.setUrl(channel.getUrl());
            return bean;
        }

        @Field(type = FieldType.Integer)
        private Integer id = 0;
        @Field(type = FieldType.Keyword, index = false)
        private String name = "";
        @Field(type = FieldType.Keyword, index = false)
        private String url = "";

        public int getId() {
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

    public static class ChannelInner extends ChannelBaseInner {
        public static ChannelInner of(Channel channel) {
            ChannelInner bean = new ChannelInner();
            bean.setId(channel.getId());
            bean.setName(channel.getName());
            bean.setUrl(channel.getUrl());
            channel.getPaths().forEach(it -> bean.getPaths().add(ChannelBaseInner.of(it)));
            return bean;
        }

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
