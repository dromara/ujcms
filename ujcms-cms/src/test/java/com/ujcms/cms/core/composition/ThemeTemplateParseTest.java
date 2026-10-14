package com.ujcms.cms.core.composition;

import freemarker.cache.FileTemplateLoader;
import freemarker.core.ParseException;
import freemarker.core.TemplateElement;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 参考主题模板语法护栏：遍历 webapp 模板目录下的全部 {@code .html} FreeMarker 模板
 * （方括号语法，与 application.yaml {@code tag_syntax: square_bracket} 一致），逐个解析。
 * 只做语法检查（include/宏在运行期解析，不在此展开），确保模板改动不引入解析错误。
 * 发行版仓库不携带 webapp 模板时跳过。
 */
class ThemeTemplateParseTest {
    private static Path templatesRoot;

    @BeforeAll
    static void locateRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        Path root = null;
        for (int i = 0; i < 3 && dir != null; i++, dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("ujcms-starter/src/main/webapp/themes"))) {
                root = dir;
                break;
            }
        }
        Assumptions.assumeTrue(root != null, "未找到 webapp 模板目录（发行版仓库不携带），跳过校验");
        templatesRoot = root.resolve("ujcms-starter/src/main/webapp/themes");
    }

    @Test
    void allFreemarkerTemplatesParse() throws IOException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setTagSyntax(Configuration.SQUARE_BRACKET_TAG_SYNTAX);
        cfg.setTemplateLoader(new FileTemplateLoader(templatesRoot.toFile()));
        cfg.setDefaultEncoding("UTF-8");

        List<String> failures = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(templatesRoot)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".html"))
                    .filter(p -> !p.toString().contains("node_modules"))
                    .sorted()
                    .forEach(p -> {
                        String name = templatesRoot.relativize(p).toString().replace('\\', '/');
                        try {
                            cfg.getTemplate(name);
                        } catch (ParseException e) {
                            failures.add(name + ": " + e.getMessage());
                        } catch (IOException e) {
                            failures.add(name + ": IO " + e.getMessage());
                        }
                    });
        }
        assertEquals(List.of(), failures, "存在解析失败的 FreeMarker 模板");
    }

    /**
     * 转义护栏（T3 / P2-4）：{@code output_format} 刻意保持关闭（application.yaml），XSS 安全
     * 依赖模板自行打开 {@code [#escape x as (x)!?html]}。{@code #escape} 是<strong>词法</strong>
     * 作用域——包含方的转义块不覆盖被 include 的文件——因此按单文件强制：任何文本输出插值
     * （{@code ${...}} / 遗留 {@code #{...}}）必须位于本文件的 {@code [#escape]} 块内。
     * 无插值输出的文件（纯宏库 icon.html、纯 include 分发页 error/5xx.html）无需转义块；
     * 刻意的原样输出（如保存期已净化的富文本正文）以 {@code [#escape]} 内嵌 {@code [#noescape]}
     * 显式声明。基于解析后的 AST 判定，故注释中的示例不误报、转义块开在插值之后的漏网也能抓到。
     * 仅约束随发行版一同交付的参考主题；第三方 ZIP 安装不在此拦截。
     */
    @Test
    @SuppressWarnings("deprecation") // AST 遍历 API（getRootTreeNode/TemplateElement）标记弃用但无替代品
    void allInterpolationsInsideEscape() throws IOException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setTagSyntax(Configuration.SQUARE_BRACKET_TAG_SYNTAX);
        cfg.setTemplateLoader(new FileTemplateLoader(templatesRoot.toFile()));
        cfg.setDefaultEncoding("UTF-8");

        List<String> failures = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(templatesRoot)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".html"))
                    .filter(p -> !p.toString().contains("node_modules"))
                    .sorted()
                    .forEach(p -> {
                        String name = templatesRoot.relativize(p).toString().replace('\\', '/');
                        Template template;
                        try {
                            template = cfg.getTemplate(name);
                        } catch (IOException e) {
                            // 解析/IO 失败由 allFreemarkerTemplatesParse 负责报告
                            return;
                        }
                        collectUnescapedInterpolations(template.getRootTreeNode(), false, name, failures);
                    });
        }
        assertEquals(List.of(), failures,
                "存在未被 [#escape] 覆盖的插值输出（刻意原样输出请用 [#escape] 内嵌 [#noescape] 显式声明）");
    }

    /**
     * 深度优先遍历模板 AST：进入 {@code EscapeBlock} 子树即视为已覆盖（其内的 {@code [#noescape]}
     * 是显式声明的原样输出，放行）；覆盖之外的插值节点（{@code DollarVariable} 与遗留数值输出
     * {@code NumericalOutput}，均为 freemarker.core 包内私有类，按类名匹配）记为违规。
     */
    @SuppressWarnings("deprecation") // 同上：TemplateElement 树是唯一可用的 AST 视图
    private static void collectUnescapedInterpolations(TemplateElement node, boolean escaped,
                                                       String name, List<String> failures) {
        String kind = node.getClass().getSimpleName();
        boolean covered = escaped || "EscapeBlock".equals(kind);
        if (!covered && ("DollarVariable".equals(kind) || "NumericalOutput".equals(kind))) {
            failures.add(name + ":" + node.getBeginLine() + " " + node.getCanonicalForm());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectUnescapedInterpolations((TemplateElement) node.getChildAt(i), covered, name, failures);
        }
    }
}
