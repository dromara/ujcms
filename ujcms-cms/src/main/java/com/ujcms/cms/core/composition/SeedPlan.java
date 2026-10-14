package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 种子导入干跑计划（theme-marketplace §6.2 {@code POST …/seed/preview}）。只读——
 * 逐类别对比种子声明与站点现状，为每个确认对话框供数：
 * 安装/激活的三态提示、更新报告的种子差异、重置/移除前的红色警示计数。
 *
 * <p>每类别口径（§6.2）：
 * <ul>
 *   <li>{@code new} — 将新插入（无本源行、无别名冲突）；</li>
 *   <li>{@code linked} — 别名撞上非本源行，导入时指向既有行、绝不收编（栏目/专题；
 *       投放为目标插槽已被非本源投放占用）；</li>
 *   <li>{@code existing} — 本源行已存在（幂等导入将按 overwrite 策略处理）；</li>
 *   <li>{@code operatorModified} — 已存在且操作员改过（R4：当前行投影散列 ≠ 导入时刻
 *       {@code origin_hash_}；散列缺失视作未改，宁可少报）；</li>
 *   <li>{@code seedChanged} — 已存在且种子内容变了（种子侧模拟保存期处理后的投影散列 ≠
 *       导入时刻散列）。与 operatorModified 独立计数，可同时为真。</li>
 * </ul>
 *
 * @param channels   栏目计划
 * @param articles   文章计划
 * @param topics     专题计划
 * @param blocks     区块定义计划（theme-seed-export E4a / D25——仅定义组；条目组是 A6 填空形不入计划）
 * @param votes      投票定义计划（theme-seed-export E4b / D29——文章式，linked 恒为 0）
 * @param surveys    问卷定义计划（theme-seed-export E4d / D35——文章式；干跑只读不经端口，
 *                   低配站点照常给出对比）
 * @param forms       表单类型定义计划（theme-seed-export E4e / D38——文章式；干跑只读不经端口，
 *                    低配站点照常给出对比）
 * @param linkGroups 链接组计划（theme-seed-export E4f / D42——区块式，别名撞既有分组 →
 *                    linked；链接随定义不入计划）
 * @param models      模型定义计划（model-seeding D1——(type, name) 撞可见集 → linked，出厂同名即常态）
 * @param dicts       字典类型定义计划（model-seeding D8——别名撞可见集 → linked；条目随类型）
 * @param panels      区域面板计划
 * @param regions     区域计划（结构树包裹行）
 * @param assetBytes  内容种子资源总字节数（{@code seed/content/assets/**}，导入时复制进上传存储）
 * @param collisions  别名冲突明细（linked 行——导入不插入、不认领）
 * @author PONY
 */
public record SeedPlan(CategoryPlan channels, CategoryPlan articles, CategoryPlan topics,
                       CategoryPlan blocks, CategoryPlan votes, CategoryPlan surveys,
                       CategoryPlan forms, CategoryPlan linkGroups, CategoryPlan models,
                       CategoryPlan dicts, CategoryPlan panels, CategoryPlan regions,
                       long assetBytes, List<Collision> collisions) {

    /**
     * 单类别计划计数（口径见类注释）
     */
    public record CategoryPlan(@JsonProperty("new") int added, int linked, int existing,
                               int operatorModified, int seedChanged) {
        public static final CategoryPlan EMPTY = new CategoryPlan(0, 0, 0, 0, 0);
    }

    /**
     * 别名冲突明细（导入时记为 linked：引用映射指向既有行，绝不收编，§5.4）
     *
     * @param category   类别（channel / topic / block / model / dict / linkGroup / region）
     * @param originId   种子内的 originId
     * @param alias      冲突别名（区域为 {@code page#region}）
     * @param existingId 既有非本源行ID
     */
    public record Collision(String category, String originId, String alias, Long existingId) {
    }
}
