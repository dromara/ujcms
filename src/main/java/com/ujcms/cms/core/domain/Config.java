package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ujcms.cms.core.domain.base.ConfigBase;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.util.file.FileHandler;
import com.ujcms.util.file.LocalFileHandler;
import com.ujcms.util.file.MinIoFileHandler;
import com.ujcms.util.web.PathResolver;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.Length;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static com.ujcms.cms.core.domain.Config.Storage.TYPE_LOCAL;

/**
 * 全局配置 实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties({"uploadSettings", "registerSettings", "emailSettings", "uploadStorageSettings",
        "htmlStorageSettings", "customsSettings"})
public class Config extends ConfigBase implements Serializable {
    /**
     * 获取所有字段中的附件
     *
     * @return 附件列表
     */
    @JsonIgnore
    public List<String> getAttachmentUrls() {
        List<String> urls = new ArrayList<>();
        getModel().handleCustoms(getCustoms(), new Model.GetUrlsHandle(urls));
        return urls;
    }


    public Upload getUpload() {
        if (upload != null) {
            return upload;
        }
        String settings = getUploadSettings();
        if (StringUtils.isBlank(settings)) {
            return new Upload();
        }
        try {
            return Constants.MAPPER.readValue(settings, Upload.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot read value of Upload: " + settings, e);
        }
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
        try {
            setUploadSettings(Constants.MAPPER.writeValueAsString(upload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write value of Upload", e);
        }
    }

    public Storage getUploadStorage() {
        if (uploadStorage != null) {
            return uploadStorage;
        }
        String settings = getUploadStorageSettings();
        if (StringUtils.isBlank(settings)) {
            return new Storage(TYPE_LOCAL, "/uploads", "/uploads");
        }
        try {
            return Constants.MAPPER.readValue(settings, Storage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot read value of Storage: " + settings, e);
        }
    }

    public void setUploadStorage(Storage uploadStorage) {
        this.uploadStorage = uploadStorage;
        try {
            setUploadStorageSettings(Constants.MAPPER.writeValueAsString(uploadStorage));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write value of Storage", e);
        }
    }

    public Storage getHtmlStorage() {
        if (htmlStorage != null) {
            return htmlStorage;
        }
        String settings = getHtmlStorageSettings();
        if (StringUtils.isBlank(settings)) {
            return new Storage(TYPE_LOCAL, "", "");
        }
        try {
            return Constants.MAPPER.readValue(settings, Storage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot read value of Storage: " + settings, e);
        }
    }

    public void setHtmlStorage(Storage htmlStorage) {
        this.htmlStorage = htmlStorage;
        try {
            setHtmlStorageSettings(Constants.MAPPER.writeValueAsString(htmlStorage));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write value of Storage", e);
        }
    }

    public Storage getTemplateStorage() {
        if (templateStorage != null) {
            return templateStorage;
        }
        String settings = getTemplateStorageSettings();
        if (StringUtils.isBlank(settings)) {
            return new Storage(TYPE_LOCAL, "/templates", "/templates");
        }
        try {
            return Constants.MAPPER.readValue(settings, Storage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot read value of Storage: " + settings, e);
        }
    }

    public void setTemplateStorage(Storage templateStorage) {
        this.templateStorage = templateStorage;
        try {
            setTemplateStorageSettings(Constants.MAPPER.writeValueAsString(templateStorage));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write value of Storage", e);
        }
    }

    public Map<String, Object> getCustoms() {
        if (customs != null) {
            return customs;
        }
        String settings = getCustomsSettings();
        if (StringUtils.isBlank(settings)) {
            return Collections.emptyMap();
        }
        try {
            customs = Constants.MAPPER.readValue(settings, new TypeReference<Map<String, Object>>() {
            });
            return customs;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot read value of Customs: " + settings, e);
        }
    }

    public void setCustoms(Map<String, Object> customs) {
        try {
            setCustomsSettings(Constants.MAPPER.writeValueAsString(customs));
            this.customs = null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write value of Customs", e);
        }
    }

    @Nullable
    @JsonIgnore
    private Storage uploadStorage;
    @Nullable
    @JsonIgnore
    private Storage htmlStorage;
    @Nullable
    @JsonIgnore
    private Storage templateStorage;
    @Nullable
    private Upload upload;
    @Nullable
    private Map<String, Object> customs;

    @JsonIgnore
    private Model model = new Model();

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
    }

    @Nullable
    @Override
    @Length(max = 50)
    @Pattern(regexp = "^$|^/[\\w-]*$")
    public String getChannelUrl() {
        return super.getChannelUrl();
    }

    @Nullable
    @Override
    @Length(max = 50)
    @Pattern(regexp = "^$|^/[\\w-]*$")
    public String getArticleUrl() {
        return super.getArticleUrl();
    }

    public static class Upload implements Serializable {
        // 音频后缀：mp3,ogg
        /**
         * 允许上传的图片类型。格式如：jpg,jpeg,png,gif
         */
        private String imageTypes = "jpg,jpeg,png,gif";
        /**
         * 允许上传的视频类型。格式如：mp4,m3u8
         */
        private String videoTypes = "mp4,m3u8";
        /**
         * 允许上传的文库类型。格式如：doc,docx,xls,xlsx,ppt,pptx,pdf
         */
        private String libraryTypes = "doc,docx,xls,xlsx,ppt,pptx,pdf";
        /**
         * 允许DOC导入的文库类型。格式如：doc,docx,xls,xlsx
         */
        private String docTypes = "doc,docx,xls,xlsx";
        /**
         * 允许上传的文件类型。格式如：zip,7z,gz,bz2,iso,rar,pdf,doc,docx,xls,xlsx,ppt,pptx
         */
        private String fileTypes = "zip,7z,gz,bz2,iso,rar,pdf,doc,docx,xls,xlsx,ppt,pptx";
        /**
         * 图片最大长度。单位 MB
         */
        private int imageLimit = 0;
        /**
         * 视频最大长度。单位 MB
         */
        private int videoLimit = 0;
        /**
         * 文库最大长度。单位 MB
         */
        private int libraryLimit = 0;
        /**
         * DOC导入最大长度。单位 MB
         */
        private int docLimit = 0;
        /**
         * 文件最大长度。单位 MB
         */
        private int fileLimit = 0;
        /**
         * 图片最大宽度。等于 0 则不限制。默认1920
         */
        private int imageMaxWidth = 1920;
        /**
         * 图片最大高度。等于 0 则不限制。默认0
         */
        private int imageMaxHeight = 0;

        /**
         * 标准的允许上传后缀以逗号分割后缀名，如 `jpg,jpeg,png,gif`，而 input 的 accept 格式为 .jpg,.jpeg,.png,.gif。
         *
         * @param types `jpg,jpeg,png,gif`格式的后缀名
         * @return `.jpg,.jpeg,.png,.gif` 格式的后缀名
         */
        public static String toInputAccept(String types) {
            StringJoiner joiner = new StringJoiner(",.", ".", "");
            Arrays.asList(types.split(",")).forEach(joiner::add);
            return joiner.toString();
        }

        public String getImageInputAccept() {
            return toInputAccept(getImageTypes());
        }

        public String getVideoInputAccept() {
            return toInputAccept(getVideoTypes());
        }

        public String getLibraryInputAccept() {
            return toInputAccept(getLibraryTypes());
        }

        public String getDocInputAccept() {
            return toInputAccept(getDocTypes());
        }

        public String getFileInputAccept() {
            return toInputAccept(getFileTypes());
        }

        public long getImageLimitByte() {
            return this.imageLimit * 1024L * 1024L;
        }

        public long getVideoLimitByte() {
            return this.videoLimit * 1024L * 1024L;
        }

        public long getLibraryLimitByte() {
            return this.libraryLimit * 1024L * 1024L;
        }

        public long getDocLimitByte() {
            return this.docLimit * 1024L * 1024L;
        }

        public long getFileLimitByte() {
            return this.fileLimit * 1024L * 1024L;
        }

        public String getImageTypes() {
            return imageTypes;
        }

        public void setImageTypes(String imageTypes) {
            this.imageTypes = imageTypes;
        }

        public String getVideoTypes() {
            return videoTypes;
        }

        public void setVideoTypes(String videoTypes) {
            this.videoTypes = videoTypes;
        }

        public String getLibraryTypes() {
            return libraryTypes;
        }

        public void setLibraryTypes(String libraryTypes) {
            this.libraryTypes = libraryTypes;
        }

        public String getDocTypes() {
            return docTypes;
        }

        public void setDocTypes(String docTypes) {
            this.docTypes = docTypes;
        }

        public String getFileTypes() {
            return fileTypes;
        }

        public void setFileTypes(String fileTypes) {
            this.fileTypes = fileTypes;
        }

        public int getImageLimit() {
            return imageLimit;
        }

        public void setImageLimit(int imageLimit) {
            this.imageLimit = imageLimit;
        }

        public int getVideoLimit() {
            return videoLimit;
        }

        public void setVideoLimit(int videoLimit) {
            this.videoLimit = videoLimit;
        }

        public int getLibraryLimit() {
            return libraryLimit;
        }

        public void setLibraryLimit(int libraryLimit) {
            this.libraryLimit = libraryLimit;
        }

        public int getDocLimit() {
            return docLimit;
        }

        public void setDocLimit(int docLimit) {
            this.docLimit = docLimit;
        }

        public int getFileLimit() {
            return fileLimit;
        }

        public void setFileLimit(int fileLimit) {
            this.fileLimit = fileLimit;
        }

        public int getImageMaxWidth() {
            return imageMaxWidth;
        }

        public void setImageMaxWidth(int imageMaxWidth) {
            this.imageMaxWidth = imageMaxWidth;
        }

        public int getImageMaxHeight() {
            return imageMaxHeight;
        }

        public void setImageMaxHeight(int imageMaxHeight) {
            this.imageMaxHeight = imageMaxHeight;
        }
    }

    public static class Storage implements Serializable {
        /**
         * 存储类型(0:本地服务器,1:MinIO,2:阿里云,3:腾讯云,4:七牛云)
         */
        private int type = 0;
        /**
         * 存储路径
         */
        @NotNull
        @Length(max = 160)
        @Pattern(regexp = "^(?!.*\\.\\.).*$")
        private String path = "";
        /**
         * 访问路径
         */
        @NotNull
        @Length(max = 160)
        @Pattern(regexp = "^(?!.*\\.\\.).*$")
        private String url = "";

        private String endpoint = "";
        private String region = "";
        private String bucket = "";
        private String accessKey = "";
        private String secretKey = "";

        public Storage() {
        }

        public Storage(int type, String path, String url) {
            this.type = type;
            this.path = path;
            this.url = url;
        }

        public FileHandler getFileHandler(PathResolver pathResolver) {
            if (getType() == TYPE_MINIO) {
                return new MinIoFileHandler(endpoint, region, bucket, accessKey, secretKey, path, url);
            }
            return new LocalFileHandler(pathResolver, getPath(), getUrl());
        }

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        /**
         * 存储类型：本地服务器
         */
        public static final int TYPE_LOCAL = 0;
        /**
         * 存储类型：MinIO
         */
        public static final int TYPE_MINIO = 1;
        /**
         * 存储类型：阿里云
         */
        public static final int TYPE_ALIYUN = 2;
        /**
         * 存储类型：腾讯云
         */
        public static final int TYPE_QCLOUD = 3;
        /**
         * 存储类型：七牛云
         */
        public static final short TYPE_QINIU = 4;
    }
}