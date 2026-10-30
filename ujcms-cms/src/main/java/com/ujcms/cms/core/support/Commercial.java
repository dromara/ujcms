package com.ujcms.cms.core.support;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 商业版（Commercial Edition）状态与权限级别注册表。
 * <p>
 * 版本级别（tier）由类路径上的商业配置类决定：{@code CommercialT1Config}/{@code CommercialT2Config}/
 * {@code CommercialT3Config}/{@code CommercialT4Config} 的静态初始化块调用 {@link #raiseTier(int)}，级别取最高者；
 * 社区版（开源版）不含商业代码，级别为 0。
 * <p>
 * 权限码与所需级别的映射 {@link #PERMISSION_TIERS} 为单一事实来源的代码侧投影，
 * 与 {@code ujcms-cms/src/test/resources/commercial-features/features.yaml} 保持一致（由
 * {@code CommercialFeatureManifestTest} 自动校验）。
 *
 * @author PONY
 */
public final class Commercial {
    /**
     * 权限码 → 所需商业版级别（1=T1 基础版，2=T2 标准版，3=T3 高级版，4=T4 旗舰版）。
     */
    private static final Map<String, Integer> PERMISSION_TIERS = Map.ofEntries(
            // T1
            Map.entry("menu.log", 1),
            Map.entry("operationLog:page", 1),
            Map.entry("loginLog:page", 1),
            Map.entry("shortMessage:page", 1),
            Map.entry("processModel:page", 1),
            Map.entry("processInstance:page", 1),
            Map.entry("processHistory:page", 1),
            Map.entry("generator:fulltext:reindexSite", 1),
            Map.entry("articleReview:page", 1),
            Map.entry("importData:show", 1),
            Map.entry("config:security:update", 1),
            Map.entry("article:import", 1),
            Map.entry("user:import", 1),
            // T2
            Map.entry("article:internalPush", 2),
            Map.entry("article:createTranslation", 2),
            Map.entry("ai:translate", 2),
            Map.entry("article:translateBatch", 2),
            Map.entry("channel:translateBatch", 2),
            Map.entry("config:ai:show", 2),
            Map.entry("config:ai:update", 2),
            Map.entry("siteSettings:ai:update", 2),
            Map.entry("aiLog:page", 2),
            Map.entry("ai:assist", 2),
            Map.entry("ai:replyDraft", 2),
            Map.entry("aiModeration:page", 2),
            Map.entry("aiModeration:review", 2),
            Map.entry("survey:page", 2),
            Map.entry("site:page", 2),
            Map.entry("theme:sandbox", 2),
            Map.entry("config:grey:update", 2),
            Map.entry("config:oauth:show", 2),
            Map.entry("config:oauth:update", 2),
            Map.entry("sensitiveWord:page", 2),
            Map.entry("errorWord:page", 2),
            Map.entry("siteSettings:editor:update", 2),
            Map.entry("collection:page", 2),
            Map.entry("form:page", 2),
            Map.entry("formReview:page", 2),
            Map.entry("topic:page", 2),
            Map.entry("backup:page", 2),
            Map.entry("menu.stat.articleStat", 2),
            Map.entry("articleStatByUser:page", 2),
            Map.entry("articleStatByOrg:page", 2),
            Map.entry("articleStatByChannel:page", 2),
            Map.entry("performanceType:page", 2),
            Map.entry("menu.stat.performanceStat", 2),
            Map.entry("performanceStatByUser:page", 2),
            Map.entry("performanceStatByOrg:page", 2),
            Map.entry("scheduleTask:page", 2),
            // T3
            Map.entry("homepage:systemMonitor", 3),
            Map.entry("article:externalPush", 3),
            Map.entry("formReview:back", 3),
            Map.entry("formReview:delegate", 3),
            Map.entry("formReview:transfer", 3),
            Map.entry("articleReview:back", 3),
            Map.entry("articleReview:delegate", 3),
            Map.entry("articleReview:transfer", 3),
            Map.entry("org:updatePermission", 3),
            Map.entry("menu.wechat", 3),
            Map.entry("wechatAccount:page", 3),
            Map.entry("wechatMaterial:page", 3),
            Map.entry("wechatDraft:page", 3),
            Map.entry("wechatPublish:page", 3),
            Map.entry("wechatMenu:page", 3),
            Map.entry("wechatFan:page", 3),
            Map.entry("wechatTag:page", 3),
            Map.entry("wechatReply:page", 3),
            Map.entry("wechatMessage:page", 3),
            Map.entry("wechatArticle:page", 3),
            Map.entry("wechatArticle:push", 3),
            Map.entry("wechatQrcode:page", 3),
            Map.entry("wechatTemplate:page", 3),
            Map.entry("wechatStat:page", 3),
            Map.entry("wechatMass:page", 3),
            Map.entry("pageRegion:show", 3),
            Map.entry("pageRegion:update", 3),
            Map.entry("shape:list", 3),
            Map.entry("composition:health", 3),
            Map.entry("composition:rescan", 3),
            // T4
            Map.entry("disclosure:page", 4),
            Map.entry("mailbox:page", 4),
            Map.entry("mailboxStat:page", 4),
            Map.entry("interview:page", 4),
            Map.entry("menu.census", 4),
            Map.entry("census:page", 4),
            Map.entry("censusIssue:page", 4),
            Map.entry("siteSettings:disclosure:update", 4),
            Map.entry("siteSettings:mailbox:update", 4),
            Map.entry("siteSettings:interview:update", 4),
            Map.entry("siteSettings:census:update", 4),
            Map.entry("siteSettings:accessibility:update", 4)
    );

    /**
     * 当前商业版级别。0=社区版。
     */
    private static int tier = 0;

    public static int getTier() {
        return tier;
    }

    /**
     * 提升商业版级别。由商业配置类的静态初始化块调用，级别只升不降。
     */
    public static void raiseTier(int newTier) {
        if (newTier > tier) {
            tier = newTier;
        }
    }

    /**
     * 当前版本未解锁的权限码（所需级别高于当前级别）。
     */
    public static List<String> getLockedPermissions() {
        return PERMISSION_TIERS.entrySet().stream()
                .filter(entry -> entry.getValue() > tier)
                .map(Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * 权限码在当前版本是否已解锁。未注册为商业权限的权限码恒为已解锁。
     */
    public static boolean isUnlocked(String permission) {
        return PERMISSION_TIERS.getOrDefault(permission, 0) <= tier;
    }

    /**
     * 权限码所需的商业版级别。仅供一致性测试与诊断使用。
     */
    public static Map<String, Integer> getPermissionTiers() {
        return PERMISSION_TIERS;
    }

    private Commercial() {
    }
}
