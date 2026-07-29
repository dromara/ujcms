package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.FriendLink;
import com.ujcms.cms.ext.domain.FriendLinkType;
import com.ujcms.cms.ext.service.FriendLinkService;
import com.ujcms.cms.ext.service.FriendLinkTypeService;
import com.ujcms.cms.ext.service.args.FriendLinkArgs;
import com.ujcms.common.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.core.web.support.Directives.getLong;

/**
 * 友情链接列表 标签
 *
 * @author PONY
 */
public class FriendLinkListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型(Integer)。
     */
    public static final String SITE_ID = "siteId";
    /**
     * 类型ID。长整型(Long)。默认获取全部类型。
     */
    public static final String TYPE_ID = "typeId";
    /**
     * 类型别名。字符串(String)。用于按别名获取指定类型的友情链接。
     */
    public static final String TYPE_ALIAS = "typeAlias";
    /**
     * 是否启用。字符串(String)。可选值：all(全部), false(禁用)。默认值：启用。
     */
    public static final String IS_ENABLED = "isEnabled";

    public static void assemble(FriendLinkArgs args, Map<String, ?> params, Long defaultSiteId,
                                FriendLinkTypeService typeService) {
        Long siteId = getLong(params, SITE_ID);
        if (siteId == null) {
            siteId = defaultSiteId;
        }
        args.siteId(siteId);
        Long typeId = getLong(params, TYPE_ID);
        String typeAlias = Directives.getString(params, TYPE_ALIAS);
        if (typeId == null && StringUtils.isNotBlank(typeAlias)) {
            typeId = Optional.ofNullable(typeService.selectByAlias(siteId, typeAlias))
                    .map(FriendLinkType::getId).orElse(-1L);
        }
        args.typeId(typeId);
        Optional.ofNullable(Directives.getBooleanDefault(params, IS_ENABLED, true)).ifPresent(args::enabled);
        Directives.handleOrderBy(args.getQueryMap(), params);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        FriendLinkArgs args = FriendLinkArgs.of(Directives.getQueryMap(params));
        assemble(args, params, Frontends.getSiteId(env), typeService);

        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        List<FriendLink> list = service.selectList(args, offset, limit);
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }


    private final FriendLinkService service;
    private final FriendLinkTypeService typeService;

    public FriendLinkListDirective(FriendLinkService service, FriendLinkTypeService typeService) {
        this.service = service;
        this.typeService = typeService;
    }
}
