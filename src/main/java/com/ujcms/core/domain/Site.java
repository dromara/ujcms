package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ujcms.core.domain.base.SiteBase;
import com.ujcms.core.support.Constants;
import com.ujcms.core.support.Props;
import com.ofwise.util.file.FileHandler;
import com.ofwise.util.file.LocalFileHandler;
import com.ofwise.util.web.PathResolver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 站点 实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties({"watermarkSettings"})
public class Site extends SiteBase implements Serializable {
    public String getUrl() {
        return getUrl(null);
    }

    public String getUrl(@Nullable String url) {
        String dy = getDy();
        if (StringUtils.isNotBlank(url)) {
            return dy + url;
        }
        return dy;
    }

    public String getDy() {
        StringBuilder buff = new StringBuilder();
        buff.append(getProtocol()).append("://").append(getDomain());
        Integer port = getGlobal().getPort();
        if (port != null) {
            buff.append(":").append(port);
        }
        String contextPath = getGlobal().getContextPath();
        if (StringUtils.isNotBlank(contextPath)) {
            buff.append(contextPath);
        }
        if (StringUtils.isNotBlank(getSubDir())) {
            buff.append("/").append(getSubDir());
        }
        return buff.toString();
    }

    @Nullable
    public String getLinkUrl(@Nullable String linkUrl) {
        if (StringUtils.isBlank(linkUrl)) {
            return null;
        }
        String relativePathPrefix = "/";
        if (linkUrl.startsWith(relativePathPrefix) && getGlobal().getContextPath() != null) {
            return getGlobal().getContextPath() + linkUrl;
        }
        return linkUrl;
    }

    @Nullable
    @JsonIgnore
    public String getTemplate() {
        return getTemplate(TEMPLATE_FILE);
    }

    @Nullable
    public String getTemplate(@Nullable String template) {
        if (StringUtils.isBlank(template)) {
            return null;
        }
        return getTheme() + "/" + template;
    }

    public String getTitle() {
        if (StringUtils.isNotBlank(getSeoTitle())) {
            return getSeoTitle();
        }
        return getName();
    }

    /**
     * 获得模板的基础路径。/{id} + path。例如：/1/...
     */
    public String getBasePath(String path) {
        return "/" + getId() + path;
    }

    /**
     * 获取模板文件路径。如：/templates/1/default/_files
     */
    public String getFilesPath() {
        return Props.TEMPLATE_URL + getTheme() + "/" + Constants.TEMPLATE_FILES;
    }

    @JsonIncludeProperties({"id", "name", "url"})
    public List<Site> getPaths() {
        LinkedList<Site> parents = new LinkedList<>();
        Site bean = this;
        while (bean != null) {
            parents.addFirst(bean);
            bean = bean.getParent();
        }
        return parents;
    }

    public List<String> getNames() {
        return getPaths().stream().map(SiteBase::getName).collect(Collectors.toList());
    }

    public FileHandler getFileHandler(PathResolver pathResolver) {
        String storePrefix = Optional.ofNullable(getStorage().getPath()).orElse("");
        String displayPrefix = Optional.ofNullable(getStorage().getUrl()).orElse("");
        switch (this.storage.getType()) {
            // case Storage.MODE_FTP:
            //     break;
            // case Storage.MODE_MINIO:
            //     break;
            default:
                return new LocalFileHandler(pathResolver, storePrefix, displayPrefix);
        }
    }

    /**
     * 获取所有字段中的附件
     *
     * @return 附件列表
     */
    @JsonIgnore
    public List<String> getAttachmentUrls() {
        List<String> urls = new ArrayList<>();
        Optional.ofNullable(getLogo()).ifPresent(urls::add);
        if (getModel() != null) {
            getModel().handleCustoms(getCustomList(), new Model.GetUrlsHandle(urls));
        }
        return urls;
    }


    public Map<String, Object> getCustoms() {
        if (getModel() == null) {
            return Collections.emptyMap();
        }
        return getModel().assembleCustoms(getCustomList());
    }

    public void setCustoms(Map<String, Object> customs) {
        getCustomList().clear();
        customs.forEach((key, value) -> {
            if (value instanceof ArrayList) {
                ((ArrayList<?>) value)
                        .forEach(val -> getCustomList().add(new SiteCustom(getId(), key, String.valueOf(val))));
                return;
            }
            Optional.ofNullable(value).map(String::valueOf).filter(StringUtils::isNotBlank)
                    .ifPresent(it -> getCustomList().add(new SiteCustom(getId(), key, it)));
        });
    }

    @Nullable
    private Watermark watermark;

    public Watermark getWatermark() {
        if (watermark != null) {
            return watermark;
        }
        String settings = getWatermarkSettings();
        if (StringUtils.isBlank(settings)) {
            return new Watermark();
        }
        try {
            return Constants.MAPPER.readValue(settings, Watermark.class);
        } catch (JsonProcessingException e) {
            return new Watermark();
        }
    }

    public void setWatermark(Watermark watermark) throws JsonProcessingException {
        this.watermark = watermark;
        setWatermarkSettings(Constants.MAPPER.writeValueAsString(watermark));
    }

    /**
     * 子站点列表
     */
    @JsonIgnore
    private List<Site> children = new ArrayList<>();
    /**
     * 自定义字段列表
     */
    @JsonIgnore
    private List<SiteCustom> customList = new ArrayList<>();
    /**
     * 站点缓冲对象
     */
    @JsonIgnore
    private SiteBuffer buffer = new SiteBuffer();
    /**
     * 全局对象
     */
    @JsonIgnore
    private Global global = new Global();
    /**
     * 上级站点
     */
    @Nullable
    private Site parent;
    /**
     * 组织
     */
    @JsonIncludeProperties({"id", "name"})
    private Org org = new Org();
    /**
     * 模型
     */
    @Nullable
    @JsonIncludeProperties({"id", "name"})
    private Model model;
    /**
     * 附件储存点
     */
    @JsonIncludeProperties({"id", "name"})
    private Storage storage = new Storage();
    /**
     * HTML储存点
     */
    @JsonIncludeProperties({"id", "name"})
    private Storage htmlStorage = new Storage();
    /**
     * 手机HTML存储点
     */
    @JsonIncludeProperties({"id", "name"})
    private Storage mobileStorage = new Storage();

    public List<Site> getChildren() {
        return children;
    }

    public void setChildren(List<Site> children) {
        this.children = children;
    }

    public List<SiteCustom> getCustomList() {
        return customList;
    }

    public void setCustomList(List<SiteCustom> customList) {
        this.customList = customList;
    }

    public SiteBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(SiteBuffer buffer) {
        this.buffer = buffer;
    }

    public Global getGlobal() {
        return global;
    }

    public void setGlobal(Global global) {
        this.global = global;
    }

    @Nullable
    public Site getParent() {
        return parent;
    }

    public void setParent(Site parent) {
        this.parent = parent;
    }

    public Org getOrg() {
        return org;
    }

    public void setOrg(Org org) {
        this.org = org;
    }

    @Nullable
    public Model getModel() {
        return model;
    }

    public void setModel(@Nullable Model model) {
        this.model = model;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Storage getHtmlStorage() {
        return htmlStorage;
    }

    public void setHtmlStorage(Storage htmlStorage) {
        this.htmlStorage = htmlStorage;
    }

    public Storage getMobileStorage() {
        return mobileStorage;
    }

    public void setMobileStorage(Storage mobileStorage) {
        this.mobileStorage = mobileStorage;
    }

    // SiteBuffer 对象属性

    public long getViews() {
        return getBuffer().getViews();
    }

    /**
     * 站点首页模板
     */
    public static final String TEMPLATE_FILE = "index";

    public static final class Watermark implements Serializable {
        /**
         * 是否开启
         */
        private boolean enabled = false;
        /**
         * 水印图片地址
         */
        @Nullable
        private String overlay;
        /**
         * 水印位置。1-9。NorthWest, North, NorthEast, West, Center, East, SouthWest, South, SouthEast。默认9，右下位置
         */
        private int position = 9;
        /**
         * 透明度。0-100。0: 完全透明; 100: 完全不透明。默认50
         */
        private int dissolve = 50;
        /**
         * 图片最小宽度。小于这个宽度的图片不加水印。默认300
         */
        private int minWidth = 300;
        /**
         * 图片最小高度。小于这个高度的图片不加水印。默认300
         */
        private int minHeight = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Nullable
        public String getOverlay() {
            return overlay;
        }

        public void setOverlay(@Nullable String overlay) {
            this.overlay = overlay;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public int getDissolve() {
            return dissolve;
        }

        public void setDissolve(int dissolve) {
            this.dissolve = dissolve;
        }

        public int getMinWidth() {
            return minWidth;
        }

        public void setMinWidth(int minWidth) {
            this.minWidth = minWidth;
        }

        public int getMinHeight() {
            return minHeight;
        }

        public void setMinHeight(int minHeight) {
            this.minHeight = minHeight;
        }
    }
}