package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 注册表快照。某一时刻已加载的全部 Shape / 页面清单 / 主题设置结构的不可变记录。
 * 所有读取经由 {@code AtomicReference<RegistrySnapshot>}（{@link ManifestService}）；
 * 重载构建新快照、校验通过后原子替换，进行中的请求在旧快照上完成。
 *
 * <p>命名空间解析：Shape 只存在于主题命名空间（theme-owned-shapes D15，核心命名空间已退役）；
 * 保存/渲染按活动主题键序解析，链上无命中即不可解析。
 *
 * @author PONY
 */
public final class RegistrySnapshot {
    private final Map<String, Map<String, Shape>> themeShapes;
    private final Map<String, Map<String, PageManifest>> themeManifests;
    /**
     * 主题设置结构（每主题），由 {@code theme.manifest.json#settings} 解析（§8.2）。
     * 结构是文件事实，随主题清单热重载；操作员设置的值另存于 {@code ujcms_theme_state}。
     */
    private final Map<String, ThemeSettingsSchema> themeSettings;
    private final Map<String, LinkSlotCatalog> themeLinkSlots;
    /**
     * 主题声明的布局变体目录（每主题键；theme-marketplace R1 统一注册表的另一半）。
     * 由主题清单 {@code theme.manifest.json#layoutVariants}（manifest 2.0）声明，Phase A1 起由
     * {@link ThemeManifestLoader} 装入；A0 阶段恒为空。读取按站点合并（SSR ∪ 活动主题）。
     */
    private final Map<String, LayoutVariantCatalog> themeLayoutVariants;
    /**
     * 模板名 → 页面清单 绑定索引（每主题）。模板轨道按渲染模板名解析清单（§6.2，精确匹配、无隐式回退）。
     * 同一模板被多个清单绑定时首个胜出——该情形会被 {@link SnapshotValidator} 拒绝，不会生效。
     */
    private final Map<String, Map<String, PageManifest>> themeTemplateBindings;
    /**
     * 制品级内容散列（key 与 {@link #contentHash} 的散列输入同源：
     * {@code shape:theme:主题键:名}、{@code manifest:theme:主题键:名}、{@code settings:theme:主题键} 等）。
     * 供 {@link SnapshotDiff} 对比新旧快照，产出加载记录的差异摘要（diff_summary_json_）。
     */
    private final Map<String, String> sourceHashes;
    private final String contentHash;
    private final OffsetDateTime loadedAt;

    private RegistrySnapshot(Builder builder) {
        Map<String, Map<String, Shape>> themeShapeMap = new LinkedHashMap<>();
        builder.themeShapes.forEach((theme, shapes) ->
                themeShapeMap.put(theme, Collections.unmodifiableMap(new LinkedHashMap<>(shapes))));
        this.themeShapes = Collections.unmodifiableMap(themeShapeMap);
        Map<String, Map<String, PageManifest>> themeManifestMap = new LinkedHashMap<>();
        Map<String, Map<String, PageManifest>> bindingMap = new LinkedHashMap<>();
        builder.themeManifests.forEach((theme, manifests) -> {
            // 页面按清单声明的 order 一次排定（PageManifest.DECLARED_ORDER），不再依赖 pages/ 文件名序；
            // 页面目录、区域目录、主题详情与页面设置都直接沿用这里的顺序
            Map<String, PageManifest> declared = new LinkedHashMap<>(manifests.size());
            manifests.values().stream().sorted(PageManifest.DECLARED_ORDER)
                    .forEach(manifest -> declared.put(manifest.getName(), manifest));
            Map<String, PageManifest> ordered = resolveSharedSlots(declared);
            themeManifestMap.put(theme, Collections.unmodifiableMap(ordered));
            Map<String, PageManifest> bindings = new LinkedHashMap<>();
            ordered.values().forEach(manifest ->
                    manifest.getTemplates().forEach(template -> bindings.putIfAbsent(template, manifest)));
            bindingMap.put(theme, Collections.unmodifiableMap(bindings));
        });
        this.themeManifests = Collections.unmodifiableMap(themeManifestMap);
        this.themeTemplateBindings = Collections.unmodifiableMap(bindingMap);
        this.themeSettings = Collections.unmodifiableMap(new LinkedHashMap<>(builder.themeSettings));
        this.themeLayoutVariants = Collections.unmodifiableMap(new LinkedHashMap<>(builder.themeLayoutVariants));
        this.themeLinkSlots = Collections.unmodifiableMap(new LinkedHashMap<>(builder.themeLinkSlots));
        Map<String, String> hashes = new LinkedHashMap<>(builder.hashSources.size());
        builder.hashSources.forEach((key, value) -> hashes.put(key, sha256Hex(value)));
        this.sourceHashes = Collections.unmodifiableMap(hashes);
        this.contentHash = sha256Hex(builder.hashSources);
        this.loadedAt = OffsetDateTime.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 解引用共享区域（page-regions §15）：{@code {"kind":"region","shared":"<page>"}} 的区域取同主题
     * 宿主页面同名区域的完整声明副本（带 {@code sharedFrom} 标记），此后所有读取 {@code getSlots()}
     * 的消费者都看到完整定义。宿主不存在 / 同名区域未在宿主完整声明 / 宿主自身为引用（链式）/
     * 自引用 → 保持未解引用形态，由 {@link SnapshotValidator} 报错。只解一层，不递归。
     */
    private static Map<String, PageManifest> resolveSharedSlots(Map<String, PageManifest> declared) {
        Map<String, PageManifest> resolved = new LinkedHashMap<>(declared.size());
        declared.forEach((name, manifest) -> {
            if (manifest.getSlots().values().stream().noneMatch(slot -> slot.getSharedFrom() != null)) {
                resolved.put(name, manifest);
                return;
            }
            Map<String, PageManifest.SlotDef> slots = new LinkedHashMap<>(manifest.getSlots().size());
            manifest.getSlots().forEach((slotName, slot) -> {
                String owner = slot.getSharedFrom();
                if (owner == null) {
                    slots.put(slotName, slot);
                    return;
                }
                PageManifest ownerManifest = declared.get(owner);
                PageManifest.SlotDef ownerSlot = ownerManifest == null || ownerManifest == manifest
                        ? null : ownerManifest.getSlots().get(slotName);
                if (ownerSlot == null || ownerSlot.getSharedFrom() != null) {
                    slots.put(slotName, slot);
                    return;
                }
                slots.put(slotName, ownerSlot.asSharedFrom(owner));
            });
            resolved.put(name, manifest.withSlots(slots));
        });
        return resolved;
    }

    public static RegistrySnapshot empty() {
        return new Builder().build();
    }

    public Map<String, Map<String, Shape>> getThemeShapes() {
        return themeShapes;
    }

    public Map<String, Map<String, PageManifest>> getThemeManifests() {
        return themeManifests;
    }

    public Map<String, ThemeSettingsSchema> getThemeSettings() {
        return themeSettings;
    }

    /**
     * 主题声明的布局变体目录（R1 统一注册表；A1 起由主题清单装入）。未声明 → {@link LayoutVariantCatalog#EMPTY}。
     *
     * @param themeKey 规范化主题键（{@code siteId/themeName}）
     */
    public LayoutVariantCatalog getThemeLayoutVariants(String themeKey) {
        return themeLayoutVariants.getOrDefault(themeKey, LayoutVariantCatalog.EMPTY);
    }

    /**
     * 主题声明的链接组展示位词汇表（link-groups D7）。未声明的主题 → 空词汇表。
     */
    public LinkSlotCatalog getThemeLinkSlots(String themeKey) {
        return themeLinkSlots.getOrDefault(themeKey, LinkSlotCatalog.EMPTY);
    }

    /**
     * 主题声明的布局变体目录全集（主题键 → 目录；快照校验遍历用）。
     */
    public Map<String, LayoutVariantCatalog> getThemeLayoutVariantsMap() {
        return themeLayoutVariants;
    }

    /**
     * 按主题键解析设置结构（无设置或未声明 → 空结构 {@link ThemeSettingsSchema#EMPTY}）。
     *
     * @param themeKey 规范化主题键（如 {@code 1/default}）
     */
    public ThemeSettingsSchema getThemeSettings(String themeKey) {
        return themeSettings.getOrDefault(themeKey, ThemeSettingsSchema.EMPTY);
    }

    /**
     * 规范化内容的 SHA-256（作为快照版本号，§9.1 contentHash）
     */
    public String getContentHash() {
        return contentHash;
    }

    /**
     * 制品级内容散列（制品键 → 该制品 JSON 原文的 SHA-256；键序稳定）。空快照为空映射。
     */
    public Map<String, String> getSourceHashes() {
        return sourceHashes;
    }

    public OffsetDateTime getLoadedAt() {
        return loadedAt;
    }

    /**
     * 在指定主题命名空间解析 Shape。
     *
     * @param themeName 规范化主题键（{@code siteId/themeName}）
     * @param name      Shape 名
     */
    @Nullable
    public Shape resolveShape(String themeName, String name) {
        return themeShapes.getOrDefault(themeName, Map.of()).get(name);
    }

    /**
     * 与活动主题无关地解析 Shape：任一主题命名空间，装载序首个匹配。
     * 供目录场景（未激活主题的 Shape 详情）与不依赖渲染上下文的存在性判定使用。
     * 主题间同名 Shape 取首个（共享名约定为同构声明，theme-owned-shapes D15；
     * 与 {@code ShapeT3Controller.list} 的并集口径一致）。
     */
    @Nullable
    public Shape resolveAnyShape(String name) {
        for (Map<String, Shape> shapes : themeShapes.values()) {
            Shape themeShape = shapes.get(name);
            if (themeShape != null) {
                return themeShape;
            }
        }
        return null;
    }

    /**
     * 按命名空间链解析 Shape（保存/渲染口径）：活动主题键序，链上无命中 → {@code null}。
     * 保存校验（{@code RegionService.saveDraft}）、渲染（{@code PanelResolver}）与控制台设置表单的
     * Shape 端点（{@code ShapeT3Controller.show}）共用此口径，保证表单渲染的
     * 设置结构与保存校验所用的同为一个 Shape（component-settings §8 Phase B 发现项）。
     *
     * @param themeKeys 活动主题键序
     * @param name      Shape 名
     */
    @Nullable
    public Shape resolveTrackShape(Collection<String> themeKeys, String name) {
        for (String themeKey : themeKeys) {
            Shape shape = resolveShape(themeKey, name);
            if (shape != null) {
                return shape;
            }
        }
        return null;
    }

    /**
     * 按页面名解析主题页面清单。
     *
     * @param themeName 规范化主题键（如 {@code 1/default}）
     * @param pageName  页面名（{@code home}、{@code channel}…）
     */
    @Nullable
    public PageManifest getThemeManifest(String themeName, String pageName) {
        return themeManifests.getOrDefault(themeName, Map.of()).get(pageName);
    }

    /**
     * 按渲染模板名解析主题页面清单（§6.2 模板轨道解析：精确匹配、无隐式回退）。
     *
     * @param themeName    规范化主题键
     * @param templateName 未组装模板名（{@code index}、{@code channel_video}…）
     * @return 绑定该模板的页面清单；未绑定时为 {@code null}（插槽渲染为空）
     */
    @Nullable
    public PageManifest resolveThemeManifestByTemplate(String themeName, String templateName) {
        return themeTemplateBindings.getOrDefault(themeName, Map.of()).get(templateName);
    }

    public int getShapeCount() {
        return themeShapes.values().stream().mapToInt(Map::size).sum();
    }

    public int getManifestCount() {
        return themeManifests.values().stream().mapToInt(Map::size).sum();
    }

    private static String sha256Hex(Map<String, String> sources) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            sources.forEach((key, value) -> {
                digest.update(key.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            });
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(value.getBytes(StandardCharsets.UTF_8));
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * 快照构建器。重复名称在加入时即报错（带来源信息），不会带病构建。
     */
    public static final class Builder {
        private final Map<String, Map<String, Shape>> themeShapes = new LinkedHashMap<>();
        private final Map<String, Map<String, PageManifest>> themeManifests = new LinkedHashMap<>();
        private final Map<String, ThemeSettingsSchema> themeSettings = new LinkedHashMap<>();
        private final Map<String, LayoutVariantCatalog> themeLayoutVariants = new LinkedHashMap<>();
        private final Map<String, LinkSlotCatalog> themeLinkSlots = new LinkedHashMap<>();
        /**
         * 规范化散列输入：key 为制品标识，value 为制品 JSON 原文。TreeMap 保证次序稳定。
         */
        private final Map<String, String> hashSources = new TreeMap<>();

        public Builder addThemeShape(String themeName, Shape shape, String json, String sourceDesc) {
            Map<String, Shape> shapes = themeShapes.computeIfAbsent(themeName, k -> new LinkedHashMap<>());
            if (shapes.putIfAbsent(shape.getName(), shape) != null) {
                throw new CompositionLoadException(sourceDesc,
                        "主题 " + themeName + " 内 Shape 名称重复: " + shape.getName());
            }
            hashSources.put("shape:theme:" + themeName + ":" + shape.getName(), json);
            return this;
        }

        public Builder addThemeManifest(String themeName, PageManifest manifest, String json, String sourceDesc) {
            Map<String, PageManifest> manifests = themeManifests.computeIfAbsent(themeName, k -> new LinkedHashMap<>());
            if (manifests.putIfAbsent(manifest.getName(), manifest) != null) {
                throw new CompositionLoadException(sourceDesc,
                        "主题 " + themeName + " 内页面名称重复: " + manifest.getName());
            }
            hashSources.put("manifest:theme:" + themeName + ":" + manifest.getName(), json);
            return this;
        }

        /**
         * 登记主题设置结构（每主题至多一次；空结构不登记，不计入内容散列）。
         *
         * @param themeKey 规范化主题键
         * @param schema   设置结构
         * @param json     {@code settings} 节点原文（计入内容散列，编辑清单即作废缓存）
         */
        public Builder addThemeSettings(String themeKey, ThemeSettingsSchema schema, String json) {
            if (schema.isEmpty()) {
                return this;
            }
            themeSettings.put(themeKey, schema);
            hashSources.put("settings:theme:" + themeKey, json);
            return this;
        }

        /**
         * 登记主题声明的布局变体目录（R1；每主题至多一次，空目录不登记、不计入内容散列）。
         * A1 起由 {@link ThemeManifestLoader} 自主题清单 {@code layoutVariants} 块装入。
         *
         * @param themeKey 规范化主题键（{@code siteId/themeName}）
         * @param catalog  布局变体目录
         * @param json     {@code layoutVariants} 节点原文（计入内容散列，编辑清单即作废缓存）
         */
        public Builder addThemeLayoutVariants(String themeKey, LayoutVariantCatalog catalog, String json) {
            if (catalog.isEmpty()) {
                return this;
            }
            themeLayoutVariants.put(themeKey, catalog);
            hashSources.put("layout-variants:theme:" + themeKey, json);
            return this;
        }

        /**
         * 登记主题声明的链接组展示位词汇表（link-groups D7；每主题至多一次，空词汇表不登记、
         * 不计入内容散列）。由 {@link ThemeManifestLoader} 自主题清单 {@code linkSlots} 块装入。
         *
         * @param themeKey 规范化主题键（{@code siteId/themeName}）
         * @param catalog  展示位词汇表
         * @param json     {@code linkSlots} 节点原文（计入内容散列，编辑清单即作废缓存）
         */
        public Builder addThemeLinkSlots(String themeKey, LinkSlotCatalog catalog, String json) {
            if (catalog.isEmpty()) {
                return this;
            }
            themeLinkSlots.put(themeKey, catalog);
            hashSources.put("link-slots:theme:" + themeKey, json);
            return this;
        }

        public RegistrySnapshot build() {
            return new RegistrySnapshot(this);
        }
    }
}
