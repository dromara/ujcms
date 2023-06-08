package com.ujcms.cms.ext.web.api;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.ext.component.VisitService;
import com.ujcms.cms.ext.domain.VisitLog;
import com.ujcms.cms.ext.domain.VisitStat;
import com.ujcms.commons.ip.IpSeeker;
import com.ujcms.commons.ip.Region;
import com.ujcms.commons.web.Servlets;
import eu.bitwalker.useragentutils.UserAgent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

import static com.ujcms.cms.core.domain.User.ANONYMOUS_ID;
import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * Visit访问统计 接口
 *
 * @author PONY
 */
@RestController
@RequestMapping({API + "/visit", FRONTEND_API + "/visit"})
public class VisitController {
    private final VisitService service;
    private final IpSeeker ipSeeker;

    public VisitController(VisitService service, IpSeeker ipSeeker) {
        this.service = service;
        this.ipSeeker = ipSeeker;
    }

    @PostMapping("{siteId}")
    public void visit(@PathVariable Integer siteId, String url, String entryUrl, String referrer, Long si, Long uv,
                      Boolean newVisitor, Integer count, Integer duration, HttpServletRequest request) {
        String host = UriComponentsBuilder.fromHttpUrl(url).build().getHost();
        String source = VisitStat.SOURCE_DIRECT;
        if (StringUtils.isNotBlank(referrer)) {
            UriComponents uriComp = UriComponentsBuilder.fromHttpUrl(referrer).build();
            String referHost = uriComp.getHost();
            if (StringUtils.equals(host, referHost)) {
                source = VisitStat.SOURCE_INNER;
            } else {
                StringBuilder sourceBuff = new StringBuilder(uriComp.getScheme() + "://" + referHost);
                if (uriComp.getPort() != -1) {
                    sourceBuff.append(":").append(uriComp.getPort());
                }
                source = sourceBuff.toString();
            }
        }

        String userAgentString = request.getHeader("user-agent");
        UserAgent userAgent = UserAgent.parseUserAgentString(userAgentString);
        String ip = Servlets.getRemoteAddr(request);
        String browser = userAgent.getBrowser().toString();
        String os = userAgent.getOperatingSystem().toString();
        String device = userAgent.getOperatingSystem().getDeviceType().toString();
        Region region = ipSeeker.find(Servlets.getRemoteAddr(request));

        VisitLog bean = new VisitLog();
        bean.setSiteId(siteId);
        bean.setUserId(Optional.ofNullable(Contexts.findCurrentUser()).map(User::getId).orElse(ANONYMOUS_ID));
        bean.setUrl(StringUtils.substring(StringUtils.substringBefore(url, "?"), 0, 255));
        bean.setEntryUrl(StringUtils.substring(StringUtils.substringBefore(entryUrl, "?"), 0, 255));
        bean.setSource(source);
        bean.setCountry(region.getCountry());
        bean.setProvince(region.getProvince());
        bean.setDevice(device);
        bean.setOs(os);
        bean.setBrowser(browser);
        bean.setUserAgent(StringUtils.substring(userAgentString, 0, 255));
        bean.setCount(count);
        bean.setSi(si);
        bean.setUv(uv);
        bean.setIp(ip);
        bean.setDuration(duration);
        bean.setNewVisitor(newVisitor);
        service.cacheVisitLog(bean);
    }

}
