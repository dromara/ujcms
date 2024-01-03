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
            Arrays.asList("menu.system",
                    "backupTemplates:page", "backupUploads:page", "incrementalUploads:page", "backupDatabase:page",
                    "processModel:page", "processInstance:page", "processHistory:page",
                    "homepage:systemMonitor", "machine:code", "machine:license",
                    "generator:fulltext:reindexSite",
                    "articleReview:page", "article:internalPush", "article.externalPush",
                    "survey:page", "sensitiveWord:page", "errorWord:page", "siteSettings:editor:update",
                    "menu.stat.articleStat", "articleStatByUser:page", "articleStatByOrg:page", "articleStatByChannel:page",
                    "visitedPage:page", "entryPage:page", "visitSource:page", "visitEnv:page",
                    "performanceType:page", "menu.stat.performanceStat",
                    "performanceStatByUser:page", "performanceStatByOrg:page"));
    private static boolean epDisplay = true;
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
