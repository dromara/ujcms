package com.ujcms.cms.core.service.args;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 守卫（docs/spec/article-data-permission/plan.md D26）：主代码写入查询表的分组键只能用 0 开头的服务端组名
 * （{@code EQ_0scope_…}），不得再出现请求式的组（{@code EQ_1_…}）——那会与请求的同名组合并成 OR。扫描本模块与
 * 检出里存在的兄弟模块的 {@code src/main/java} 字符串字面量
 */
class ServerQueryGroupGuardTest {
    private static final Pattern REQUEST_STYLE_GROUP_KEY = Pattern.compile(
            "\"(?:EQ|NE|GT|GE|LT|LE|In|NotIn|Contains|NotContains|StartsWith|StartWith|EndsWith|EndWith|IsNull|IsNotNull"
                    + "|Like|NotLike|Array[A-Za-z]+)_[1-9]\\d*(?:-\\d+)?_[A-Za-z@$][^\"]*\"");

    @Test
    void mainCodeWritesOnlyServerGroups() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : sourceRoots()) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    Matcher matcher = REQUEST_STYLE_GROUP_KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                    while (matcher.find()) {
                        offenders.add(root.relativize(file) + " " + matcher.group());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "request-style OR groups written by code (use a 0-prefixed server group):\n"
                + String.join("\n", offenders));
    }

    /** 本模块的 src/main/java，加上检出里存在的兄弟模块（surefire 工作目录即模块目录） */
    private static List<Path> sourceRoots() {
        Path module = Paths.get("").toAbsolutePath();
        List<Path> roots = new ArrayList<>();
        for (String name : List.of("ujcms-cms", "ujcms-commercial", "ujcms-common")) {
            Path candidate = module.resolveSibling(name).resolve("src/main/java").normalize();
            if (Files.isDirectory(candidate)) {
                roots.add(candidate);
            }
        }
        if (roots.isEmpty()) {
            roots.add(module.resolve("src/main/java"));
        }
        return roots;
    }
}
