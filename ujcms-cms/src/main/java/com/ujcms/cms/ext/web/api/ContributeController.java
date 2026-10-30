package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.component.CaptchaGuard;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.service.ContributeService;
import com.ujcms.cms.ext.service.ContributeService.Content;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Length;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.common.db.MyBatis.springPage;

/**
 * 会员投稿 Controller（docs/spec/member-contribute/plan.md）。
 * <p>
 * 会员写入面：本人投稿的增删改查（全部状态）与可投稿栏目。登录是门禁，归属全部在服务端按 {@code userId==member.id}
 * 强制，从不采信请求参数。站点由请求里的 {@code siteId} 解析（与留言、表单一致），栏目须属于该站点
 *
 * @author PONY
 */
@Tag(name = "会员投稿接口")
@RestController
@RequestMapping({API + "/contribute", FRONTEND_API + "/contribute"})
public class ContributeController {
    private final ContributeService service;
    private final ChannelService channelService;
    private final SiteResolver siteResolver;
    private final CaptchaGuard captchaGuard;

    public ContributeController(ContributeService service, ChannelService channelService,
                                SiteResolver siteResolver, CaptchaGuard captchaGuard) {
        this.service = service;
        this.channelService = channelService;
        this.siteResolver = siteResolver;
        this.captchaGuard = captchaGuard;
    }

    @Operation(summary = "可投稿栏目_ContributeChannels。当前登录用户在本站可投稿的栏目，按栏目树顺序；站点未开放投稿为空。"
            + "未登录返回 401")
    @GetMapping("/channels")
    public List<ChannelOption> channels(@RequestParam(required = false) Long siteId, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Site site = siteResolver.resolve(request, siteId);
        return service.listChannels(site, user).stream()
                .map(channel -> new ChannelOption(channel.getId(), channel.getName(), channel.getDepth()))
                .toList();
    }

    @Operation(summary = "我的投稿列表_ContributeList。当前登录用户在本站的投稿（全部状态），未登录返回 401")
    @Parameter(in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, name = "status",
            description = "状态过滤。不传获取全部状态", schema = @Schema(type = "integer", format = "int16"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Article> list(@RequestParam(required = false) Long siteId,
                              @RequestParam(required = false) Short status, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Site site = siteResolver.resolve(request, siteId);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return service.myList(site, user, status, Directives.getOffset(params), Directives.getLimit(params));
    }

    @Operation(summary = "我的投稿分页_ContributePage。当前登录用户在本站的投稿（全部状态），未登录返回 401。"
            + "totalElements 可用于仪表盘统计")
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<Article> page(@RequestParam(required = false) Long siteId,
                              @RequestParam(required = false) Short status, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Site site = siteResolver.resolve(request, siteId);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return springPage(service.myPage(site, user, status, Directives.getPage(params),
                Directives.getPageSize(params)));
    }

    @Operation(summary = "我的投稿详情_ContributeShow。获取本人投稿用于编辑，非本人返回 404，未登录返回 401")
    @JsonView(Views.Whole.class)
    @GetMapping("/{id:\\d+}")
    public Article show(@PathVariable Long id, @RequestParam(required = false) Long siteId,
                        HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Site site = siteResolver.resolve(request, siteId);
        Article article = service.getOwn(site, user, id);
        if (article == null) {
            throw new Http404Exception(Article.NOT_FOUND + id);
        }
        return article;
    }

    @Operation(summary = "投稿_ContributeCreate。站点未开放投稿返回 404，栏目不接受该用户投稿返回 403；"
            + "服务端设定全部可信字段，投稿一律待审核。未登录返回 401")
    @PostMapping
    public ResponseEntity<Responses.Body> create(@RequestBody @Valid ContributeParams params,
                                                 HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Site site = siteResolver.resolve(request, params.getSiteId());
        requireOpen(site);
        Long channelId = params.getChannelId();
        if (channelId == null) {
            throw new Http400Exception("Channel id is required.");
        }
        Channel channel = channelService.selectInSite(channelId, site.getId());
        if (!service.canContribute(site, user, channel)) {
            throw new Http403Exception("error.contribute.channelNotAllowed");
        }
        String captchaError = captchaGuard.verify(request, CaptchaGuard.SCOPE_CONTRIBUTE, params.getCaptchaToken(),
                params.getCaptcha(), params.getWebsite());
        if (captchaError != null) {
            return Responses.failure(request, captchaError);
        }
        Article article = service.submit(site, user, channel, params.toContent());
        return Responses.ok(new ContributeResult(article.getId(), article.getStatus()));
    }

    @Operation(summary = "修改投稿_ContributeUpdate。本人且状态可修改（已退回、待审核未进流程），否则返回 404/400；"
            + "保存即重新提交审核。未登录返回 401")
    @PutMapping("/{id:\\d+}")
    public ResponseEntity<Responses.Body> update(@PathVariable Long id, @RequestBody @Valid ContributeParams params,
                                                 HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Site site = siteResolver.resolve(request, params.getSiteId());
        requireOpen(site);
        String captchaError = captchaGuard.verify(request, CaptchaGuard.SCOPE_CONTRIBUTE, params.getCaptchaToken(),
                params.getCaptcha(), params.getWebsite());
        if (captchaError != null) {
            return Responses.failure(request, captchaError);
        }
        Article article = service.update(site, user, id, params.toContent());
        return Responses.ok(new ContributeResult(article.getId(), article.getStatus()));
    }

    @Operation(summary = "删除投稿_ContributeDelete。本人且状态可修改，否则返回 404/400；级联删除评论、收藏等。未登录返回 401")
    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Responses.Body> remove(@PathVariable Long id, @RequestParam(required = false) Long siteId,
                                                 HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Site site = siteResolver.resolve(request, siteId);
        service.delete(site, user, id);
        return Responses.ok();
    }

    private void requireOpen(Site site) {
        if (!service.isOpen(site)) {
            throw new Http404Exception("error.contribute.disabled");
        }
    }

    /**
     * 可投稿栏目
     *
     * @param id    栏目ID
     * @param name  栏目名称
     * @param depth 栏目层级（根为 0），供表单缩进
     */
    public record ChannelOption(Long id, String name, Short depth) {
    }

    /**
     * 投稿结果
     *
     * @param id     文章ID
     * @param status 文章状态
     */
    public record ContributeResult(@Nullable Long id, @Nullable Short status) {
    }

    @Schema(name = "ContributeController.ContributeParams", description = "投稿参数")
    public static class ContributeParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "站点ID。子目录站点必传")
        @Nullable
        private Long siteId;
        @Schema(description = "栏目ID。新投稿必传，修改时忽略")
        @Nullable
        private Long channelId;
        @NotBlank
        @Length(max = 150)
        @Schema(description = "标题")
        private String title = "";
        @Length(max = 1000)
        @Schema(description = "摘要")
        @Nullable
        private String seoDescription;
        @Length(max = 50)
        @Schema(description = "作者")
        @Nullable
        private String author;
        @Length(max = 255)
        @Schema(description = "封面图片URL。须是本人上传的图片附件")
        @Nullable
        private String image;
        @Size(max = 200000)
        @Schema(description = "正文（HTML）")
        @Nullable
        private String text;
        @Size(max = 20)
        @Schema(description = "标签")
        private List<@Length(max = 50) String> tagNames = Collections.emptyList();
        @Schema(description = "自定义字段。只有简单类型的字段被采用")
        private Map<String, Object> customs = Collections.emptyMap();
        @Schema(description = "验证码")
        @Nullable
        private String captcha;
        @Schema(description = "验证码令牌")
        @Nullable
        private String captchaToken;
        @Schema(description = "蜜罐字段，须为空")
        @Nullable
        private String website;

        public Content toContent() {
            return new Content(title, seoDescription, author, image, text, tagNames, customs);
        }

        @Nullable
        public Long getSiteId() {
            return siteId;
        }

        public void setSiteId(@Nullable Long siteId) {
            this.siteId = siteId;
        }

        @Nullable
        public Long getChannelId() {
            return channelId;
        }

        public void setChannelId(@Nullable Long channelId) {
            this.channelId = channelId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        @Nullable
        public String getSeoDescription() {
            return seoDescription;
        }

        public void setSeoDescription(@Nullable String seoDescription) {
            this.seoDescription = seoDescription;
        }

        @Nullable
        public String getAuthor() {
            return author;
        }

        public void setAuthor(@Nullable String author) {
            this.author = author;
        }

        @Nullable
        public String getImage() {
            return image;
        }

        public void setImage(@Nullable String image) {
            this.image = image;
        }

        @Nullable
        public String getText() {
            return text;
        }

        public void setText(@Nullable String text) {
            this.text = text;
        }

        public List<String> getTagNames() {
            return tagNames;
        }

        public void setTagNames(List<String> tagNames) {
            this.tagNames = tagNames;
        }

        public Map<String, Object> getCustoms() {
            return customs;
        }

        public void setCustoms(Map<String, Object> customs) {
            this.customs = customs;
        }

        @Nullable
        public String getCaptcha() {
            return captcha;
        }

        public void setCaptcha(@Nullable String captcha) {
            this.captcha = captcha;
        }

        @Nullable
        public String getCaptchaToken() {
            return captchaToken;
        }

        public void setCaptchaToken(@Nullable String captchaToken) {
            this.captchaToken = captchaToken;
        }

        @Nullable
        public String getWebsite() {
            return website;
        }

        public void setWebsite(@Nullable String website) {
            this.website = website;
        }
    }
}
