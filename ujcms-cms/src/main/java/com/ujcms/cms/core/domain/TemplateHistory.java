package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedTemplateHistory;

import java.util.Objects;

/**
 * 模板编辑历史实体类（只追加）。每行捕获模板文件的一次文本状态：控制台文件管理器的保存/上传/删除，
 * 以及漂移回填（特性上线前存量或旁路写入——主题安装、ZIP 上传、改名移动——在下次保存时先补记）。
 * 以存储相对路径为键、不设站点列/外键：模板存储是一棵全局树，路径已编码归属站点；历史在主题卸载、
 * 文件删除后仍可凭路径恢复（docs/spec/template-history/plan.md §5/§7）。
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class TemplateHistory extends GeneratedTemplateHistory {
    /**
     * 来源：编辑器保存（含新建文件）
     */
    public static final String SOURCE_SAVE = "save";
    /**
     * 来源：单文件上传（覆盖即坏保存同类）
     */
    public static final String SOURCE_UPLOAD = "upload";
    /**
     * 来源：回填（保存/上传/恢复时发现当前文件内容未被最新历史覆盖——特性上线前存量或旁路写入——先补记再继续）
     */
    public static final String SOURCE_BACKFILL = "backfill";
    /**
     * 来源：删除（删除前捕获被销毁的内容；凭此恢复即重建文件）
     */
    public static final String SOURCE_DELETE = "delete";
    /**
     * 来源：恢复（本条是恢复动作写入的新状态，{@code restored_from_} 指向被恢复的历史条目）
     */
    public static final String SOURCE_RESTORE = "restore";

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TemplateHistory bean = (TemplateHistory) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
