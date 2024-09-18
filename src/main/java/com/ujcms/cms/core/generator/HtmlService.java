package com.ujcms.cms.core.generator;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleExt;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.mapper.ArticleExtMapper;
import com.ujcms.cms.core.mapper.ChannelExtMapper;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.commons.file.FileHandler;
import com.ujcms.commons.web.PathResolver;
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

import static com.ujcms.cms.core.support.Frontends.PAGE_SIZE;

/**
 * HTML Service
 *
 * @author PONY
 */
@Service
public class HtmlService {
    private final SiteMapper siteMapper;
    private final ArticleExtMapper articleExtMapper;
    private final ChannelExtMapper channelExtMapper;
    private final PathResolver pathResolver;
    private final Configuration configuration;
    private final FreeMarkerProperties properties;

    public HtmlService(SiteMapper siteMapper, ArticleExtMapper articleExtMapper, ChannelExtMapper channelExtMapper,
                       PathResolver pathResolver, Configuration configuration, FreeMarkerProperties properties) {
        this.siteMapper = siteMapper;
        this.articleExtMapper = articleExtMapper;
        this.channelExtMapper = channelExtMapper;
        this.pathResolver = pathResolver;
        this.configuration = configuration;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateHomeHtml(Site site) {
        deleteHomeHtml(site);
        if (!site.isHtmlEnabled()) {
            return;
        }
        Map<String, Object> dataModel = new HashMap<>(16);
        Site defaultSite = getDefaultSite(site.getConfig().getDefaultSiteId());
        Frontends.setDate(dataModel, site, defaultSite, site.getUrl(), 1, null);
        dataModel.put(PAGE_SIZE, site.getPageSize());
        dataModel.put("isHome", true);
        try {
            Contexts.setMobile(false);
            String filename = site.getNormalStaticPath();
            FileHandler fileHandler = site.getConfig().getHtmlStorage().getFileHandler(pathResolver);
            fileHandler.store(filename, resolveTemplate(site.getTemplate()), dataModel);
            site.setStaticFile(filename);

            if (site.hasMobileTheme()) {
                Contexts.setMobile(true);
                filename = site.getMobileStaticPath();
                fileHandler.store(filename, resolveTemplate(site.getTemplate()), dataModel);
                site.setMobileStaticFile(filename);
            }
            siteMapper.update(site);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            Contexts.clearMobile();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteHomeHtml(Site site) {
        FileHandler fileHandler = site.getConfig().getHtmlStorage().getFileHandler(pathResolver);
        Optional.ofNullable(site.getStaticFile()).ifPresent(filename -> {
            fileHandler.deleteFileAndEmptyParentDir(filename);
            site.setStaticFile(null);
        });
        Optional.ofNullable(site.getMobileStaticFile()).ifPresent(filename -> {
            fileHandler.deleteFileAndEmptyParentDir(filename);
            site.setMobileStaticFile(null);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateArticleHtml(Article article) {
        deleteArticleHtml(article);
        // 跳转连接不用静态化
        if (!article.getSite().isHtmlEnabled()
                || !StringUtils.isBlank(article.getLinkUrl())
                || !article.isNormal()) {
            return;
        }
        Site site = article.getSite();
        Map<String, Object> dataModel = new HashMap<>(16);
        dataModel.put("article", article);
        dataModel.put("channel", article.getChannel());
        int page = 1;
        Site defaultSite = getDefaultSite(site.getConfig().getDefaultSiteId());
        Frontends.setDate(dataModel, site, defaultSite, article.getUrl(page), page, article);
        try {
            ArticleExt ext = article.getExt();
            Contexts.setMobile(false);
            String filename = article.getNormalStaticPath(page);
            FileHandler fileHandler = site.getConfig().getHtmlStorage().getFileHandler(pathResolver);
            fileHandler.store(filename, resolveTemplate(article.getTemplate()), dataModel);
            ext.setStaticFile(filename);

            if (site.hasMobileTheme()) {
                Contexts.setMobile(true);
                filename = article.getMobileStaticPath(page);
                fileHandler.store(filename, resolveTemplate(article.getTemplate()), dataModel);
                ext.setMobileStaticFile(filename);
            }
            articleExtMapper.update(ext);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            Contexts.clearMobile();
        }
    }

    public void deleteArticleHtml(Article article) {
        FileHandler fileHandler = article.getSite().getConfig().getHtmlStorage().getFileHandler(pathResolver);
        Optional.ofNullable(article.getStaticFile()).ifPresent(filename -> {
            fileHandler.deleteFileAndEmptyParentDir(filename);
            article.setStaticFile(null);
        });
        Optional.ofNullable(article.getMobileStaticFile()).ifPresent(filename -> {
            fileHandler.deleteFileAndEmptyParentDir(filename);
            article.setMobileStaticFile(null);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateChannelHtml(Channel channel) {
        deleteChannelHtml(channel);

        if (!channel.getSite().isHtmlEnabled()) {
            return;
        }
        // 跳转连接不用静态化
        if (channel.isLink()) {
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
            throw new IllegalStateException(e);
        } finally {
            Contexts.clearMobile();
        }
    }

    private void doMakeChannelHtml(Channel channel) throws IOException {
        Site site = channel.getSite();
        Site defaultSite = getDefaultSite(site.getConfig().getDefaultSiteId());
        Map<String, Object> dataModel = new HashMap<>(16);
        dataModel.put("channel", channel);
        dataModel.put(PAGE_SIZE, channel.getPageSize());

        FileHandler fileHandler = site.getConfig().getHtmlStorage().getFileHandler(pathResolver);
        Template template = resolveTemplate(channel.getTemplate());
        int page = 1;
        int totalPages = 1;
        int maxPages = site.getHtml().getListPages();
        for (; page <= totalPages && page <= maxPages; page += 1) {
            Frontends.setDate(dataModel, site, defaultSite, channel.getUrl(page), page, channel);
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
        FileHandler fileHandler = site.getConfig().getHtmlStorage().getFileHandler(pathResolver);
        Optional.ofNullable(channel.getStaticFile()).ifPresent(filename -> {
            deleteHtmlList(fileHandler, filename);
            channel.setStaticFile(null);
        });
        Optional.ofNullable(channel.getMobileStaticFile()).ifPresent(filename -> {
            deleteHtmlList(fileHandler, filename);
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
        String baseFilename = filename.substring(0, filename.length() - Site.Html.SUFFIX.length());
        // 第一次删除无分页的索引页，第二次要从第二页开始删除，所以page=2，
        for (int page = 2; fileHandler.deleteFileAndEmptyParentDir(filename); page += 1) {
            filename = baseFilename + "_" + page + Site.Html.SUFFIX;
        }
    }

    private Template resolveTemplate(String template) throws IOException {
        return configuration.getTemplate(properties.getPrefix() + template + properties.getSuffix());
    }

    private Site getDefaultSite(Long defaultSiteId) {
        return Optional.ofNullable(siteMapper.select(defaultSiteId))
                .orElseThrow(() -> new IllegalStateException("default site not exist. id: " + defaultSiteId));
    }
}
