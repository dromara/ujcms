package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleBuffer;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.*;
import com.ujcms.cms.core.support.*;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.commons.file.FileHandler;
import com.ujcms.commons.web.PathResolver;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.exception.Http403Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.core.support.Frontends.PAGE;
import static com.ujcms.cms.core.support.Frontends.PAGE_URL_RESOLVER;
import static com.ujcms.cms.core.web.api.ArticleController.checkAccessPermission;

/**
 * 前台文章 Controller
 *
 * @author PONY
 */
@Controller("frontendArticleController")
public class ArticleController {
    private final ArticleBufferService bufferService;
    private final ArticleService articleService;
    private final ChannelService channelService;
    private final OrgService orgService;
    private final GroupService groupService;
    private final SiteResolver siteResolver;
    private final PathResolver pathResolver;
    private final Props props;

    public ArticleController(ArticleBufferService bufferService, ArticleService articleService,
                             ChannelService channelService, OrgService orgService, GroupService groupService,
                             SiteResolver siteResolver, PathResolver pathResolver, Props props) {
        this.bufferService = bufferService;
        this.articleService = articleService;
        this.channelService = channelService;
        this.groupService = groupService;
        this.siteResolver = siteResolver;
        this.pathResolver = pathResolver;
        this.props = props;
        this.orgService = orgService;
    }

    @GetMapping({UrlConstants.ARTICLE + "/{id}", UrlConstants.ARTICLE + "/{id}/{page:[\\d]+}",
            UrlConstants.ARTICLE + "/{id}/{alias:[\\w-]+}", UrlConstants.ARTICLE + "/{id}/{alias:[\\w-]+}/{page:[\\d]+}",
            "/{subDir:[\\w-]+}" + UrlConstants.ARTICLE + "/{id}", "/{subDir:[\\w-]+}" + UrlConstants.ARTICLE + "/{id}_{page:[\\d]+}",
            "/{subDir:[\\w-]+}" + UrlConstants.ARTICLE + "/{id}/{alias:[\\w-]+}",
            "/{subDir:[\\w-]+}" + UrlConstants.ARTICLE + "/{id}/{alias:[\\w-]+}/{page:[\\d]+}"})
    public String article(@PathVariable Long id, @PathVariable(required = false) String subDir,
                          @PathVariable(required = false) String alias, @PathVariable(required = false) Integer page,
                          @RequestParam(defaultValue = "false") boolean preview,
                          HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        User user = Contexts.findCurrentUser();
        Article article = validateArticle(id, site, user, preview);
        if (StringUtils.isNotBlank(article.getLinkUrl())) {
            return "redirect:" + article.getUrl();
        }
        if (!StringUtils.equals(article.getExt().getAlias(), alias)) {
            return "redirect:" + article.getUrl();
        }
        modelMap.put("article", article);
        modelMap.put("channel", channelService.select(article.getChannelId()));
        modelMap.put(PAGE, Constants.validPage(page));
        modelMap.put(PAGE_URL_RESOLVER, article);
        return article.getTemplate();
    }

    @GetMapping({"/download-file/{id:[\\d]+}", "/download-file/{id:[\\d]+}/{index:[\\d]+}",
            "/{subDir:[\\w-]+}/download-file/{id:[\\d]+}", "/{subDir:[\\w-]+}/download-file/{id:[\\d]+}/{index:[\\d]+}"})
    public void download(@PathVariable Long id, @PathVariable(required = false) Integer index,
                         @PathVariable(required = false) String subDir,
                         @RequestParam long time, @NotNull String key,
                         @RequestParam(defaultValue = "false") boolean preview,
                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        Site site = siteResolver.resolve(request, subDir);
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
        ArticleBuffer buffer = ArticleBuffer.of(article);
        int downloads = buffer.getDownloads() + 1;
        buffer.setDownloads(downloads);
        bufferService.update(buffer);
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

    private Article validateArticle(Long id, Site site, User user, boolean preview) {
        Article article = articleService.select(id);
        if (article == null) {
            throw new Http404Exception("Article not found. ID=" + id);
        }
        if (!site.getId().equals(article.getSiteId())) {
            throw new Http404Exception("error.notInSite",
                    String.valueOf(article.getSiteId()), String.valueOf(site.getId()));
        }
        checkAccessPermission(article, user, groupService, channelService, orgService, preview);
        return article;
    }
}
