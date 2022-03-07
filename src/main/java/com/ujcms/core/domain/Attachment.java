package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.core.domain.base.AttachmentBase;
import com.ofwise.util.file.FilesEx;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 附件实体类
 *
 * @author PONY
 */
public class Attachment extends AttachmentBase implements Serializable {
    public Attachment() {
    }

    public Attachment(Integer siteId, Integer userId, String name, String path, String url, long length) {
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

    @JsonIncludeProperties({"id", "username"})
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

    public List<AttachmentRefer> getReferList() {
        return referList;
    }

    public void setReferList(List<AttachmentRefer> referList) {
        this.referList = referList;
    }
}