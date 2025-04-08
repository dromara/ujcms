package com.ujcms.cms.ext.web.api;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.GlobalService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.ext.component.VisitService;
import com.ujcms.cms.ext.domain.VisitLog;
import com.ujcms.cms.ext.domain.VisitStat;
import com.ujcms.cms.ext.domain.global.GlobalVisitorCount;
import com.ujcms.commons.ip.IpSeeker;
import com.ujcms.commons.ip.Region;
import com.ujcms.commons.web.Servlets;
import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.UserAgent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Optional;

import static com.ujcms.cms.core.domain.User.ANONYMOUS_ID;
import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.cms.ext.domain.VisitStat.SEARCH_ENGINE_LIST;

/**
 * Visit访问统计 接口
 *
 * @author PONY
 */
@Tag(name = "访问统计接口")
@RestController
public class VisitController {
    private final GlobalService globalService;
    private final VisitService service;
    private final IpSeeker ipSeeker;

    public VisitController(GlobalService globalService, VisitService service, IpSeeker ipSeeker) {
        this.globalService = globalService;
        this.service = service;
        this.ipSeeker = ipSeeker;
    }

    @Operation(summary = "获取在线访问者数量")
    @GetMapping({API + "/visit/online-visitors", FRONTEND_API + "/visit/online-visitors"})
    public long onlineVisitors() {
        return globalService.selectGlobalDataOrInit(new GlobalVisitorCount(), GlobalVisitorCount.class).getVisitor();
    }

    @Operation(summary = "记录访问统计接口")
    @PostMapping(API + "/visit/{siteId}")
    public void visit(@Parameter(description = "站点ID") @PathVariable Long siteId,
                      @RequestBody @Valid VisitParams params,
                      HttpServletRequest request) {
        doVisit(siteId, params, request);
    }

    @Operation(summary = "记录访问统计接口")
    @PostMapping(FRONTEND_API + "/visit/{siteId}")
    public void visit(@Parameter(description = "站点ID") @PathVariable Long siteId,
                      @Parameter(description = "受访URL地址。即需要记录的访问地址。js中可以使用`document.location.href`获取") String url,
                      @Parameter(description = "入口地址。用户每次浏览网站，通常会浏览多个页面，第一个打开的页面即为入口页面") String entryUrl,
                      @Parameter(description = "来源页面。当前页面的上一个页面。js中可以使用`document.referrer`获取") String referrer,
                      @Parameter(description = "会话ID(Session ID)。可以在页面中使用随机数生成会话ID，同一个会话访问需确保会话ID相同。可将会话ID保存至cookie中，cookie会在关闭浏览器时清空数据") Long si,
                      @Parameter(description = "访客ID(Unique Visitor)。可以在页面中使用随机数生成访客ID，同一个访客访问需确保访客ID相同。可将访客ID数据保存在localstorage中，localstorage可永久保存数据") Long uv,
                      @Parameter(description = "是否新访客。如果用户是第一次访问网站，本次会话的所有访问都应为新访客访问") Boolean newVisitor,
                      @Parameter(description = "访问计数。正常访问为`1`，用于记录访问信息；关闭页面结束访问为`0`，用于记录访问结束时间，统计访问时长") Integer count,
                      @Parameter(description = "持续时间。本次访问会话的持续时间，从本次会话访问的第一个页面开始计算时间。单位：秒") Integer duration,
                      HttpServletRequest request) {
        VisitParams params = new VisitParams();
        params.setUrl(url);
        params.setEntryUrl(entryUrl);
        params.setReferrer(referrer);
        params.setSi(si);
        params.setUv(uv);
        params.setNewVisitor(newVisitor);
        params.setCount(count);
        params.setDuration(duration);
        doVisit(siteId, params, request);
    }

    private void doVisit(Long siteId, VisitParams params, HttpServletRequest request) {
        String host = UriComponentsBuilder.fromHttpUrl(params.getUrl()).build().getHost();
        String source = VisitStat.SOURCE_DIRECT;
        String sourceType = VisitStat.SOURCE_DIRECT;
        String referrer = params.getReferrer();
        if (StringUtils.isNotBlank(referrer)) {
            UriComponents uriComp = UriComponentsBuilder.fromHttpUrl(referrer).build();
            String referHost = StringUtils.lowerCase(uriComp.getHost());
            if (StringUtils.equals(host, referHost)) {
                source = VisitStat.SOURCE_INNER;
                sourceType = VisitStat.SOURCE_INNER;
            } else {
                StringBuilder sourceBuff = new StringBuilder(uriComp.getScheme() + "://" + referHost);
                if (uriComp.getPort() != -1) {
                    sourceBuff.append(":").append(uriComp.getPort());
                }
                source = sourceBuff.toString();
                if (SEARCH_ENGINE_LIST.contains(referHost)) {
                    sourceType = VisitStat.SOURCE_SEARCH;
                } else {
                    sourceType = VisitStat.SOURCE_OUTER;
                }
            }
        }

        String userAgentString = request.getHeader("user-agent");
        UserAgent userAgent = UserAgent.parseUserAgentString(userAgentString);
        Browser agentBrowser = userAgent.getBrowser();
        // 不统计机器人访问的数据
        if (agentBrowser.equals(Browser.BOT)) {
            return;
        }
        String browser = agentBrowser.toString();
        String ip = Servlets.getRemoteAddr(request);
        String os = userAgent.getOperatingSystem().toString();
        String device = userAgent.getOperatingSystem().getDeviceType().toString();
        Region region = ipSeeker.find(Servlets.getRemoteAddr(request));

        VisitLog bean = new VisitLog();
        bean.setSiteId(siteId);
        bean.setUserId(Optional.ofNullable(Contexts.findCurrentUser()).map(User::getId).orElse(ANONYMOUS_ID));
        bean.setUrl(StringUtils.substring(StringUtils.substringBefore(params.getUrl(), "?"), 0, 255));
        bean.setEntryUrl(StringUtils.substring(StringUtils.substringBefore(params.getEntryUrl(), "?"), 0, 255));
        bean.setSource(source);
        bean.setSourceType(sourceType);
        bean.setCountry(region.getCountry());
        bean.setProvince(region.getProvince());
        bean.setDevice(device);
        bean.setOs(os);
        bean.setBrowser(browser);
        bean.setUserAgent(StringUtils.substring(userAgentString, 0, 255));
        bean.setCount(params.getCount());
        bean.setSi(params.getSi());
        bean.setUv(params.getUv());
        bean.setIp(ip);
        bean.setDuration(params.getDuration());
        bean.setNewVisitor(params.getNewVisitor());
        service.cacheVisitLog(bean);
    }

    @Schema(name = "VisitController.VisitParams", description = "访问统计参数")
    public static class VisitParams implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 受访URL地址。即需要记录的访问地址。js中可以使用`document.location.href`获取
         */
        @NotEmpty
        private String url;
        /**
         * 入口地址。用户每次浏览网站，通常会浏览多个页面，第一个打开的页面即为入口页面
         */
        private String entryUrl;
        /**
         * 来源页面。当前页面的上一个页面。js中可以使用`document.referrer`获取
         */
        private String referrer;
        /**
         * 会话ID(Session ID)。可以在页面中使用随机数生成会话ID，同一个会话访问需确保会话ID相同
         */
        @NotNull
        private Long si;
        /**
         * 访客ID(Unique Visitor)。可以在页面中使用随机数生成访客ID，同一个访客访问需确保访客ID相同
         */
        @NotNull
        private Long uv;
        /**
         * 是否新访客。如果用户是第一次访问网站，本次会话的所有访问都应为新访客访问
         */
        @NotNull
        private Boolean newVisitor;
        /**
         * 访问计数。正常访问为`1`，关闭页面结束访问为`0`
         */
        @NotNull
        private Integer count;
        /**
         * 持续时间。本次访问会话的持续时间，从本次会话访问的第一个页面开始计算时间。单位：秒
         */
        @NotNull
        private Integer duration;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getEntryUrl() {
            return entryUrl;
        }

        public void setEntryUrl(String entryUrl) {
            this.entryUrl = entryUrl;
        }

        public String getReferrer() {
            return referrer;
        }

        public void setReferrer(String referrer) {
            this.referrer = referrer;
        }

        public Long getSi() {
            return si;
        }

        public void setSi(Long si) {
            this.si = si;
        }

        public Long getUv() {
            return uv;
        }

        public void setUv(Long uv) {
            this.uv = uv;
        }

        public Boolean getNewVisitor() {
            return newVisitor;
        }

        public void setNewVisitor(Boolean newVisitor) {
            this.newVisitor = newVisitor;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public Integer getDuration() {
            return duration;
        }

        public void setDuration(Integer duration) {
            this.duration = duration;
        }
    }

}
