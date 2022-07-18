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

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
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
@JsonIgnoreProperties({"uploadSettings", "securitySettings", "registerSettings", "smsSettings", "emailSettings",
        "uploadStorageSettings", "htmlStorageSettings", "templateStorageSettings", "customsSettings"})
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

    public Security getSecurity() {
        if (security != null) {
            return security;
        }
        String settings = getSecuritySettings();
        if (StringUtils.isBlank(settings)) {
            return new Security();
        }
        try {
            return Constants.MAPPER.readValue(settings, Security.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot read value of Security: " + settings, e);
        }
    }

    public void setSecurity(Security security) {
        this.security = security;
        try {
            setSecuritySettings(Constants.MAPPER.writeValueAsString(security));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write value of Security", e);
        }
    }

    public Sms getSms() {
        if (sms != null) {
            return sms;
        }
        String settings = getSmsSettings();
        if (StringUtils.isBlank(settings)) {
            return new Sms();
        }
        try {
            return Constants.MAPPER.readValue(settings, Sms.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot read value of Sms: " + settings, e);
        }
    }

    public void setSms(Sms sms) {
        this.sms = sms;
        try {
            setSmsSettings(Constants.MAPPER.writeValueAsString(sms));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write value of Sms", e);
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
    private Upload upload;
    @Nullable
    private Security security;
    @Nullable
    @JsonIgnore
    private Sms sms;
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

    /**
     * 上传配置类
     * <p>
     * https://developer.mozilla.org/en-US/docs/Web/Media/Formats/Containers
     * https://www.runoob.com/html/html-media.html
     * https://www.runoob.com/html/html5-video.html
     * https://www.runoob.com/html/html5-audio.html
     * https://www.w3schools.com/html/html_media.asp
     * https://www.w3schools.com/html/html5_video.asp
     * https://www.w3schools.com/html/html5_audio.asp
     */
    public static class Upload implements Serializable {
        /**
         * 允许上传的图片类型。格式如：jpg,jpeg,png,gif
         */
        private String imageTypes = "jpg,jpeg,png,gif";
        /**
         * 允许上传的视频类型。格式如：mp4,webm,ogg
         */
        private String videoTypes = "mp4,webm,ogg";
        /**
         * 允许上传的音频格式。格式如：mp3,ogg,wav
         */
        private String audioTypes = "mp3,ogg,wav";
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
         * 音频最大长度。单位 MB
         */
        private int audioLimit = 0;
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

        public String getAudioInputAccept() {
            return toInputAccept(getAudioTypes());
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

        public long getAudioLimitByte() {
            return this.audioLimit * 1024L * 1024L;
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

        public String getAudioTypes() {
            return audioTypes;
        }

        public void setAudioTypes(String audioTypes) {
            this.audioTypes = audioTypes;
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

        public int getAudioLimit() {
            return audioLimit;
        }

        public void setAudioLimit(int audioLimit) {
            this.audioLimit = audioLimit;
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

    /**
     * 安全配置类
     * <p>
     * Windows密码强度策略：
     * 不能包含用户的帐户名，不能包含用户姓名中超过两个连续字符的部分
     * 至少有六个字符长
     * 包含以下四类字符中的三类字符:
     * 英文大写字母(A 到 Z)
     * 英文小写字母(a 到 z)
     * 10 个基本数字(0 到 9)
     * 非字母字符(例如 !、$、#、%)
     */
    public static class Security implements Serializable {
        /**
         * 密码最长使用天数。可以将密码设置为在某些天数(介于 1 到 999 之间)后到期，或者将天数设置为 0，指定密码永不过期。
         * 0-999。0不限制，常用值90
         */
        @Min(0)
        @Max(999)
        private int passwordMaxDays = 0;
        /**
         * 密码最短使用天数。可以设置一个介于 1 和 998 天之间的值，或者将天数设置为 0，允许立即更改密码。
         * 0-998。0不限制，常用值15
         */
        @Min(0)
        @Max(998)
        private int passwordMinDays = 0;
        /**
         * 密码过期提前警告天数。
         * 0-90。0不警告，常用值7
         */
        @Min(0)
        @Max(90)
        private int passwordWarnDays = 0;
        /**
         * 强制密码历史。该值必须介于 0 个和 24 个密码之间。
         * 0-24。0不限制，常用值5
         */
        @Min(0)
        @Max(24)
        private int passwordMaxHistory = 0;
        /**
         * 密码最小长度
         * 0-16。常用值8
         */
        @Min(0)
        @Max(16)
        private int passwordMinLength = 0;
        /**
         * 密码最大长度
         * 16-128。常用值64
         */
        @Min(16)
        @Max(128)
        private int passwordMaxLength = 64;
        /**
         * 密码强度(0:不限制; 1:大小字母+数字; 2:大写字母+小写字母+数字; 3:大小写字母+数字+特殊字符; 4:大写字母+小写字母+数字+特殊字符)
         * 0-3。0不限制，常用值2
         */
        @Min(0)
        @Max(4)
        private int passwordStrength = 0;
        /**
         * 用户最大尝试次数
         * 0-100。0不限制，常用值5
         */
        @Min(0)
        @Max(100)
        private int userMaxAttempts = 0;
        /**
         * 登录锁定时间。单位分钟
         * 1-1440，0不限制，常用值30
         */
        @Min(1)
        @Max(1440)
        private int userLockMinutes = 30;
        /**
         * IP验证码次数
         * 0-100。0必须提供验证码，常用值3
         */
        @Min(0)
        @Max(100)
        private int ipCaptchaAttempts = 3;
        /**
         * IP允许尝试次数
         * 0-999。0不限制，常用值10
         */
        @Min(0)
        @Max(999)
        private int ipMaxAttempts = 0;
        /**
         * 双因子认证
         */
        private boolean twoFactor = false;

        public int getPasswordMaxDays() {
            return passwordMaxDays;
        }

        public void setPasswordMaxDays(int passwordMaxDays) {
            this.passwordMaxDays = passwordMaxDays;
        }

        public int getPasswordMinDays() {
            return passwordMinDays;
        }

        public void setPasswordMinDays(int passwordMinDays) {
            this.passwordMinDays = passwordMinDays;
        }

        public int getPasswordWarnDays() {
            return passwordWarnDays;
        }

        public void setPasswordWarnDays(int passwordWarnDays) {
            this.passwordWarnDays = passwordWarnDays;
        }

        public int getPasswordMaxHistory() {
            return passwordMaxHistory;
        }

        public void setPasswordMaxHistory(int passwordMaxHistory) {
            this.passwordMaxHistory = passwordMaxHistory;
        }

        public int getPasswordMinLength() {
            return passwordMinLength;
        }

        public void setPasswordMinLength(int passwordMinLength) {
            this.passwordMinLength = passwordMinLength;
        }

        public int getPasswordMaxLength() {
            return passwordMaxLength;
        }

        public void setPasswordMaxLength(int passwordMaxLength) {
            this.passwordMaxLength = passwordMaxLength;
        }

        public int getPasswordStrength() {
            return passwordStrength;
        }

        public void setPasswordStrength(int passwordStrength) {
            this.passwordStrength = passwordStrength;
        }

        public int getUserMaxAttempts() {
            return userMaxAttempts;
        }

        public void setUserMaxAttempts(int userMaxAttempts) {
            this.userMaxAttempts = userMaxAttempts;
        }

        public int getUserLockMinutes() {
            return userLockMinutes;
        }

        public void setUserLockMinutes(int userLockMinutes) {
            this.userLockMinutes = userLockMinutes;
        }

        public int getIpCaptchaAttempts() {
            return ipCaptchaAttempts;
        }

        public void setIpCaptchaAttempts(int ipCaptchaAttempts) {
            this.ipCaptchaAttempts = ipCaptchaAttempts;
        }

        public int getIpMaxAttempts() {
            return ipMaxAttempts;
        }

        public void setIpMaxAttempts(int ipMaxAttempts) {
            this.ipMaxAttempts = ipMaxAttempts;
        }

        public boolean isTwoFactor() {
            return twoFactor;
        }

        public void setTwoFactor(boolean twoFactor) {
            this.twoFactor = twoFactor;
        }
    }

    public static class Sms implements Serializable {
        /**
         * 未开启
         */
        public static final int PROVIDER_NONE = 0;
        /**
         * 阿里云短信
         */
        public static final int PROVIDER_ALIYUN = 1;
        /**
         * 腾讯云短信
         */
        public static final int PROVIDER_TENCENTCLOUD = 2;

        /**
         * 短信服务商(0:未开启,1:阿里云短信,2:腾讯云短信)
         */
        @Min(0)
        @Max(2)
        private int provider = 0;
        /**
         * IP每日最大量
         */
        @Min(1)
        @Max(99999)
        private int maxPerIp = 100;
        /**
         * 验证码长度
         */
        @Min(4)
        @Max(6)
        private int codeLength = 6;
        /**
         * 验证码过期时间。单位：分钟
         */
        @Min(3)
        @Max(30)
        private int codeExpires = 10;
        /**
         * 测试号码
         */
        @Nullable
        @Length(max = 30)
        private String testMobile;

        /**
         * 短信签名
         */
        @Nullable
        @Length(max = 50)
        private String signName;
        /**
         * accessKeyId
         */
        @Nullable
        @Length(max = 128)
        private String accessKeyId;
        /**
         * accessKeySecret
         */
        @Nullable
        @Length(max = 128)
        private String accessKeySecret;
        /**
         * 模板Code
         */
        @Nullable
        @Length(max = 32)
        private String templateCode;
        /**
         * 变量名称
         */
        @Length(max = 20)
        private String codeName = "code";
        /**
         * secretId
         */
        @Nullable
        @Length(max = 128)
        private String secretId;
        /**
         * secretKey
         */
        @Nullable
        @Length(max = 128)
        private String secretKey;
        /**
         * SDKAppID
         */
        @Nullable
        @Length(max = 64)
        private String sdkAppId;
        /**
         * 地域region
         */
        @Nullable
        @Length(max = 64)
        private String region;
        /**
         * 模板ID
         */
        @Nullable
        @Length(max = 32)
        private String templateId;

        public int getProvider() {
            return provider;
        }

        public void setProvider(int provider) {
            this.provider = provider;
        }

        public int getMaxPerIp() {
            return maxPerIp;
        }

        public void setMaxPerIp(int maxPerIp) {
            this.maxPerIp = maxPerIp;
        }

        public int getCodeLength() {
            return codeLength;
        }

        public void setCodeLength(int codeLength) {
            this.codeLength = codeLength;
        }

        public int getCodeExpires() {
            return codeExpires;
        }

        public void setCodeExpires(int codeExpires) {
            this.codeExpires = codeExpires;
        }

        @Nullable
        public String getTestMobile() {
            return testMobile;
        }

        public void setTestMobile(@Nullable String testMobile) {
            this.testMobile = testMobile;
        }

        @Nullable
        public String getSignName() {
            return signName;
        }

        public void setSignName(@Nullable String signName) {
            this.signName = signName;
        }

        @Nullable
        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(@Nullable String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        @Nullable
        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(@Nullable String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        @Nullable
        public String getTemplateCode() {
            return templateCode;
        }

        public void setTemplateCode(@Nullable String templateCode) {
            this.templateCode = templateCode;
        }

        public String getCodeName() {
            return codeName;
        }

        public void setCodeName(String codeName) {
            this.codeName = codeName;
        }

        @Nullable
        public String getSecretId() {
            return secretId;
        }

        public void setSecretId(@Nullable String secretId) {
            this.secretId = secretId;
        }

        @Nullable
        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(@Nullable String secretKey) {
            this.secretKey = secretKey;
        }

        @Nullable
        public String getSdkAppId() {
            return sdkAppId;
        }

        public void setSdkAppId(@Nullable String sdkAppId) {
            this.sdkAppId = sdkAppId;
        }

        @Nullable
        public String getRegion() {
            return region;
        }

        public void setRegion(@Nullable String region) {
            this.region = region;
        }

        @Nullable
        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(@Nullable String templateId) {
            this.templateId = templateId;
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