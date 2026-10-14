package com.ujcms.cms.core.domain;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.generated.GeneratedAttachment;
import com.ujcms.common.file.FilesEx;
import com.ujcms.common.web.Views;

/**
 * 附件实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Attachment extends GeneratedAttachment {

    public Attachment() {
    }

    public Attachment(Long siteId, Long userId, String name, String path, String url, long length) {
        setSiteId(siteId);
        setUserId(userId);
        setName(name);
        setPath(path);
        setUrl(url);
        setLength(length);
    }

    public String getSize() {
        return FilesEx.getSize(getLength());
    }

    @JsonIncludeProperties({"id", "username", "nickname"})
    private User user = new User();
    @JsonIncludeProperties({"id", "name"})
    private Site site = new Site();
    private List<AttachmentRefer> referList = new ArrayList<>();

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    /** 引用列表：仅详情输出（列表查询使用不含该集合的 ListResultMap，避免懒加载逐行触发） */
    @JsonView(Views.Whole.class)
    public List<AttachmentRefer> getReferList() {
        return referList;
    }

    public void setReferList(List<AttachmentRefer> referList) {
        this.referList = referList;
    }

    /** 共享资源库分类ID（null 表示不在共享资源库）。持久化字段，置于子类以避免改动生成代码。 */
    private Long sharedCategoryId;
    /** 当前用户是否已收藏（我的资源库）。瞬时字段，按页查询后填充。 */
    private Boolean favorited = false;

    public Long getSharedCategoryId() {
        return sharedCategoryId;
    }

    public void setSharedCategoryId(Long sharedCategoryId) {
        this.sharedCategoryId = sharedCategoryId;
    }

    public Boolean getFavorited() {
        return favorited;
    }

    public void setFavorited(Boolean favorited) {
        this.favorited = favorited;
    }

    /** 共享资源库分类名称。瞬时字段，仅共享列表查询填充。 */
    private String sharedCategoryName;

    public String getSharedCategoryName() {
        return sharedCategoryName;
    }

    public void setSharedCategoryName(String sharedCategoryName) {
        this.sharedCategoryName = sharedCategoryName;
    }
}