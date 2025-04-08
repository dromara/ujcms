package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.ext.service.args.ImportDataArgs;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.ext.domain.ImportData;
import com.ujcms.cms.ext.domain.base.ImportDataBase;
import com.ujcms.cms.ext.mapper.ImportDataMapper;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.jodconverter.core.util.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.ujcms.commons.db.DataMigration.*;

/**
 * 导入数据 Service
 *
 * @author PONY
 */
@Service
public class ImportDataService {
    private final ArticleService articleService;
    private final ChannelService channelService;
    private final ImportDataMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public ImportDataService(ArticleService articleService, ChannelService channelService,
                             ImportDataMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.articleService = articleService;
        this.channelService = channelService;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Nullable
    public ImportData selectByOrigId(String origId) {
        return mapper.selectByOrigId(origId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertChannel(ResultSet rs, List<String> columns, Long siteId, Long channelModelId, Long articleModelId)
            throws SQLException {
        String id = getRsStringRequired(rs, columns, "id_");
        String parentId = getRsString(rs, columns, "parent_id_");
        String name = getRsStringRequired(rs, columns, "name_");
        String alias = getRsString(rs, columns, "alias_");
        String seoTitle = getRsString(rs, columns, "seo_title_");
        String seoKeywords = getRsString(rs, columns, "seo_keywords_");
        String seoDescription = getRsString(rs, columns, "seo_description_");
        String linkUrl = getRsString(rs, columns, "link_url_");
        String image = getRsString(rs, columns, "image_");
        String text = getRsString(rs, columns, "text_");
        String markdown = getRsString(rs, columns, "markdown_");
        Long views = getRsLong(rs, columns, "views_");
        OffsetDateTime created = getRsOffsetDateTime(rs, columns, "created_");
        OffsetDateTime modified = getRsOffsetDateTime(rs, columns, "modified_");
        Short type = getRsShort(rs, columns, "type_");
        Boolean targetBlank = getRsBoolean(rs, columns, "target_blank_");
        Boolean nav = getRsBoolean(rs, columns, "nav_");

        ImportData imported = selectByOrigId(id);
        // 之前已经导入过的
        if (imported != null) {
            Channel importedChannel = channelService.select(Long.parseLong(imported.getCurrentId()));
            // 依然存在的数据，不导入
            if (importedChannel != null) {
                return;
            }
            // 否则删除导入记录
            delete(imported.getId());
        }

        Channel channel = new Channel();
        // 如果 alias 不重复，则采用 alias
        if (StringUtils.isNotBlank(alias) && !channelService.existsByAlias(alias, siteId)) {
            channel.setAlias(alias);
        }
        channel.setName(name);
        channel.setSeoTitle(seoTitle);
        channel.setSeoKeywords(seoKeywords);
        channel.setSeoDescription(seoDescription);
        channel.setLinkUrl(linkUrl);
        channel.setImage(image);
        channel.setText(text);
        if (StringUtils.isNotBlank(markdown)) {
            channel.setMarkdown(markdown);
            channel.setEditorType(Channel.EDITOR_TYPE_MARKDOWN);
        }
        Optional.ofNullable(nav).ifPresent(channel::setNav);
        Optional.ofNullable(views).ifPresent(channel::setViews);
        Optional.ofNullable(type).ifPresent(channel::setType);
        Optional.ofNullable(targetBlank).ifPresent(channel::setTargetBlank);
        Optional.ofNullable(created).ifPresent(channel::setCreated);
        Optional.ofNullable(modified).ifPresent(channel::setModified);
        channel.setSiteId(siteId);
        channel.setChannelModelId(channelModelId);
        channel.setArticleModelId(articleModelId);
        channelService.insert(channel, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        ImportData importData = new ImportData();
        importData.setId(snowflakeSequence.nextId());
        importData.setCurrentId(channel.getId().toString());
        importData.setOrigId(id);
        importData.setOrigParentId(parentId);
        importData.setTableName(ImportData.TABLE_CHANNEL);
        importData.setType(ImportData.TYPE_MIGRATION);
        insert(importData);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertArticle(ResultSet rs, List<String> columns, Long siteId, Long orgId, Long userId, Long channelId)
            throws SQLException {
        // String id = getRsStringRequired(rs, columns, "id_")
        String origChannelId = getRsStringRequired(rs, columns, "channel_id_");
        OffsetDateTime created = getRsOffsetDateTime(rs, columns, "created_");
        OffsetDateTime modified = getRsOffsetDateTime(rs, columns, "modified_");
        OffsetDateTime publishDate = getRsOffsetDateTime(rs, columns, "publish_date_");
        Short status = getRsShort(rs, columns, "status_");
        String title = getRsStringRequired(rs, columns, "title_");
        String subtitle = getRsString(rs, columns, "subtitle_");
        String fullTitle = getRsString(rs, columns, "full_title_");
        String linkUrl = getRsString(rs, columns, "link_url_");
        Boolean targetBlank = getRsBoolean(rs, columns, "target_blank_");
        String seoKeywords = getRsString(rs, columns, "seo_keywords_");
        String seoDescription = getRsString(rs, columns, "seo_description_");
        String source = getRsString(rs, columns, "source_");
        String imageListJson = getRsString(rs, columns, "image_list_json_");
        String fileListJson = getRsString(rs, columns, "file_list_json_");
        String image = getRsString(rs, columns, "image_");
        String video = getRsString(rs, columns, "video_");
        String videoOrig = getRsString(rs, columns, "video_orig_");
        Integer videoDuration = getRsInteger(rs, columns, "video_duration_");
        String audio = getRsString(rs, columns, "audio_");
        String audioOrig = getRsString(rs, columns, "audio_orig_");
        Integer audioDuration = getRsInteger(rs, columns, "audio_duration_");
        String file = getRsString(rs, columns, "file_");
        String fileName = getRsString(rs, columns, "file_name_");
        Long fileLength = getRsLong(rs, columns, "file_length_");
        String doc = getRsString(rs, columns, "doc_");
        String docOrig = getRsString(rs, columns, "doc_orig_");
        String docName = getRsString(rs, columns, "doc_name_");
        Long docLength = getRsLong(rs, columns, "doc_length_");
        String text = getRsString(rs, columns, "text_");
        String markdown = getRsString(rs, columns, "markdown_");
        Long views = getRsLong(rs, columns, "views_");

        Article article = new Article();
        article.setTitle(title);
        article.setSubtitle(subtitle);
        article.setFullTitle(fullTitle);
        article.setLinkUrl(linkUrl);
        Optional.ofNullable(targetBlank).ifPresent(article::setTargetBlank);
        Optional.ofNullable(status).ifPresent(article::setStatus);
        article.setSeoKeywords(seoKeywords);
        article.setSeoDescription(seoDescription);
        article.setSource(source);
        Optional.ofNullable(imageListJson).ifPresent(article::setImageListJson);
        Optional.ofNullable(fileListJson).ifPresent(article::setFileListJson);
        article.setImage(image);
        article.setVideo(video);
        article.setVideoOrig(videoOrig);
        article.setVideoDuration(videoDuration);
        article.setAudio(audio);
        article.setAudioOrig(audioOrig);
        article.setAudioDuration(audioDuration);
        article.setFile(file);
        article.setFileName(fileName);
        article.setFileLength(fileLength);
        article.setDoc(doc);
        article.setDocOrig(docOrig);
        article.setDocName(docName);
        article.setDocLength(docLength);
        if (StringUtils.isNotBlank(markdown)) {
            article.setMarkdown(markdown);
            article.setEditorType(Article.EDITOR_TYPE_MARKDOWN);
        }
        article.setMarkdown(markdown);
        article.setText(text);
        Optional.ofNullable(views).ifPresent(article::setViews);
        Optional.ofNullable(created).ifPresent(article::setCreated);
        Optional.ofNullable(modified).ifPresent(article::setModified);
        Optional.ofNullable(publishDate).ifPresent(article::setPublishDate);

        ImportData origImportData = mapper.selectByOrigId(origChannelId);
        if (origImportData != null) {
            article.setChannelId(Long.parseLong(origImportData.getCurrentId()));
        } else {
            article.setChannelId(channelId);
        }
        article.setSiteId(siteId);
        article.setOrgId(orgId);

        articleService.insert(article, userId, Collections.emptyList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByType(Short type) {
        mapper.deleteByType(type);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(ImportData bean) {
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ImportData bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public ImportData select(Long id) {
        return mapper.select(id);
    }

    public List<ImportData> selectList(ImportDataArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), ImportDataBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<ImportData> selectList(ImportDataArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<ImportData> selectPage(ImportDataArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }
}