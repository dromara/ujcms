package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.commons.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Map;

/**
 * 栏目 标签
 *
 * @author PONY
 */
public class ChannelDirective implements TemplateDirectiveModel {
    private static final String SITE_ID = "siteId";
    private static final String ALIAS = "alias";
    private static final String ID = "id";

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);
        Long id = Directives.getLong(params, ID);
        Channel channel;
        if (id != null) {
            channel = channelService.select(id);
        } else {
            String alias = Directives.getString(params, ALIAS);
            if (StringUtils.isBlank(alias)) {
                throw new IllegalArgumentException("Params id or alias is required.");
            }
            Long defaultSiteId = Frontends.getSiteId(env);
            Long siteId = Directives.getLong(params, SITE_ID, defaultSiteId);
            channel = channelService.findBySiteIdAndAlias(siteId, alias);
        }
        loopVars[0] = env.getObjectWrapper().wrap(channel);
        body.render(env.getOut());
    }

    private final ChannelService channelService;

    public ChannelDirective(ChannelService channelService) {
        this.channelService = channelService;
    }
}
