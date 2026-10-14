package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

/**
 * 极简语义化版本（Shape 演进用，§13）。仅取 {@code major.minor.patch} 三段做比较，
 * 预发布/构建元数据（{@code -beta}、{@code +build}）按 §13.5 在比较时忽略。
 *
 * <p>配套的 {@link Range} 实现 §13.2 迁移块 {@code from} 的「semver range」子集：
 * {@code N.M.P}（精确）、{@code N.M.x}、{@code N.x}、{@code *}/{@code x}（任意）。
 *
 * @param major 主版本
 * @param minor 次版本
 * @param patch 修订号
 * @author PONY
 */
public record Semver(int major, int minor, int patch) implements Comparable<Semver> {
    /**
     * 解析 {@code major.minor.patch}（忽略 {@code -pre}/{@code +build}）。无法解析的段记为 0。
     *
     * @param text 版本串（如 {@code 2.0.0}）
     * @return 解析结果；{@code text} 为空时返回 {@code 0.0.0}
     */
    public static Semver parse(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return new Semver(0, 0, 0);
        }
        String core = stripQualifier(text.trim());
        String[] parts = core.split("\\.");
        return new Semver(numberAt(parts, 0), numberAt(parts, 1), numberAt(parts, 2));
    }

    @Override
    public int compareTo(Semver o) {
        int byMajor = Integer.compare(major, o.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, o.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        return Integer.compare(patch, o.patch);
    }

    private static String stripQualifier(String text) {
        int dash = text.indexOf('-');
        int plus = text.indexOf('+');
        int cut = text.length();
        if (dash >= 0) {
            cut = Math.min(cut, dash);
        }
        if (plus >= 0) {
            cut = Math.min(cut, plus);
        }
        return text.substring(0, cut);
    }

    private static int numberAt(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * semver range（§13.2 {@code from} 的子集）。每个维度要么固定为某数，要么为通配（{@code x}）。
     * 高维通配则低维必然通配（{@code 1.x} 即 minor/patch 均通配）。
     */
    public static final class Range {
        private static final int WILDCARD = -1;

        private final int major;
        private final int minor;
        private final int patch;
        private final String raw;

        private Range(int major, int minor, int patch, String raw) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.raw = raw;
        }

        /**
         * 解析范围串。{@code *}/{@code x}（任意）、{@code N.x}、{@code N.M.x}、{@code N.M.P}（精确）。
         * 缺省的低维按通配处理（{@code 1} 等价 {@code 1.x}）。
         *
         * @param text 范围串（如 {@code 1.x}）
         * @return 解析结果；空串视为任意版本
         */
        public static Range parse(@Nullable String text) {
            if (text == null || text.isBlank() || "*".equals(text.trim()) || "x".equalsIgnoreCase(text.trim())) {
                return new Range(WILDCARD, WILDCARD, WILDCARD, text == null ? "*" : text.trim());
            }
            String core = stripQualifier(text.trim());
            String[] parts = core.split("\\.");
            int major = segment(parts, 0);
            int minor = major == WILDCARD ? WILDCARD : segment(parts, 1);
            int patch = minor == WILDCARD ? WILDCARD : segment(parts, 2);
            return new Range(major, minor, patch, text.trim());
        }

        /**
         * 版本是否落在范围内（固定维度须相等，通配维度不约束）。
         */
        public boolean contains(Semver version) {
            if (major != WILDCARD && major != version.major()) {
                return false;
            }
            if (minor != WILDCARD && minor != version.minor()) {
                return false;
            }
            return patch == WILDCARD || patch == version.patch();
        }

        /**
         * 两个范围的版本集合是否相交（任一固定维度冲突即不相交）。
         */
        public boolean overlaps(Range other) {
            if (conflict(major, other.major)) {
                return false;
            }
            if (conflict(minor, other.minor)) {
                return false;
            }
            return !conflict(patch, other.patch);
        }

        /**
         * 范围的下界（通配维度取 0），用于按 {@code from} 升序排列迁移块。
         */
        public Semver lowerBound() {
            return new Semver(major == WILDCARD ? 0 : major,
                    minor == WILDCARD ? 0 : minor,
                    patch == WILDCARD ? 0 : patch);
        }

        public String raw() {
            return raw;
        }

        private static boolean conflict(int a, int b) {
            return a != WILDCARD && b != WILDCARD && a != b;
        }

        private static int segment(String[] parts, int index) {
            if (index >= parts.length) {
                return WILDCARD;
            }
            String part = parts[index].trim();
            if (part.isEmpty() || "x".equalsIgnoreCase(part) || "*".equals(part)) {
                return WILDCARD;
            }
            try {
                return Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return WILDCARD;
            }
        }
    }
}
