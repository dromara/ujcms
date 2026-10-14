package com.ujcms.cms.core.component;

import com.ujcms.cms.core.support.Constants;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author PONY
 */
@Component
public class TemplateService {
    private final ResourceLoader resourceLoader;

    public TemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 列出主题 {@code templates/} 目录下符合前缀的模板文件名（不含后缀），用于后台模板选择器。
     * 仅扫描 {@code <theme>/templates/} 顶层（{@code app/} 等子目录的片段不是可选页面模板）。
     *
     * @param theme       主题资源路径（已含站点 ID 与主题名，例如 {@code /themes/1/default}
     *                    或 {@code file:/abs/path/themes/1/default}）
     * @param startWith   文件名前缀，如 {@code "channel"} 或 {@code "article"}
     * @return 去重并去后缀后的模板基名列表
     */
    public List<String> getTemplates(String theme, String startWith) throws IOException {
        File themeDir = resourceLoader.getResource(theme).getFile();
        if (!themeDir.exists() || !themeDir.isDirectory()) {
            return new ArrayList<>();
        }
        File templatesDir = new File(themeDir, "templates");
        return scan(templatesDir, startWith, Constants.TEMPLATE_SUFFIX);
    }

    /**
     * 模板文件是否存在于主题 {@code templates/} 顶层（渲染期孤立模板选择回退判定，
     * template-track-variants Q2）。主题目录或 {@code templates/} 目录缺失、存储不可读
     * → 无法校验，返回 {@code true} 宽松放行（与孤立选择计数的空集合整面不计同规）。
     *
     * @param theme 主题资源路径（已含站点 ID 与主题名，与 {@link #getTemplates} 同口径）
     * @param name  未组装模板名（不含后缀），如 {@code channel_video}
     */
    public boolean templateExists(String theme, String name) {
        File templatesDir;
        try {
            templatesDir = new File(resourceLoader.getResource(theme).getFile(), "templates");
        } catch (IOException e) {
            return true;
        }
        if (!templatesDir.isDirectory()) {
            return true;
        }
        return new File(templatesDir, name + Constants.TEMPLATE_SUFFIX).isFile();
    }

    private static List<String> scan(File scanDir, String startWith, String suffix) {
        if (!scanDir.exists() || !scanDir.isDirectory()) {
            return new ArrayList<>();
        }
        File[] matches = scanDir.listFiles((dir, name) ->
                name.startsWith(startWith) && name.endsWith(suffix));
        if (matches == null) {
            return new ArrayList<>();
        }
        // 按名称排序：File.listFiles 的顺序取决于文件系统，下拉列表不应随部署环境变化
        Set<String> names = new TreeSet<>();
        for (File match : matches) {
            String name = match.getName();
            names.add(name.substring(0, name.length() - suffix.length()));
        }
        return new ArrayList<>(names);
    }
}
