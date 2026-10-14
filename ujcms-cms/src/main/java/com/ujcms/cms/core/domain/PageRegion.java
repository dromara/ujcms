package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedPageRegion;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * 页面区域实体类（页面构成-结构树）。{@code (site, page_ref, region_name, scope)} 唯一，
 * 持有已发布布局树 {@code layout_json_} 与可选草稿 {@code draft_layout_json_}。
 * 布局树（rows/columns/panel-ids）的解析与校验在 {@link com.ujcms.cms.core.composition.RegionLayout}，
 * 面板内容仍是投放（Panel = Placement），由树以投放 ID 引用。
 *
 * <p>范围覆盖（visual-builder §9 / D6）：{@code scope_} 区分页面默认行（{@code default} 哨兵值）与
 * 覆盖行（{@code channel:<id>} / {@code variant:<surface>:<value>}）；渲染期按 请求范围 → 页面默认
 * 回退解析（Drupal defaults+overrides 模型）。覆盖行完全遮蔽默认行（含空树——「该频道刻意为空」）。
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class PageRegion extends GeneratedPageRegion {
    public static final String NOT_FOUND = "PageRegion not found. ID: ";

    /**
     * 页面默认范围哨兵值。规格草案的「NULL=页面默认」落地为 NOT NULL 哨兵：MySQL 唯一键视 NULL
     * 彼此不同（默认行会重复），空串哨兵在 Dameng/Oracle 兼容模式等价 NULL——'default' 跨库一致。
     */
    public static final String SCOPE_DEFAULT = "default";
    /**
     * 频道范围前缀（{@code channel:<id>}，按当前频道覆盖）
     */
    public static final String SCOPE_PREFIX_CHANNEL = "channel:";
    /**
     * 布局变体范围前缀（{@code variant:<surface>:<value>}，按活动布局变体覆盖）
     */
    public static final String SCOPE_PREFIX_VARIANT = "variant:";

    /**
     * 范围值合法形态：default | channel:<数字ID> | variant:<surface>:<value>
     * （surface 小写字母；value 为清单声明的变体标识符）。
     */
    private static final java.util.regex.Pattern SCOPE_PATTERN = java.util.regex.Pattern.compile(
            "default|channel:\\d{1,19}|variant:[a-z]{1,32}:[A-Za-z0-9_-]{1,64}");

    /**
     * 是否存在未发布草稿
     */
    public boolean isDraftPresent() {
        return StringUtils.isNotBlank(getDraftLayoutJson());
    }

    /**
     * 是否页面默认范围行
     */
    public boolean isDefaultScope() {
        return SCOPE_DEFAULT.equals(getScope());
    }

    /**
     * 范围值是否合法（后台保存/删除入口校验；渲染期候选值由服务端可信上下文构造，无需校验）
     */
    public static boolean isValidScope(@org.jspecify.annotations.Nullable String scope) {
        return scope != null && SCOPE_PATTERN.matcher(scope).matches();
    }

    /**
     * 构造频道范围值（{@code channel:<id>}）
     */
    public static String channelScope(Long channelId) {
        return SCOPE_PREFIX_CHANNEL + channelId;
    }

    /**
     * 构造布局变体范围值（{@code variant:<surface>:<value>}；入参已是 {@code <surface>:<value>}）
     */
    public static String variantScope(String variant) {
        return SCOPE_PREFIX_VARIANT + variant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PageRegion bean = (PageRegion) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
