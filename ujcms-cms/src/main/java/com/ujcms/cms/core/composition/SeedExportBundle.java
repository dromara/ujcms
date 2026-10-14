package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.SortedMap;

/**
 * 主题种子导出文件束（theme-seed-export §7.1）。键为 {@code seed/} 目录下的相对路径
 * （如 {@code content/channels.channels.json}、{@code content/assets/img/…}、
 * {@code regions/home.regions.json}），按字典序排序；值为文件字节。E2 的打包器
 * （ZIP / refreshSeedDir）只做逐文件落盘，不再触碰内容——确定性由本束一次性保证（D4）。
 *
 * @param files     相对路径 → 文件字节（字典序）
 * @param plan      导出计划报告（§8）
 * @param adoptions 待认领行（§4.2：第 3 类行的铸造三元组候选；{@code adoptOrigin=true} 时由
 *                  {@link SeedOriginAdopter} 回写，引擎自身不写库——D2）
 * @author PONY
 */
public record SeedExportBundle(SortedMap<String, byte[]> files, ExportPlan plan,
                               List<SeedAdoption> adoptions) {

    /**
     * 确定性 JSON 序列化（D4）：键序取 {@link SeedHash#canonicalTree} 的规范化口径（递归字典序，
     * 与散列同源），两空格缩进 + LF 换行（跨平台字节一致）+ 结尾换行。同一值恒产出同一字节序列——
     * 「内容未变则重导出字节一致」的唯一序列化路径。
     */
    public static byte[] canonicalJsonBytes(Object value) {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(
                Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER));
        printer.indentObjectsWith(new DefaultIndenter("  ", "\n"));
        printer.indentArraysWith(new DefaultIndenter("  ", "\n"));
        try {
            String json = SeedHash.canonicalWriter().with(printer).writeValueAsString(SeedHash.canonicalTree(value));
            return (json + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize seed export JSON", e);
        }
    }
}
