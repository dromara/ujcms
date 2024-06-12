package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.base.SiteBase;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.LeaderBoard;
import com.ujcms.cms.ext.service.LeaderBoardService;
import com.ujcms.cms.ext.service.args.LeaderBoardArgs;
import com.ujcms.commons.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.springframework.data.domain.Page;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static com.ujcms.cms.core.web.support.Directives.*;
import static com.ujcms.commons.db.MyBatis.springPage;

/**
 * 文章排行榜列表 标签
 *
 * @author PONY
 */
public class LeaderBoardListDirective implements TemplateDirectiveModel {
    /**
     * 类型。枚举(String)。channel:栏目排行榜, org: 组织排行榜, user:用户排行榜。默认：栏目。
     */
    public static final String TYPE = "type";
    /**
     * 站点ID。整型(Integer)。
     */
    public static final String SITE_ID = "siteId";
    /**
     * 是否发布。布尔型(Boolean)。`true`:已发布文章, `false`:全部文章。默认：`false`。
     */
    public static final String IS_PUBLISHED = "isPublished";
    /**
     * 期间。枚举型(String)。none:无期间，即所有, today:当天, week:本周, month:本月, quarter:本季, year:本年。默认: none。
     */
    public static final String PERIOD = "period";
    /**
     * 开始日期。日期(OffsetDateTime)。
     */
    public static final String BEGIN = "begin";
    /**
     * 结束日期。日期(OffsetDateTime)。
     */
    public static final String END = "end";
    /**
     * 是否获取所有站点的数据。布尔型(Boolean)。默认：false。
     */
    public static final String IS_ALL_SITE = "isAllSite";


    public static void assemble(LeaderBoardArgs args, Map<String, ?> params, Long defaultSiteId) {
        Long siteId = getLong(params, SITE_ID);
        // 不获取所有站点，则给默认站点ID
        if (siteId == null && !getBoolean(params, IS_ALL_SITE, false)) {
            siteId = defaultSiteId;
        }
        Collection<Short> status = Boolean.TRUE.equals(getBoolean(params, IS_PUBLISHED, false)) ?
                Collections.singleton(Article.STATUS_PUBLISHED) : Collections.emptyList();
        OffsetDateTime begin = Directives.getOffsetDateTime(params, BEGIN);
        OffsetDateTime end = Directives.getOffsetDateTime(params, END);
        if (begin == null && end == null) {
            String period = Directives.getString(params, PERIOD, PERIOD_NONE);
            begin = beginPeriod(period);
            end = endPeriod(period);
        }
        args.setSiteId(siteId);
        args.setStatus(status);
        args.setBegin(begin);
        args.setEnd(end);
    }

    protected void doExecute(Environment env, Map<String, TemplateModel> params, TemplateModel[] loopVars,
                             TemplateDirectiveBody body, boolean isPage) throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        String type = Directives.getString(params, TYPE, TYPE_CHANNEL);

        LeaderBoardArgs args = LeaderBoardArgs.of();
        assemble(args, params, Frontends.getSiteId(env));

        if (isPage) {
            int page = Directives.getPage(params, env);
            int pageSize = Directives.getPageSize(params, env);
            Page<LeaderBoard> pagedList = fetchPage(service, siteService, type, args, page, pageSize);
            Directives.setTotalPages(pagedList.getTotalPages());
            loopVars[0] = env.getObjectWrapper().wrap(pagedList);
        } else {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            List<LeaderBoard> list = fetchList(service, siteService, type, args, offset, limit);
            loopVars[0] = env.getObjectWrapper().wrap(list);
        }
        body.render(env.getOut());
    }

    public static Page<LeaderBoard> fetchPage(LeaderBoardService service, SiteService siteService,
                                              String type, LeaderBoardArgs args, int page, int pageSize) {
        switch (type) {
            case TYPE_CHANNEL:
                return springPage(
                        service.channelLeaderBoardPage(args.getSiteId(), args.getStatus(),
                                args.getBegin(), args.getEnd(), page, pageSize));
            case TYPE_ORG:
                return springPage(
                        service.orgLeaderBoardPage(fetchOrgId(siteService, args.getSiteId()), args.getStatus(),
                                args.getBegin(), args.getEnd(), page, pageSize));
            case TYPE_USER:
                return springPage(
                        service.userLeaderBoardPage(fetchOrgId(siteService, args.getSiteId()), args.getStatus(),
                                args.getBegin(), args.getEnd(), page, pageSize));
            default:
                throw new IllegalArgumentException("Type not support: " + type);
        }
    }

    public static List<LeaderBoard> fetchList(LeaderBoardService service, SiteService siteService,
                                              String type, LeaderBoardArgs args, int offset, int limit) {
        switch (type) {
            case TYPE_CHANNEL:
                return service.channelLeaderBoardList(args.getSiteId(), args.getStatus(),
                        args.getBegin(), args.getEnd(), offset, limit);
            case TYPE_ORG:
                return service.orgLeaderBoardList(fetchOrgId(siteService, args.getSiteId()), args.getStatus(),
                        args.getBegin(), args.getEnd(), offset, limit);
            case TYPE_USER:
                return service.userLeaderBoardList(fetchOrgId(siteService, args.getSiteId()), args.getStatus(),
                        args.getBegin(), args.getEnd(), offset, limit);
            default:
                throw new IllegalArgumentException("Type not support: " + type);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        doExecute(env, params, loopVars, body, false);
    }

    @Nullable
    private static Long fetchOrgId(SiteService siteService, @Nullable Long siteId) {
        return Optional.ofNullable(siteId).map(siteService::select).map(SiteBase::getOrgId).orElse(null);
    }

    @Nullable
    public static OffsetDateTime beginPeriod(String period) {
        OffsetDateTime today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        switch (period) {
            case PERIOD_DAY:
                return today;
            case PERIOD_WEEK:
                return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            case PERIOD_MONTH:
                return today.withDayOfMonth(1);
            case PERIOD_QUARTER:
                return today.with(IsoFields.DAY_OF_QUARTER, 1);
            case PERIOD_YEAR:
                return today.withDayOfYear(1);
            default:
                return null;
        }
    }

    @Nullable
    public static OffsetDateTime endPeriod(String period) {
        OffsetDateTime today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        switch (period) {
            case PERIOD_DAY:
                return today.plusDays(1);
            case PERIOD_WEEK:
                return today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
            case PERIOD_MONTH:
                return today.withDayOfMonth(1).plusMonths(1);
            case PERIOD_QUARTER:
                return today.with(IsoFields.DAY_OF_QUARTER, 1).plus(1, IsoFields.QUARTER_YEARS);
            case PERIOD_YEAR:
                return today.withDayOfYear(1).plusYears(1);
            default:
                return null;
        }
    }

    /**
     * 类型：栏目
     */
    public static final String TYPE_CHANNEL = "channel";
    /**
     * 类型：组织
     */
    public static final String TYPE_ORG = "org";
    /**
     * 类型：用户
     */
    public static final String TYPE_USER = "user";

    /**
     * 期间：无
     */
    public static final String PERIOD_NONE = "none";
    /**
     * 期间：当天
     */
    public static final String PERIOD_DAY = "day";
    /**
     * 期间：本周
     */
    public static final String PERIOD_WEEK = "week";
    /**
     * 期间：本月
     */
    public static final String PERIOD_MONTH = "month";
    /**
     * 期间：本季
     */
    public static final String PERIOD_QUARTER = "quarter";
    /**
     * 期间：本年
     */
    public static final String PERIOD_YEAR = "year";

    private final LeaderBoardService service;
    private final SiteService siteService;

    public LeaderBoardListDirective(LeaderBoardService service, SiteService siteService) {
        this.service = service;
        this.siteService = siteService;
    }
}
