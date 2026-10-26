package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Site;

import java.util.Set;

/**
 * 模板预览候选来源（preview-url-resolver §3.4 / P4）：为<b>实例承载页</b>——以某条具体记录为上下文渲染、
 * 且不属于栏目/文章/专题三大实体面的系统模板（标签详情、投票详情、留言详情、会员主页、问卷详情、信件详情、
 * 访谈详情、表单提交页…）——按模板名给出代表性预览地址。核心的 {@link BuiltinTemplatePreviewSource} 覆盖
 * 核心前台控制器渲染的页面；商业版 T2 / T4 模块各自注册其页面的来源（Bean 存在即生效，层级由 classpath
 * 决定）；未加载层级的模板由 {@link PreviewUrlResolver} 的静态层级表答 {@code tier-locked}。
 *
 * <p>实现须与对应前台控制器<b>渲染同径</b>：控制器会 404 / 403 的记录（未启用、未发布、跨站点）不作候选；
 * 功能整体未启用时答 {@link PreviewUrlResolver#EMPTY_DISABLED}，无代表记录时答
 * {@link PreviewUrlResolver#EMPTY_NO_CONTENT}，主体词取 {@code PreviewUrlResolver.SUBJECT_*}；候选 URL 经
 * {@link PreviewUrlResolver#sitePath(Site, String)} 拼站点前缀。
 *
 * @author PONY
 */
public interface TemplatePreviewSource {
    /**
     * 本来源认领的模板名（不带目录与扩展名）
     */
    Set<String> templates();

    /**
     * 产出候选（至多 {@code limit} 条）；无候选时以 {@code emptyReason} / {@code emptySubject} 说明原因
     *
     * @param site     站点
     * @param template 模板名（必属于 {@link #templates()}）
     * @param limit    候选数上限
     */
    PreviewUrlResolver.Result find(Site site, String template, int limit);
}
