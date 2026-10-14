package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.component.ArticleAccessGuard;
import com.ujcms.cms.core.composition.LayoutVariantCatalog;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.*;
import com.ujcms.cms.core.support.*;
import com.ujcms.cms.core.web.support.FrontendController;
import com.ujcms.cms.core.web.support.Redirects;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.web.PathResolver;
import com.ujcms.common.web.Servlets;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ujcms.cms.core.support.Frontends.LAYOUT_VARIANT;
import static com.ujcms.cms.core.support.Frontends.PAGE;
import static com.ujcms.cms.core.support.Frontends.TEMPLATE_NAME;
import static com.ujcms.cms.core.support.Frontends.PAGE_URL_RESOLVER;

/**
 * 前台文章 Controller
 *
 * @author PONY
 */
@FrontendController("frontendArticleController")
public class ArticleController {
    private final ArticleBufferService bufferService;
    private final ArticleService articleService;
    private final ChannelService channelService;
    private final ArticleAccessGuard accessGuard;
    private final LayoutVariantService layoutVariantService;
    private final PathResolver pathResolver;
    private final Props props;

    public ArticleController(ArticleBufferService bufferService, ArticleService articleService,
                             ChannelService channelService, ArticleAccessGuard accessGuard,
                             LayoutVariantService layoutVariantService, PathResolver pathResolver, Props props) {
        this.bufferService = bufferService;
        this.articleService = articleService;
        this.channelService = channelService;
        this.accessGuard = accessGuard;
        this.layoutVariantService = layoutVariantService;
        this.pathResolver = pathResolver;
        this.props = props;
    }

    /**
     * 文章路径段（固定链接 D7，docs/spec/permalinks/plan.md）：{@code {id}}、{@code {id}-{slug}}、{@code {slug}} 三形态
     * 均可解析，与站点当前形态无关；非规范形 301 至规范形，因此切换形态不破坏既有外链。
     */
    @GetMapping({UrlConstants.ARTICLE + "/{key:[\\w-]+}", UrlConstants.ARTICLE + "/{key:[\\w-]+}/{page:[\\d]+}"})
    public Object article(Site site, @PathVariable String key, @PathVariable(required = false) Integer page,
                          @RequestParam(defaultValue = "false") boolean preview,
                          HttpServletResponse response, Map<String, Object> modelMap) {
        User user = Contexts.findCurrentUser();
        Article article = validateArticle(resolveArticle(site, key), site, user, preview);
        if (StringUtils.isNotBlank(article.getLinkUrl())) {
            return "redirect:" + article.getUrl();
        }
        int validPage = Constants.validPage(page);
        if (!key.equals(article.urlKey())) {
            return Redirects.permanent(response, article.getUrl(validPage));
        }
        Channel channel = channelService.select(article.getChannelId());
        modelMap.put("article", article);
        modelMap.put("channel", channel);
        modelMap.put(PAGE, validPage);
        modelMap.put(PAGE_URL_RESOLVER, article);
        // 模板轨道渲染精度（template-track-variants D1/D2）：显式模板选择（文章级 > 栏目级）> 默认；
        // 活动变体按有效模板反查主题变体绑定。规则收口于共享 helper
        // （preview-url-resolver §4.2 D6），与 PreviewUrlResolver 永不漂移。
        LayoutVariantService.EffectiveTemplate effective =
                layoutVariantService.effectiveArticleTemplate(site, article, channel);
        if (effective.variant() != null) {
            modelMap.put(LAYOUT_VARIANT, LayoutVariantCatalog.SURFACE_ARTICLE + ":" + effective.variant().getValue());
        }
        // 渲染上下文钩子（页面构成 §10.2）：插槽标签按未组装模板名解析页面清单
        modelMap.put(TEMPLATE_NAME, effective.template());
        return site.assembleTemplate(effective.template());
    }

    @GetMapping({"/download-file/{id:[\\d]+}", "/download-file/{id:[\\d]+}/{index:[\\d]+}"})
    public void download(Site site, @PathVariable Long id, @PathVariable(required = false) Integer index,
                         @RequestParam long time, @NotNull String key,
                         @RequestParam(defaultValue = "false") boolean preview,
                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!Utils.validateDownloadKey(key, id, time, props.getDownloadSecret())) {
            throw new Http403Exception("Download Key Invalid");
        }
        int expires = 24 * 60 * 60 * 1000;
        if (System.currentTimeMillis() - time > expires) {
            throw new Http403Exception("Download Key Expired");
        }
        User user = Contexts.findCurrentUser();
        Article article = validateArticle(id, site, user, preview);
        String fileUrl;
        String fileName;
        if (index != null) {
            List<Article.ArticleFile> fileList = article.getFileList();
            if (index >= fileList.size()) {
                throw new Http404Exception("Article file not found. id=" + id + ", index=" + index);
            }
            Article.ArticleFile articleFile = fileList.get(index);
            fileUrl = articleFile.getUrl();
            fileName = articleFile.getName();
        } else {
            fileUrl = article.getFile();
            fileName = article.getFileName();
        }
        if (StringUtils.isBlank(fileUrl)) {
            throw new Http404Exception("Article file not exist. id=" + id);
        }
        bufferService.incrementDownloads(article.getId());
        FileHandler fileHandler = site.getConfig().getUploadStorage().getFileHandler(pathResolver);
        String filename = fileHandler.getName(fileUrl);
        // 不属于当前存储点（外部文件或上传文件后修改了储存点），直接重定向至文件
        if (filename == null) {
            response.sendRedirect(fileUrl);
            return;
        }
        if (!fileHandler.isFile(filename)) {
            throw new Http404Exception("Article file not found: " + fileUrl);
        }
        Servlets.setAttachmentHeader(request, response,
                Optional.ofNullable(fileName).filter(StringUtils::isNotBlank)
                        .orElseGet(() -> FilenameUtils.getName(fileUrl)));
        try (OutputStream output = response.getOutputStream()) {
            fileHandler.writeOutputStream(filename, output);
        }
    }

    /**
     * 全数字 → 按 ID，未命中再按别名（别名可为纯数字）；{@code {id}-{slug}} → 按 ID，未命中整段按别名
     * （别名可以数字开头，如 {@code 2024-report}）；其余按别名。ID 形态永远可解析，是形态切换的安全网
     */
    private Article resolveArticle(Site site, String key) {
        Article article = null;
        Matcher matcher = ID_PREFIX.matcher(key);
        if (matcher.matches()) {
            try {
                article = articleService.select(Long.valueOf(matcher.group(1)));
            } catch (NumberFormatException e) {
                // 超长数字串不是 ID，按别名解析
            }
        }
        if (article == null) {
            article = articleService.findBySiteIdAndSlug(site.getId(), key);
        }
        if (article == null) {
            throw new Http404Exception("Article not found. key=" + key);
        }
        return article;
    }

    private static final Pattern ID_PREFIX = Pattern.compile("^(\\d+)(?:-.*)?$");

    private Article validateArticle(Long id, Site site, User user, boolean preview) {
        Article article = articleService.select(id);
        if (article == null) {
            throw new Http404Exception("Article not found. ID=" + id);
        }
        return validateArticle(article, site, user, preview);
    }

    private Article validateArticle(Article article, Site site, User user, boolean preview) {
        if (!site.getId().equals(article.getSiteId())) {
            throw new Http404Exception("error.notInSite",
                    String.valueOf(article.getSiteId()), String.valueOf(site.getId()));
        }
        accessGuard.check(article, user, preview);
        return article;
    }
}
