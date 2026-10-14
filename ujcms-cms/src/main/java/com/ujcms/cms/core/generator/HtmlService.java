package com.ujcms.cms.core.generator;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleExt;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.composition.ThemeSettingsService;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.mapper.ArticleExtMapper;
import com.ujcms.cms.core.mapper.ChannelExtMapper;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.service.LayoutVariantService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.web.PathResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerProperties;
import org.jspecify.annotations.Nullable;
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
    private final ThemeSettingsService themeSettingsService;
    private final LayoutVariantService layoutVariantService;

    public HtmlService(SiteMapper siteMapper, ArticleExtMapper articleExtMapper, ChannelExtMapper channelExtMapper,
                       PathResolver pathResolver, Configuration configuration, FreeMarkerProperties properties,
                       ThemeSettingsService themeSettingsService, LayoutVariantService layoutVariantService) {
        this.siteMapper = siteMapper;
        this.articleExtMapper = articleExtMapper;
        this.channelExtMapper = channelExtMapper;
        this.pathResolver = pathResolver;
        this.configuration = configuration;
        this.properties = properties;
        this.themeSettingsService = themeSettingsService;
        this.layoutVariantService = layoutVariantService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateHomeHtml(Site site) {
        if (!site.isHtmlEnabled()) {
            return;
        }
        Map<String, Object> dataModel = new HashMap<>(16);
        Site defaultSite = getDefaultSite(site.getConfig().getDefaultSiteId());
        Frontends.setDate(dataModel, site, defaultSite, site.getUrl(), 1, null);
        dataModel.put(Frontends.THEME, themeSettingsService.themeModel(site.getId(), site.getTheme()));
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
            siteMapper.updateBase(site);
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

    /**
     * 生成文章静态页。只回写静态页路径两列（{@link ArticleExtMapper#updateStaticFile}），不回写整行扩展数据：
     * 传进来的文章可能是任务排队之前读出的，整行写回会把期间编辑保存的正文、标题盖掉
     * （docs/spec/article-management/plan.md D9）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateArticleHtml(Article article) {
        boolean hadFile = article.getStaticFile() != null || article.getMobileStaticFile() != null;
        deleteArticleHtml(article);
        Site site = siteMapper.select(article.getSiteId());
        if (site == null) {
            return;
        }
        // 跳转连接不用静态化
        if (!site.isHtmlEnabled()
                || !StringUtils.isBlank(article.getLinkUrl())
                || !article.isNormal()) {
            // 页面已删除，库里记着的路径随之清掉
            if (hadFile) {
                articleExtMapper.updateStaticFile(article.getId(), null, null);
            }
            return;
        }
        Map<String, Object> dataModel = new HashMap<>(16);
        dataModel.put("article", article);
        dataModel.put("channel", article.getChannel());
        int page = 1;
        Site defaultSite = getDefaultSite(site.getConfig().getDefaultSiteId());
        Frontends.setDate(dataModel, site, defaultSite, article.getUrl(page), page, article);
        dataModel.put(Frontends.THEME, themeSettingsService.themeModel(site.getId(), site.getTheme()));
        try {
            ArticleExt ext = article.getExt();
            Contexts.setMobile(false);
            String filename = article.getNormalStaticPath(page);
            FileHandler fileHandler = site.getConfig().getHtmlStorage().getFileHandler(pathResolver);
            // 静态化与动态渲染同规（template-track-variants Q2）：经共享 helper 解析有效模板，
            // 孤立显式选择回退默认而非模板缺失中断。移动端另行解析（移动主题的文件与目录不同）。
            fileHandler.store(filename, resolveTemplate(articleTemplate(site, article)), dataModel);
            ext.setStaticFile(filename);

            if (site.hasMobileTheme()) {
                Contexts.setMobile(true);
                filename = article.getMobileStaticPath(page);
                fileHandler.store(filename, resolveTemplate(articleTemplate(site, article)), dataModel);
                ext.setMobileStaticFile(filename);
            }
            articleExtMapper.updateStaticFile(article.getId(), ext.getStaticFile(), ext.getMobileStaticFile());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            Contexts.clearMobile();
        }
    }

    public void deleteArticleHtml(Article article) {
        FileHandler fileHandler = article.getSite().getConfig().getHtmlStorage().getFileHandler(pathResolver);
        // 13.1 之前，扩展栏目与推送产生的子行把源文章的静态页路径也复制了过来。按这个路径删除，删掉的是源文章的页面：
        // 路径与源文章相同的不删文件，只清掉子行自己记着的路径（新建的子行已不再复制，见 ArticleExt#copyForChild）
        Article src = article.getSrcId() != null ? article.getSrc() : null;
        Optional.ofNullable(article.getStaticFile()).ifPresent(filename -> {
            if (src == null || !filename.equals(src.getStaticFile())) {
                fileHandler.deleteFileAndEmptyParentDir(filename);
            }
            article.setStaticFile(null);
        });
        Optional.ofNullable(article.getMobileStaticFile()).ifPresent(filename -> {
            if (src == null || !filename.equals(src.getMobileStaticFile())) {
                fileHandler.deleteFileAndEmptyParentDir(filename);
            }
            article.setMobileStaticFile(null);
        });
    }

    /**
     * 生成栏目静态页。须传入完整读取的栏目（{@code ChannelService.select}）：列表、关联查询带出的栏目没有扩展数据、
     * 子栏目和正文，模板一用到就出错。只回写静态页路径两列，不回写栏目正文（D9）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateChannelHtml(Channel channel) {
        deleteChannelHtml(channel);
        Site site = siteMapper.select(channel.getSiteId());
        if (site == null || !channel.getSite().isHtmlEnabled()) {
            return;
        }
        // 跳转连接不用静态化
        if (channel.isLink()) {
            return;
        }
        try {
            Contexts.setMobile(false);
            doMakeChannelHtml(channel, site);
            // 手机端模板和PC端模板一样，则不用另外生成手机端页面
            if (channel.getSite().hasMobileTheme()) {
                Contexts.setMobile(true);
                doMakeChannelHtml(channel, site);
            }
            channelExtMapper.updateStaticFile(channel.getId(), channel.getStaticFile(), channel.getMobileStaticFile());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            Contexts.clearMobile();
        }
    }

    private void doMakeChannelHtml(Channel channel, Site site) throws IOException {
        Site defaultSite = getDefaultSite(site.getConfig().getDefaultSiteId());
        Map<String, Object> dataModel = new HashMap<>(16);
        dataModel.put("channel", channel);
        dataModel.put(PAGE_SIZE, channel.getPageSize());
        dataModel.put(Frontends.THEME, themeSettingsService.themeModel(site.getId(), site.getTheme()));

        FileHandler fileHandler = site.getConfig().getHtmlStorage().getFileHandler(pathResolver);
        // 静态化与动态渲染同规（template-track-variants Q2）：孤立显式选择回退默认。
        // 本方法按 Contexts.isMobile() 各调用一次，主题与回退判定随之解析。
        Template template = resolveTemplate(site.assembleTemplate(
                layoutVariantService.effectiveChannelTemplate(site, channel).template()));
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

    /**
     * 文章静态页的已组装模板路径：经共享 helper 解析有效模板（显式级联 &gt; 默认，孤立回退，
     * template-track-variants Q2），按当前 {@code Contexts.isMobile()} 组装主题路径。
     */
    private String articleTemplate(Site site, Article article) {
        return site.assembleTemplate(
                layoutVariantService.effectiveArticleTemplate(site, article, article.getChannel()).template());
    }

    private Site getDefaultSite(Long defaultSiteId) {
        return Optional.ofNullable(siteMapper.select(defaultSiteId))
                .orElseThrow(() -> new IllegalStateException("default site not exist. id: " + defaultSiteId));
    }
}
