package com.ujcms.cms.core.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 静态属性的配置文件
 *
 * @author PONY
 */
public class StaticProps {
    private static final List<String> EP_EXCLUDES = new ArrayList<>(
            Arrays.asList(
                    // r1
                    "menu.log", "operationLog:page", "loginLog:page", "shortMessage:page",
                    "menu.system", "processModel:page", "processInstance:page", "processHistory:page",
                    "visitedPage:page", "entryPage:page", "visitSource:page", "visitEnv:page",
                    "generator:fulltext:reindexSite",
                    "articleReview:page",
                    // r2
                    "article:internalPush", "survey:page", "site:page", "config:grey:update",
                    "sensitiveWord:page", "errorWord:page", "siteSettings:editor:update",
                    "collection:page",
                    "backupTemplates:page", "backupUploads:page", "incrementalUploads:page", "backupDatabase:page",
                    "menu.stat.articleStat", "articleStatByUser:page", "articleStatByOrg:page", "articleStatByChannel:page",
                    // r3
                    "homepage:systemMonitor", "article:externalPush",
                    "performanceType:page", "menu.stat.performanceStat",
                    "menu.stat.performanceStat", "performanceStatByUser:page", "performanceStatByOrg:page",
                    "form:page", "formType:page", "formReview:page",
                    "formReview:back", "formReview:delegate", "formReview:transfer",
                    "articleReview:back", "articleReview:delegate", "articleReview:transfer",
                    "org:updatePermission",
                    // end
                    "machine:code", "machine:license", "importData:show"
            ));
    private static boolean epDisplay = false;
    private static int epRank = 0;
    private static boolean epActivated = false;

    public static List<String> getEpExcludes() {
        return Collections.unmodifiableList(EP_EXCLUDES);
    }

    public static void epExcludesRemove(String include) {
        EP_EXCLUDES.remove(include);
    }

    public static boolean isEpDisplay() {
        return epDisplay;
    }

    public static void setEpDisplay(boolean epDisplay) {
        StaticProps.epDisplay = epDisplay;
    }

    public static int getEpRank() {
        return epRank;
    }

    public static void setEpRank(int epRank) {
        StaticProps.epRank = epRank;
    }

    public static boolean isEpActivated() {
        return epActivated;
    }

    public static void setEpActivated(boolean epActivated) {
        StaticProps.epActivated = epActivated;
    }

    private StaticProps() {
        throw new IllegalStateException("Utility class");
    }
}
