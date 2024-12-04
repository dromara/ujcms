package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.ext.domain.base.FormBase;
import com.ujcms.commons.db.order.OrderEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.owasp.html.PolicyFactory;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表单实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"handler", "mainsJsons"})
public class Form extends FormBase implements Serializable, OrderEntity {
    private static final long serialVersionUID = 1L;
    /**
     * 站点
     */
    @JsonIncludeProperties({"id", "name", "url"})
    private Site site = new Site();
    /**
     * 组织
     */
    @JsonIncludeProperties({"id", "name"})
    private Org org = new Org();
    /**
     * 创建用户
     */
    @JsonIncludeProperties({"id", "username", "nickname"})
    private User user = new User();
    /**
     * 修改用户
     */
    @JsonIncludeProperties({"id", "username", "nickname"})
    private User modifiedUser = new User();
    /**
     * 表单类型
     */
    @JsonIncludeProperties({"id", "name", "modelId"})
    private FormType type = new FormType();
    /**
     * 表单扩展对象
     */
    @JsonIgnore
    private FormExt ext = new FormExt();
    /**
     * 自定义字段
     */
    @Nullable
    private transient Map<String, Object> customs;

    /**
     * 获取所有字段中的附件
     *
     * @return 附件列表
     */
    @JsonIgnore
    public List<String> getAttachmentUrls() {
        return getAttachmentUrls(getType().getModel());
    }

    public List<String> getAttachmentUrls(Model model) {
        return model.getUrlsFromMap(getCustoms());
    }

    @Schema(description = "是否可编辑")
    public boolean isEditable() {
        return StringUtils.isBlank(getType().getProcessKey()) ||
                (getStatus() != STATUS_REVIEWED && getStatus() != STATUS_REVIEWING);
    }

    public Map<String, Object> getCustoms() {
        if (customs != null) {
            return customs;
        }
        customs = new HashMap<>(16);
        Model model = getType().getModel();
        customs.putAll(model.assembleMap(getMainsJson()));
        customs.putAll(model.assembleMap(getClobsJson()));
        return customs;
    }

    public void setCustoms(Map<String, Object> customs) {
        this.customs = customs;
    }

    public void disassembleCustoms(Model model, PolicyFactory policyFactory) {
        Map<String, Object> map = model.sanitizeMap(getCustoms(), policyFactory);
        setCustoms(map);
        model.disassembleMap(map, this::setMainsJson, this::setClobsJson);
    }

    @JsonIgnore
    @Nullable
    public String getClobsJson() {
        return getExt().getClobsJson();
    }

    public void setClobsJson(@Nullable String clobsJson) {
        getExt().setClobsJson(clobsJson);
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public Org getOrg() {
        return org;
    }

    public void setOrg(Org org) {
        this.org = org;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getModifiedUser() {
        return modifiedUser;
    }

    public void setModifiedUser(User modifiedUser) {
        this.modifiedUser = modifiedUser;
    }

    public FormType getType() {
        return type;
    }

    public void setType(FormType type) {
        this.type = type;
    }

    public FormExt getExt() {
        return ext;
    }

    public void setExt(FormExt ext) {
        this.ext = ext;
    }

    // region TempFields

    @Schema(description = "任务ID")
    @Nullable
    private String taskId;

    @Nullable
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(@Nullable String taskId) {
        this.taskId = taskId;
    }
    // endregion

    /**
     * 状态：已审核
     */
    public static final short STATUS_REVIEWED = 0;

    /**
     * 状态：草稿
     */
    public static final short STATUS_DRAFT = 10;
    /**
     * 状态：待审核
     */
    public static final short STATUS_PENDING = 11;
    /**
     * 状态：审核中
     */
    public static final short STATUS_REVIEWING = 12;

    /**
     * 状态：已删除
     */
    public static final short STATUS_DELETED = 20;
    /**
     * 状态：已退回
     */
    public static final short STATUS_REJECTED = 22;

    /**
     * 表单审核流程类型
     */
    public static final String PROCESS_CATEGORY = "sys_form";
    public static final String PROCESS_VARIABLE_TYPE_ID = "typeId";

    public static final String NOT_FOUND = "Form not found. ID: ";
}