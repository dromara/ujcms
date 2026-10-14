package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 主题种子导出计划（theme-seed-export §8，镜像 {@link SeedPlan}）。只读——导出引擎在产出
 * 文件束的同时汇总本报告，供向导确认对话框与干跑预览使用。
 *
 * <p>每类别按来源三元组分类计数（§4 四类 + linked 跳过）：
 * <ul>
 *   <li>{@code originUnmodified} — 本主题来源且散列复现（第 1 类：原样重发，originId 不变）；</li>
 *   <li>{@code originModified} — 本主题来源但操作员改过（第 2 类：以当前投影重发，originId 不变）；</li>
 *   <li>{@code operatorNew} — 无来源标记的操作员行（第 3 类：铸造 originId，纯函数不写库）；</li>
 *   <li>{@code foreignOrigin} — 他主题来源行（第 4 类：按第 3 类处理，绝不认领他源，D3）；</li>
 *   <li>{@code linkedSkipped} — 仅经闭包/引用触达而未显式选择的操作员/他源栏目与专题
 *       （D7：linked 行属操作员，缺省不导出——引用以别名迟绑，不断链）。</li>
 * </ul>
 *
 * @param channels      栏目分类计数
 * @param articles      文章分类计数
 * @param topics        专题分类计数
 * @param blocks        区块定义分类计数（E4a / D27：本主题来源 + blockRef 引用闭包）
 * @param votes         投票定义分类计数（E4b / D30：本主题来源 + voteRef 引用闭包；文章式
 *                      无别名，linkedSkipped 恒为 0）
 * @param surveys       问卷定义分类计数（E4d / D35：本主题来源 + 向导显式多选——无引用闭包）
 * @param forms         表单类型定义分类计数（E4e / D40：本主题来源 + 向导显式多选——无引用闭包；
 *                      文章式无别名，linkedSkipped 恒为 0）
 * @param linkGroups   链接组分类计数（E4f / D43：本主题来源 + 向导显式多选——无引用闭包；
 *                      区块式有别名，linkedSkipped 恒为 0——分组无迟绑引用可跳）
 * @param models        模型定义分类计数（model-seeding D6：本主题来源 + 绑定闭包——栏目双绑定/
 *                      专题/区块条目模型 + 表单绑定模型（E4e 闭包边）；恒不 linked 跳过，闭包胜于 D7）
 * @param dicts         字典类型定义分类计数（model-seeding D8：本主题来源 + 导出模型的
 *                      dictTypeId 绑定闭包；恒不 linked 跳过）
 * @param panels        区域面板分类计数
 * @param regions       区域（结构树包裹行）分类计数
 * @param blockItems    导出的区块条目数（A6 权宜格式无来源三元组，只计条数——条目是文章的
 *                      子行，D25）
 * @param linkItems 导出的链接条数（E4f / D42：链接随分组定义、无独立三元组，只计条数）
 * @param assetBytes    资源总字节数（{@code seed/content/assets/**}）
 * @param largestAssets 最大的资源文件（至多 10 个，按字节数降序；§7.3 超限排查用）
 * @param externalUrls  外链 URL（去重排序；导出原样保留，跨站安装不随包，§5.2(c)）
 * @param refIssues        闭包诊断（§3）：选择之外的引用、被跳过的父栏目、不可种子化字段等
 * @param totalBytes       文件束总字节数（JSON + 资源）
 * @param maxBytes         安装侧大小上限（{@code ujcms.theme.install.maxSizeMb}，§7.3）
 * @param oversize         是否超限（超限的导出不可安装——打包前快速失败的依据）
 * @param settingsSnapshot 设置快照报告（§7.4 / D21；服务层附加——干跑恒报告「若快照会写什么」，
 *                         引擎不知清单，恒为 {@code null}）
 * @author PONY
 */
public record ExportPlan(CategoryCounts channels, CategoryCounts articles, CategoryCounts topics,
                         CategoryCounts blocks, CategoryCounts votes, CategoryCounts surveys,
                         CategoryCounts forms, CategoryCounts linkGroups, CategoryCounts models,
                         CategoryCounts dicts,
                         CategoryCounts panels, CategoryCounts regions, int blockItems,
                         int linkItems,
                         long assetBytes, List<AssetFile> largestAssets, List<String> externalUrls,
                         List<RefIssue> refIssues, long totalBytes, long maxBytes, boolean oversize,
                         @Nullable SettingsSnapshot settingsSnapshot) {

    /**
     * 引擎构造形（无设置快照报告——快照是清单概念，属服务层，§7.4）
     */
    public ExportPlan(CategoryCounts channels, CategoryCounts articles, CategoryCounts topics,
                      CategoryCounts blocks, CategoryCounts votes, CategoryCounts surveys,
                      CategoryCounts forms, CategoryCounts linkGroups, CategoryCounts models,
                      CategoryCounts dicts,
                      CategoryCounts panels, CategoryCounts regions, int blockItems,
                      int linkItems,
                      long assetBytes, List<AssetFile> largestAssets, List<String> externalUrls,
                      List<RefIssue> refIssues, long totalBytes, long maxBytes, boolean oversize) {
        this(channels, articles, topics, blocks, votes, surveys, forms, linkGroups, models, dicts,
                panels, regions, blockItems, linkItems, assetBytes, largestAssets, externalUrls,
                refIssues, totalBytes, maxBytes, oversize, null);
    }

    /**
     * 附上设置快照报告（§7.4：预览恒附、应用后附于刷新报告）。
     */
    public ExportPlan withSettingsSnapshot(@Nullable SettingsSnapshot snapshot) {
        return new ExportPlan(channels, articles, topics, blocks, votes, surveys, forms, linkGroups,
                models, dicts, panels, regions, blockItems, linkItems, assetBytes, largestAssets,
                externalUrls, refIssues, totalBytes, maxBytes, oversize, snapshot);
    }

    /**
     * 单类别的来源分类计数（口径见类注释）
     */
    public record CategoryCounts(int originUnmodified, int originModified, int operatorNew,
                                 int foreignOrigin, int linkedSkipped) {
        public static final CategoryCounts EMPTY = new CategoryCounts(0, 0, 0, 0, 0);

        /**
         * 计入类别的总导出行数（linked 跳过行不导出，不在其列）
         */
        public int exported() {
            return originUnmodified + originModified + operatorNew + foreignOrigin;
        }
    }

    /**
     * 资源文件明细（§7.3 最大文件排查）
     *
     * @param relpath 资源相对路径（{@code seed/content/assets/} 下）
     * @param bytes   字节数
     */
    public record AssetFile(String relpath, long bytes) {
    }

    /**
     * 闭包诊断条目（§3：widen-or-warn 的 warn 侧——向导可据此放宽选择后重试）
     *
     * @param category 类别（channel / article / topic / panel / blockItem）
     * @param ref      涉及的引用（originId / 别名 / 行ID，按类别取可读形）
     * @param detail   说明（何处引用、为何不可解析或被跳过）
     * @param severity 级别（D18）：{@code warning} 缺省——可散装的闭包提示；{@code error}——
     *                 装出去必坏的数据问题（如引用字段携带未展开的种子令牌），导出仍可继续
     *                 （同站导入无损，D11），但向导以红色警示
     */
    public record RefIssue(String category, String ref, String detail, String severity) {

        public static final String SEVERITY_WARNING = "warning";
        public static final String SEVERITY_ERROR = "error";

        public RefIssue(String category, String ref, String detail) {
            this(category, ref, detail, SEVERITY_WARNING);
        }
    }

    /**
     * 设置快照报告（§7.4 / D21）：把 {@code theme_state.settings_json_} 三作用域已存值写入发射清单
     * 已声明字段 {@code default} 的干跑/应用汇总。预览恒计算（向导第 2 步先于第 3 步开关展示；
     * ZIP 响应为二进制无报告通道），开关只决定是否落笔。
     *
     * @param fields    可写入的已声明字段值数（按清单声明匹配的已存值；与既有默认值相同也计入）
     * @param dropped   未声明即丢弃的已存键（点路径，镜像设置保存的 H5 口径：{@code <group>.<field>}、
     *                  {@code pages.<name>…}、{@code variants.<surface>:<value>…}——弃警不拒）
     * @param manifests 涉及的清单文件（{@code theme.manifest.json}、{@code pages/<file>}；仅含
     *                  实际有字段写入者）
     * @param issues    可移植性提示（warning 级）：指向本站存储的 URL 值、站内ID引用值——
     *                  按原样快照（同站可用，跨站为源站指向/悬挂引用；清单默认值无令牌展开机制，
     *                  不走反向资源遍历，D21）
     */
    public record SettingsSnapshot(int fields, List<String> dropped, List<String> manifests,
                                   List<RefIssue> issues) {
    }
}
