package com.ujcms.core.generator;

import com.ofwise.util.file.FileHandler;
import com.ofwise.util.web.PathResolver;
import com.ujcms.core.domain.Article;
import com.ujcms.core.domain.ArticleExt;
import com.ujcms.core.domain.Channel;
import com.ujcms.core.domain.Site;
import com.ujcms.core.domain.Storage;
import com.ujcms.core.mapper.ArticleExtMapper;
import com.ujcms.core.mapper.ChannelExtMapper;
import com.ujcms.core.mapper.SiteMapper;
import com.ujcms.core.support.Contexts;
import com.ujcms.core.support.Frontends;
import com.ujcms.core.web.support.Directives;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.core.support.Frontends.PAGE_SIZE;

/**
 * HTML Service
 *
 * @author PONY
 */
@Service
public class HtmlService {
    private ArticleExtMapper articleExtMapper;
    private ChannelExtMapper channelExtMapper;
    private SiteMapper siteMapper;
    private PathResolver pathResolver;
    private Configuration configuration;
    private FreeMarkerProperties properties;

    public HtmlService(ArticleExtMapper articleExtMapper, ChannelExtMapper channelExtMapper, SiteMapper siteMapper,
                       PathResolver pathResolver, Configuration configuration, FreeMarkerProperties properties) {
        this.articleExtMapper = articleExtMapper;
        this.channelExtMapper = channelExtMapper;
        this.siteMapper = siteMapper;
        this.pathResolver = pathResolver;
        this.configuration = configuration;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateHomeHtml(Site site) {
        deleteHomeHtml(site);

        if (!site.getHtml().isEnabled()) {
            return;
        }
        Map<String, Object> dataModel = new HashMap<>(16);
        Frontends.setDate(dataModel, site, site.getUrl(), 1, null);
        dataModel.put(PAGE_SIZE, site.getPageSize());
        dataModel.put("isHome", true);
        try {
            Contexts.setMobile(false);
            String filename = site.getNormalStaticPath();
            FileHandler fileHandler = site.getHtmlStorage().getFileHandler(pathResolver);
            fileHandler.store(filename, resolveTemplate(site.getTemplate()), dataModel);
            site.setStaticFile(filename);

            if (site.hasMobileTheme()) {
                Contexts.setMobile(true);
                filename = site.getMobileStaticPath();
                fileHandler = site.getMobileHtmlStorage().getFileHandler(pathResolver);
                fileHandler.store(filename, resolveTemplate(site.getTemplate()), dataModel);
                site.setMobileStaticFile(filename);
            }
            siteMapper.update(site);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Contexts.clearMobile();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteHomeHtml(Site site) {
        Optional.ofNullable(site.getStaticFile()).ifPresent(filename -> {
            site.getHtmlStorage().getFileHandler(pathResolver).delete(filename);
            site.setStaticFile(null);
        });
        Optional.ofNullable(site.getMobileStaticFile()).ifPresent(filename -> {
            site.getMobileHtmlStorage().getFileHandler(pathResolver).delete(filename);
            site.setMobileStaticFile(null);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateArticleHtml(Article article) {
        deleteArticleHtml(article);

        if (!article.getSite().getHtml().isEnabled()) {
            return;
        }
        // 跳转连接不用静态化
        if (StringUtils.isNotBlank(article.getLinkUrl())) {
            return;
        }
        Site site = article.getSite();
        Map<String, Object> dataModel = new HashMap<>(16);
        dataModel.put("article", article);
        dataModel.put("channel", article.getChannel());
        int page = 1;
        Frontends.setDate(dataModel, site, article.getUrl(page), page, article);
        try {
            ArticleExt ext = article.getExt();
            Contexts.setMobile(false);
            String filename = article.getNormalStaticPath(page);
            FileHandler fileHandler = site.getHtmlStorage().getFileHandler(pathResolver);
            fileHandler.store(filename, resolveTemplate(article.getTemplate()), dataModel);
            ext.setStaticFile(filename);

            if (site.hasMobileTheme()) {
                Contexts.setMobile(true);
                filename = article.getMobileStaticPath(page);
                fileHandler = site.getMobileHtmlStorage().getFileHandler(pathResolver);
                fileHandler.store(filename, resolveTemplate(article.getTemplate()), dataModel);
                ext.setMobileStaticFile(filename);
            }
            articleExtMapper.update(ext);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Contexts.clearMobile();
        }
    }

    public void deleteArticleHtml(Article article) {
        Optional.ofNullable(article.getStaticFile()).ifPresent(filename -> {
            article.getSite().getHtmlStorage().getFileHandler(pathResolver).delete(filename);
            article.setStaticFile(null);
        });
        Optional.ofNullable(article.getMobileStaticFile()).ifPresent(filename -> {
            article.getSite().getMobileHtmlStorage().getFileHandler(pathResolver).delete(filename);
            article.setMobileStaticFile(null);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateChannelHtml(Channel channel) {
        deleteChannelHtml(channel);

        if (!channel.getSite().getHtml().isEnabled()) {
            return;
        }
        // 跳转连接不用静态化
        if (StringUtils.isNotBlank(channel.getLinkUrl())) {
            return;
        }
        try {
            Contexts.setMobile(false);
            doMakeChannelHtml(channel);
            // 手机端模板和PC端模板一样，则不用另外生成手机端页面
            if (channel.getSite().hasMobileTheme()) {
                Contexts.setMobile(true);
                doMakeChannelHtml(channel);
            }
            channelExtMapper.update(channel.getExt());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Contexts.clearMobile();
        }
    }

    private void doMakeChannelHtml(Channel channel) throws IOException {
        Site site = channel.getSite();
        Map<String, Object> dataModel = new HashMap<>(16);
        dataModel.put("channel", channel);
        dataModel.put(PAGE_SIZE, channel.getPageSize());

        Storage storage = Contexts.isMobile() ? site.getMobileHtmlStorage() : site.getHtmlStorage();
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        Template template = resolveTemplate(channel.getTemplate());
        int page = 1, totalPages = 1, maxPages = site.getHtml().getListPages();
        for (; page <= totalPages && page <= maxPages; page += 1) {
            Frontends.setDate(dataModel, site, channel.getUrl(page), page, channel);
            String filename = Contexts.isMobile() ?
                    channel.getMobileStaticPath(page) : channel.getNormalStaticPath(page);
            // ArticlePage 标签会设置 totalPages。先清除，执行模板后再获取，以免获取到无效数据。
            Directives.clearTotalPages();
            fileHandler.store(filename, template, dataModel);
            totalPages = Directives.getTotalPages();
            if (page == 1) {
                if (Contexts.isMobile()) {
                    channel.setMobileStaticFile(filename);
                } else {
                    channel.setStaticFile(filename);
                }
            }
        }
    }

    public void deleteChannelHtml(Channel channel) {
        Site site = channel.getSite();
        Optional.ofNullable(channel.getStaticFile()).ifPresent(filename -> {
            deleteHtmlList(site.getHtmlStorage().getFileHandler(pathResolver), filename);
            channel.setStaticFile(null);
        });
        Optional.ofNullable(channel.getMobileStaticFile()).ifPresent(filename -> {
            deleteHtmlList(site.getMobileHtmlStorage().getFileHandler(pathResolver), filename);
            channel.setMobileStaticFile(null);
        });
    }

    private void deleteHtmlList(FileHandler fileHandler, @Nullable String filename) {
        if (StringUtils.isBlank(filename)) {
            return;
        }
        if (!filename.endsWith(Site.Html.SUFFIX)) {
            throw new IllegalArgumentException("filename must ends with '.html': " + filename);
        }
        String baseFilename = filename.substring(0, filename.length() - Site.Html.SUFFIX.length() - 1);
        for (int page = 1; fileHandler.delete(filename); page += 1) {
            filename = baseFilename + "_" + page + Site.Html.SUFFIX;
        }
    }

    private Template resolveTemplate(String template) throws IOException {
        return configuration.getTemplate(properties.getPrefix() + template + properties.getSuffix());
    }
}
