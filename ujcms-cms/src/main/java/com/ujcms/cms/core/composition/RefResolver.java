package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 引用字段读取期解析（§7.3 / §9.2 步骤 5）。{@code articleRef}/{@code channelRef} 字段存储裸ID，
 * 在<b>读取期</b>解析为摘要对象并并入渲染契约——文章 {@code {id,title,url,image}}、
 * 栏目 {@code {id,name,url}}；目标已删除时返回占位 {@code {id,deleted:true}}（Health 另行告警）。
 * 从不持久化，故文章/栏目改名或删除下次请求即反映。
 *
 * <p>沿 Shape 字段定义递归（含 {@code list}/{@code object} 内部），仅在含引用字段处生成新副本，
 * 不修改入参（卡片缓存数据不可变），与 {@link ThemeColorResolver} 同型。逐ID经
 * {@link ArticleService#select}/{@link ChannelService#select} 查取（二者自带缓存，且整个解析结果
 * 已被 {@link PanelResolveCache} 缓存），一个插槽仅少量卡片、每卡少量引用，无需批量。
 *
 * @author PONY
 */
@Component
public class RefResolver {
    /**
     * 文章引用字段类型名
     */
    public static final String TYPE_ARTICLE_REF = "articleRef";
    /**
     * 栏目引用字段类型名
     */
    public static final String TYPE_CHANNEL_REF = "channelRef";
    private static final String TYPE_LIST = "list";
    private static final String TYPE_OBJECT = "object";
    private static final String ID = "id";
    private static final String DELETED = "deleted";

    private final ArticleService articleService;
    private final ChannelService channelService;

    public RefResolver(ArticleService articleService, ChannelService channelService) {
        this.articleService = articleService;
        this.channelService = channelService;
    }

    /**
     * 解析卡片数据中的全部引用字段。
     *
     * @param fields 卡片 Shape 字段定义
     * @param data   卡片字段数据（{@code articleRef}/{@code channelRef} 值为裸ID）
     * @return 注入了引用摘要对象的字段数据（无引用字段或无值时原样返回入参）
     */
    public Map<String, Object> resolve(Map<String, FieldDef> fields, Map<String, Object> data) {
        if (fields.isEmpty() || data.isEmpty()) {
            return data;
        }
        Map<String, Object> result = null;
        for (Map.Entry<String, FieldDef> entry : fields.entrySet()) {
            String name = entry.getKey();
            Object value = data.get(name);
            if (value == null) {
                continue;
            }
            Object resolved = resolveValue(entry.getValue(), value);
            if (resolved != value) {
                if (result == null) {
                    result = new LinkedHashMap<>(data);
                }
                result.put(name, resolved);
            }
        }
        return result != null ? result : data;
    }

    @Nullable
    private Object resolveValue(FieldDef def, Object value) {
        String type = def.getType();
        if (TYPE_ARTICLE_REF.equals(type)) {
            return resolveArticle(value);
        }
        if (TYPE_CHANNEL_REF.equals(type)) {
            return resolveChannel(value);
        }
        if (TYPE_LIST.equals(type) && value instanceof List<?> list) {
            FieldDef of = def.getFieldDef("of");
            if (of == null || !isRefType(of.getType())) {
                return value;
            }
            List<Object> out = new ArrayList<>(list.size());
            boolean changed = false;
            for (Object element : list) {
                Object resolved = element == null ? null : resolveValue(of, element);
                changed |= resolved != element;
                out.add(resolved);
            }
            return changed ? out : value;
        }
        if (TYPE_OBJECT.equals(type) && value instanceof Map<?, ?> map) {
            Map<String, FieldDef> objectFields = def.getFieldDefs("fields");
            if (!hasRefField(objectFields)) {
                return value;
            }
            Map<String, Object> objectData = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k instanceof String key) {
                    objectData.put(key, v);
                }
            });
            Map<String, Object> resolved = resolve(objectFields, objectData);
            return resolved == objectData ? value : resolved;
        }
        return value;
    }

    private Object resolveArticle(Object value) {
        Long id = asRefId(value);
        if (id == null) {
            // 已是解析对象或非法值 → 原样透传（防御性；库内存的恒为裸ID）
            return value;
        }
        Article article = articleService.select(id);
        if (article == null) {
            return stub(id);
        }
        Map<String, Object> ref = new LinkedHashMap<>(4);
        ref.put(ID, String.valueOf(id));
        ref.put("title", article.getTitle());
        ref.put("url", article.getUrl());
        ref.put("image", article.getImage());
        return ref;
    }

    private Object resolveChannel(Object value) {
        Long id = asRefId(value);
        if (id == null) {
            return value;
        }
        Channel channel = channelService.select(id);
        if (channel == null) {
            return stub(id);
        }
        Map<String, Object> ref = new LinkedHashMap<>(3);
        ref.put(ID, String.valueOf(id));
        ref.put("name", channel.getName());
        ref.put("url", channel.getUrl());
        return ref;
    }

    /**
     * 已删除目标的占位对象：{@code {id, deleted:true}}。渲染器据 {@code deleted} 或缺失的
     * {@code title}/{@code name} 降级；Health 的 stale-ref 检查另行列出供操作员处理。
     */
    private static Map<String, Object> stub(Long id) {
        Map<String, Object> ref = new LinkedHashMap<>(2);
        ref.put(ID, String.valueOf(id));
        ref.put(DELETED, true);
        return ref;
    }

    /**
     * 首个指定引用类型的<b>顶层</b>字段的裸ID（visual-builder §5.5：编排卡片的 articleId/channelId
     * 契约标注）。仅扫描顶层单值字段——list/object 内的引用语义有歧义（深链该编辑哪一个），不参与。
     * 须在 {@link #resolve} 之前调用（解析后值已是摘要对象，不再是裸ID）。
     *
     * @param fields 卡片 Shape 字段定义
     * @param data   卡片字段数据（未经引用解析，值为裸ID）
     * @param type   {@link #TYPE_ARTICLE_REF} 或 {@link #TYPE_CHANNEL_REF}
     * @return 首个有效引用ID；无该类型字段或无有效值时为 {@code null}
     */
    @Nullable
    public static Long firstRefId(Map<String, FieldDef> fields, Map<String, Object> data, String type) {
        for (Map.Entry<String, FieldDef> entry : fields.entrySet()) {
            if (!type.equals(entry.getValue().getType())) {
                continue;
            }
            Long id = asRefId(data.get(entry.getKey()));
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static boolean isRefType(String type) {
        return TYPE_ARTICLE_REF.equals(type) || TYPE_CHANNEL_REF.equals(type)
                || TYPE_LIST.equals(type) || TYPE_OBJECT.equals(type);
    }

    private static boolean hasRefField(Map<String, FieldDef> fields) {
        return fields.values().stream().anyMatch(def -> isRefType(def.getType()));
    }

    @Nullable
    private static Long asRefId(Object value) {
        if (value instanceof Number number && number.longValue() > 0
                && number.longValue() == number.doubleValue()) {
            return number.longValue();
        }
        if (value instanceof String str && str.matches("\\d{1,19}") && Long.parseLong(str) > 0) {
            return Long.parseLong(str);
        }
        return null;
    }
}
