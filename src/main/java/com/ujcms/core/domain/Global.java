package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ujcms.core.domain.base.GlobalBase;
import com.ujcms.core.support.Constants;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.Length;
import org.springframework.lang.Nullable;

import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 全局 实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties({"secret", "uploadSettings", "registerSettings", "emailSettings", "customsSettings"})
public class Global extends GlobalBase implements Serializable {
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

    @Nullable
    private Upload upload;

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

    @Nullable
    private Map<String, Object> customs;

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCustoms() {
        if (customs != null) {
            return customs;
        }
        String settings = getCustomsSettings();
        if (StringUtils.isBlank(settings)) {
            return Collections.emptyMap();
        }
        try {
            return Constants.MAPPER.readValue(settings, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot read value of Customs: " + settings, e);
        }
    }

    public void setCustoms(Map<String, Object> customs) {
        this.customs = customs;
        try {
            setCustomsSettings(Constants.MAPPER.writeValueAsString(customs));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write value of Customs", e);
        }
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
         * 允许上传的文库类型。格式如：pdf,doc,docx,xls,xlsx,ppt,pptx
         */
        private String docTypes = "pdf,doc,docx,xls,xlsx,ppt,pptx";
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

        public static boolean isValidType(String types, @Nullable String type) {
            return Arrays.asList(types.split(",")).contains(type);
        }

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

        public String getDocInputAccept() {
            return toInputAccept(getFileTypes());
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

        public long getDocLimitByte() {
            return this.videoLimit * 1024L * 1024L;
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
    @Pattern(regexp = "^/[\\w-]+$")
    public String getChannelUrl() {
        return super.getChannelUrl();
    }

    @Nullable
    @Override
    @Length(max = 50)
    @Pattern(regexp = "^/[\\w-]+$")
    public String getArticleUrl() {
        return super.getArticleUrl();
    }
}