package com.ujcms.cms.core.domain;

import static com.ujcms.cms.core.domain.Config.Storage.TYPE_LOCAL;
import static com.ujcms.cms.core.support.Constants.MAPPER;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.hibernate.validator.constraints.Length;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import org.jspecify.annotations.Nullable;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ujcms.cms.core.domain.generated.GeneratedConfig;
import com.ujcms.cms.core.support.Commercial;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.file.FtpClientProperties;
import com.ujcms.common.file.FtpFileHandler;
import com.ujcms.common.file.LocalFileHandler;
import com.ujcms.common.file.MinIoFileHandler;
import com.ujcms.common.security.oauth.QqOauthClient;
import com.ujcms.common.security.oauth.WeiboOauthClient;
import com.ujcms.common.security.oauth.WeixinOauthClient;
import com.ujcms.common.web.PathResolver;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 全局配置实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"uploadSettings", "greySettings", "securitySettings", "registerSettings", "smsSettings", "emailSettings",
        "aiSettings", "oauthSettings", "uploadStorageSettings", "htmlStorageSettings", "templateStorageSettings",
        "packageStorageSettings",
        "customsSettings", "marketplaceSettings", "consoleSettings", "handler"})
public class Config extends GeneratedConfig {

    /**
     * 是否置灰
     *
     * @return 当开启置灰，且处于置灰日，返回 `true`，否则返回 `false`
     */
    public boolean isGreyStyle() {
        return getGrey().isEnabled() && getGrey().isOnDate();
    }

    /**
     * 获取所有字段中的附件
     *
     * @return 附件列表
     */
    @JsonIgnore
    public List<String> getAttachmentUrls() {
        return getModel().getUrlsFromMap(getCustoms());
    }

    /**
     * 上传文件URL地址前缀
     */
    public String getUploadUrlPrefix() {
        return getUploadStorage().getUrl();
    }

    public Upload getUpload() {
        if (upload != null) {
            return upload;
        }
        String settings = getUploadSettings();
        if (StringUtils.isBlank(settings)) {
            upload = new Upload();
            return upload;
        }
        try {
            upload = Constants.MAPPER.readValue(settings, Upload.class);
            return upload;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read value of Upload: " + settings, e);
        }
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
        try {
            setUploadSettings(Constants.MAPPER.writeValueAsString(upload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Upload", e);
        }
    }

    public Grey getGrey() {
        if (grey != null) {
            return grey;
        }
        String settings = getGreySettings();
        if (StringUtils.isBlank(settings)) {
            return new Grey();
        }
        try {
            return Constants.MAPPER.readValue(settings, Grey.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read value of Grey: " + settings, e);
        }
    }

    public void setGrey(Grey grey) {
        this.grey = grey;
        try {
            setGreySettings(Constants.MAPPER.writeValueAsString(grey));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Grey", e);
        }
    }

    public Register getRegister() {
        if (register != null) {
            return register;
        }
        String settings = getRegisterSettings();
        if (StringUtils.isBlank(settings)) {
            return new Register();
        }
        try {
            return Constants.MAPPER.readValue(settings, Register.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read value of Register: " + settings, e);
        }
    }

    public void setRegister(Register register) {
        this.register = register;
        try {
            setRegisterSettings(Constants.MAPPER.writeValueAsString(register));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Register", e);
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
            throw new IllegalStateException("Cannot read value of Security: " + settings, e);
        }
    }

    public void setSecurity(Security security) {
        this.security = security;
        try {
            setSecuritySettings(Constants.MAPPER.writeValueAsString(security));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Security", e);
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
            throw new IllegalStateException("Cannot read value of Sms: " + settings, e);
        }
    }

    public void setSms(Sms sms) {
        this.sms = sms;
        try {
            setSmsSettings(Constants.MAPPER.writeValueAsString(sms));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Sms", e);
        }
    }

    /**
     * AI设置含服务商明文密钥，不可随 Config 序列化（如 /api/env/config）。
     * 后台展示走 GlobalSettingsController.showAi 的“只写不回显”脱敏路径（密钥置空，留空提交表示不修改）
     */
    @JsonIgnore
    public Ai getAi() {
        if (ai != null) {
            return ai;
        }
        String settings = getAiSettings();
        if (StringUtils.isBlank(settings)) {
            return new Ai();
        }
        try {
            return Constants.MAPPER.readValue(settings, Ai.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read value of Ai: " + settings, e);
        }
    }

    public void setAi(Ai ai) {
        this.ai = ai;
        try {
            setAiSettings(Constants.MAPPER.writeValueAsString(ai));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Ai", e);
        }
    }

    /**
     * 第三方登录设置含各服务商的应用密钥，不可随 Config 序列化，模板亦不可达（TemplateMemberAccessPolicy）。
     * 后台展示走 GlobalSettingsT2Controller.showOauth 的“只写不回显”脱敏路径；模板与公开接口只读
     * {@link #getOauthProviders()}
     */
    @JsonIgnore
    public Oauth getOauth() {
        if (oauth != null) {
            return oauth;
        }
        String settings = getOauthSettings();
        if (StringUtils.isBlank(settings)) {
            oauth = new Oauth();
            return oauth;
        }
        try {
            oauth = MAPPER.readValue(settings, Oauth.class);
            return oauth;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read value of Oauth: " + settings, e);
        }
    }

    public void setOauth(Oauth oauth) {
        try {
            setOauthSettings(MAPPER.writeValueAsString(oauth));
            this.oauth = oauth;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Oauth", e);
        }
    }

    /**
     * 可用的第三方登录服务商代码（已启用且凭据齐全，按 {@link Oauth#PROVIDERS} 的顺序），不含任何凭据。
     * 登录/注册模板据此决定显示哪些第三方登录入口：{@code config.oauthProviders?seq_contains('weixin')}。
     * 只读：不加 READ_ONLY 时 Jackson 会把无 setter 的集合 getter 当作写入口（USE_GETTERS_AS_SETTERS），
     * 基础设置回传整个 Config JSON 即向这个不可变列表追加元素而报错
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public List<String> getOauthProviders() {
        return getOauth().usableProviders();
    }

    public Email getEmail() {
        if (email != null) {
            return email;
        }
        String settings = getEmailSettings();
        if (StringUtils.isBlank(settings)) {
            return new Email();
        }
        try {
            return Constants.MAPPER.readValue(settings, Email.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read value of Email: " + settings, e);
        }
    }

    public void setEmail(Email email) {
        this.email = email;
        try {
            setEmailSettings(Constants.MAPPER.writeValueAsString(email));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Email", e);
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
            throw new IllegalStateException(e);
        }
    }

    public void setUploadStorage(Storage uploadStorage) {
        this.uploadStorage = uploadStorage;
        try {
            setUploadStorageSettings(Constants.MAPPER.writeValueAsString(uploadStorage));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
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
            throw new IllegalStateException(e);
        }
    }

    public void setHtmlStorage(Storage htmlStorage) {
        this.htmlStorage = htmlStorage;
        try {
            setHtmlStorageSettings(Constants.MAPPER.writeValueAsString(htmlStorage));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Storage getTemplateStorage() {
        if (templateStorage != null) {
            return templateStorage;
        }
        String settings = getTemplateStorageSettings();
        if (StringUtils.isBlank(settings)) {
            return new Storage(TYPE_LOCAL, "/themes", "/themes");
        }
        try {
            return Constants.MAPPER.readValue(settings, Storage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public void setTemplateStorage(Storage templateStorage) {
        this.templateStorage = templateStorage;
        try {
            setTemplateStorageSettings(Constants.MAPPER.writeValueAsString(templateStorage));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 主题包存储点（install-flow §6 P3 / Q1）：独立存储点，LOCAL 默认自有根目录，
     * 不复用模板存储的保留目录。ZIP 本体按 {@code <name>/<version>.zip} 存放。
     */
    public Storage getPackageStorage() {
        if (packageStorage != null) {
            return packageStorage;
        }
        String settings = getPackageStorageSettings();
        if (StringUtils.isBlank(settings)) {
            return new Storage(TYPE_LOCAL, "/theme-packages", "/theme-packages");
        }
        try {
            return Constants.MAPPER.readValue(settings, Storage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public void setPackageStorage(Storage packageStorage) {
        this.packageStorage = packageStorage;
        try {
            setPackageStorageSettings(Constants.MAPPER.writeValueAsString(packageStorage));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Object> getCustoms() {
        if (customs != null) {
            return customs;
        }
        String json = getCustomsSettings();
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        customs = getModel().assembleMap(json);
        return customs;
    }

    public void setCustoms(Map<String, Object> customs) {
        this.customs = customs;
    }

    public void disassembleCustoms(Model model, HtmlSanitizeService sanitizeService) {
        Map<String, Object> map = model.sanitizeMap(getCustoms(), sanitizeService);
        setCustoms(map);
        try {
            setCustomsSettings(MAPPER.writeValueAsString(map));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nullable
    private Upload upload;
    @Nullable
    private Grey grey;
    @Nullable
    private Register register;
    @Nullable
    private Security security;
    @Nullable
    @JsonIgnore
    private Sms sms;
    @Nullable
    @JsonIgnore
    private Ai ai;
    @Nullable
    @JsonIgnore
    private Email email;
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
    @JsonIgnore
    private Storage packageStorage;

    /**
     * AI设置原始 JSON 字段。映射 ujcms_config.ai_settings_，不放入 GeneratedConfig（generated 文件不手工编辑）
     */
    @Nullable
    private String aiSettings;

    @Nullable
    public String getAiSettings() {
        return aiSettings;
    }

    public void setAiSettings(@Nullable String aiSettings) {
        this.aiSettings = aiSettings;
    }

    /**
     * 第三方登录设置原始 JSON 字段。映射 ujcms_config.oauth_settings_（SM4 加密落库），不放入 GeneratedConfig（生成器 ignoreColumn）
     */
    @Nullable
    private String oauthSettings;
    @Nullable
    @JsonIgnore
    private Oauth oauth;

    @Nullable
    public String getOauthSettings() {
        return oauthSettings;
    }

    public void setOauthSettings(@Nullable String oauthSettings) {
        this.oauthSettings = oauthSettings;
        this.oauth = null;
    }

    /**
     * 主题包存储点设置原始 JSON 字段。映射 ujcms_config.package_storage_settings_，
     * 不放入 GeneratedConfig（generated 文件不手工编辑）
     */
    @Nullable
    private String packageStorageSettings;

    @Nullable
    public String getPackageStorageSettings() {
        return packageStorageSettings;
    }

    public void setPackageStorageSettings(@Nullable String packageStorageSettings) {
        this.packageStorageSettings = packageStorageSettings;
    }

    /**
     * 控制台默认外观原始 JSON 字段。映射 ujcms_config.console_settings_，不放入 GeneratedConfig（生成器 ignoreColumn）。
     * 随“基础设置”整行保存（Entities.copy 只拷简单类型，故拷的是本字段而非 {@link #getConsole()}）。
     */
    @Nullable
    private String consoleSettings;
    // 缓存字段不加 @JsonIgnore：Jackson 对某一访问器的忽略会连带整个 console 属性，getConsole() 就不再序列化（upload/grey 同例）
    @Nullable
    private Console console;

    @Nullable
    public String getConsoleSettings() {
        return consoleSettings;
    }

    public void setConsoleSettings(@Nullable String consoleSettings) {
        this.consoleSettings = consoleSettings;
        this.console = null;
    }

    @Valid
    public Console getConsole() {
        if (console != null) {
            return console;
        }
        String settings = getConsoleSettings();
        if (StringUtils.isBlank(settings)) {
            console = new Console();
            return console;
        }
        try {
            console = MAPPER.readValue(settings, Console.class);
            return console;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read value of Console: " + settings, e);
        }
    }

    public void setConsole(Console console) {
        this.console = console;
        try {
            setConsoleSettings(MAPPER.writeValueAsString(console));
            this.console = console;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Console", e);
        }
    }

    private String uploadsExtensionBlacklist = "";
    private String filesExtensionBlacklist = "";
    /**
     * 自定义字段
     */
    @Nullable
    private transient Map<String, Object> customs;
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

    @Nullable
    @Override
    @Length(max = 50)
    @Pattern(regexp = "^$|^/[\\w-]*$")
    public String getTopicUrl() {
        return super.getTopicUrl();
    }

    public String getUploadsExtensionBlacklist() {
        return uploadsExtensionBlacklist;
    }

    public void setUploadsExtensionBlacklist(String uploadsExtensionBlacklist) {
        this.uploadsExtensionBlacklist = uploadsExtensionBlacklist;
    }

    public String getFilesExtensionBlacklist() {
        return filesExtensionBlacklist;
    }

    public void setFilesExtensionBlacklist(String filesExtensionBlacklist) {
        this.filesExtensionBlacklist = filesExtensionBlacklist;
    }

    /**
     * 商业版级别（0=社区版，1=T1 基础版，2=T2 标准版，3=T3 高级版）。模板中以
     * {@code config.commercialTier} 访问，用于按版本展示/隐藏商业功能区块。
     */
    public int getCommercialTier() {
        return Commercial.getTier();
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
    @Schema(name = "Config.Upload", description = "上传配置")
    public static class Upload implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 允许上传的图片类型。格式如：jpg,jpeg,jfif,pjpeg,pjp,png,gif,webp
         */
        private String imageTypes = "jpg,jpeg,jfif,pjpeg,pjp,png,gif,webp";
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
         * 允许DOC导入的文库类型。格式如：doc,docx,wps,xls,xlsx
         */
        private String docTypes = "doc,docx,wps,xls,xlsx";
        /**
         * 允许上传的文件类型。格式如：zip,7z,gz,bz2,iso,rar,doc,docx,xls,xlsx,ppt,pptx,pdf,mp3,ogg,wav,mp4,webm,ogg,jpg,jpeg,jfif,pjpeg,pjp,png,gif,webp
         */
        private String fileTypes = "zip,7z,gz,bz2,iso,rar,doc,docx,xls,xlsx,ppt,pptx,pdf,mp3,ogg,wav,mp4,webm,ogg,jpg,jpeg,jfif,pjpeg,pjp,png,gif,webp";
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

        /**
         * 媒体类型。取视频和音频的合集
         */
        public String getMediaTypes() {
            StringBuilder buffer = new StringBuilder(getVideoTypes());
            if (buffer.length() > 0) {
                buffer.append(",");
            }
            buffer.append(getAudioTypes());
            return buffer.toString();
        }

        /**
         * 媒体大小限制。取视频和音频中值更大的那个。
         */
        public int getMediaLimit() {
            // 0 为不限制，优先取 0
            if (this.videoLimit == 0 || this.audioLimit == 0) {
                return 0;
            }
            return Math.max(this.videoLimit, this.audioLimit);
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

        public String getMediaInputAccept() {
            return toInputAccept(getMediaTypes());
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

        public long getMediaLimitByte() {
            return getMediaLimit() * 1024L * 1024L;
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

    @Schema(name = "Config.Grey", description = "置灰配置")
    public static class Grey implements Serializable {
        private static final long serialVersionUID = 1L;

        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        private boolean enabled = false;
        private String greyDates = "09-08,12-13";

        @JsonIgnore
        public boolean isOnDate() {
            String[] dates = StringUtils.split(greyDates, ',');
            if (dates == null || dates.length <= 0) {
                return false;
            }
            OffsetDateTime now = OffsetDateTime.now();
            String nowDate = formatter.format(now);
            for (String date : dates) {
                if (nowDate.equals(date.trim())) {
                    return true;
                }
            }
            return false;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getGreyDates() {
            return greyDates;
        }

        public void setGreyDates(String greyDates) {
            this.greyDates = greyDates;
        }
    }

    @Schema(name = "Config.Register", description = "注册配置")
    public static class Register implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 大头像名称
         */
        public static final String AVATAR_LARGE = "@large.";
        /**
         * 中头像名称
         */
        public static final String AVATAR_MEDIUM = "@medium.";
        /**
         * 小头像名称
         */
        public static final String AVATAR_SMALL = "@small.";
        /**
         * 是否启用
         */
        private boolean enabled = false;
        /**
         * 验证方式。1:不验证, 2:人工验证, 3:邮箱地址验证, 4:手机号码验证
         */
        private int verifyMode = VERIFY_MODE_NONE;
        /**
         * 用户名最小长度
         */
        private int usernameMinLength = 4;
        /**
         * 用户名最大长度
         */
        private int usernameMaxLength = 12;
        /**
         * 用户名正则表达式。默认允许为 中文 数字 字符 . - _
         */
        private String usernameRegex = "^[0-9a-zA-Z\\u4e00-\\u9fa5\\.\\-_]+$";
        /**
         * 默认头像
         */
        private String avatar = "/uploads/avatar/default/default.png";
        /**
         * 小头像尺寸
         */
        private int smallAvatarSize = 80;
        /**
         * 中头像尺寸
         */
        private int mediumAvatarSize = 240;
        /**
         * 大头像尺寸
         */
        private int largeAvatarSize = 960;

        /**
         * 验证模式：不验证
         */
        public static final int VERIFY_MODE_NONE = 1;
        /**
         * 验证模式：人工验证
         */
        public static final int VERIFY_MODE_MANUAL = 2;
        /**
         * 验证模式：邮箱地址验证
         */
        public static final int VERIFY_MODE_EMAIL = 3;
        /**
         * 验证模式：手机号码验证
         */
        public static final int VERIFY_MODE_MOBILE = 4;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getVerifyMode() {
            return verifyMode;
        }

        public void setVerifyMode(int verifyMode) {
            this.verifyMode = verifyMode;
        }

        public int getUsernameMinLength() {
            return usernameMinLength;
        }

        public void setUsernameMinLength(int usernameMinLength) {
            this.usernameMinLength = usernameMinLength;
        }

        public int getUsernameMaxLength() {
            return usernameMaxLength;
        }

        public void setUsernameMaxLength(int usernameMaxLength) {
            this.usernameMaxLength = usernameMaxLength;
        }

        public String getUsernameRegex() {
            return usernameRegex;
        }

        public void setUsernameRegex(String usernameRegex) {
            this.usernameRegex = usernameRegex;
        }

        public String getAvatar() {
            return avatar;
        }

        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }

        public int getSmallAvatarSize() {
            return smallAvatarSize;
        }

        public void setSmallAvatarSize(int smallAvatarSize) {
            this.smallAvatarSize = smallAvatarSize;
        }

        public int getMediumAvatarSize() {
            return mediumAvatarSize;
        }

        public void setMediumAvatarSize(int mediumAvatarSize) {
            this.mediumAvatarSize = mediumAvatarSize;
        }

        public int getLargeAvatarSize() {
            return largeAvatarSize;
        }

        public void setLargeAvatarSize(int largeAvatarSize) {
            this.largeAvatarSize = largeAvatarSize;
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
    @Schema(name = "Config.Security", description = "安全配置")
    public static class Security implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
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
         * 16-64。常用值64
         */
        @Min(16)
        @Max(64)
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
        /**
         * 首次登录强制改密。开启后，{@code pwd_must_change_} 为 true 的用户（后台新建用户 / 管理员重置口令后、
         * 种子 admin）登录时需先修改密码。默认关闭，不影响开箱行为（等保 身份鉴别）。
         */
        private boolean forceChangePasswordOnFirstLogin = false;
        /**
         * SSRF白名单
         */
        @Size(max = 1500)
        private String ssrfWhiteList = "";

        /**
         * SSRF白名单通配符
         */
        public static final String SSRF_WILDCARD = "*";
        /**
         * 密码强度0：密码可以是任意字符
         */
        public static final int PASSWORD_STRENGTH_0 = 0;
        /**
         * 密码强度1：密码中必须包含字母、数字
         */
        public static final int PASSWORD_STRENGTH_1 = 1;
        /**
         * 密码强度2：密码中必须包含大写字母、小写字母、数字
         */
        public static final int PASSWORD_STRENGTH_2 = 2;
        /**
         * 密码强度3：密码中必须包含字母、数字、特殊字符
         */
        public static final int PASSWORD_STRENGTH_3 = 3;
        /**
         * 密码强度4：密码中必须包含大写字母、小写字母、数字、特殊字符
         */
        public static final int PASSWORD_STRENGTH_4 = 4;

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public List<String> getSsrfList() {
            return Arrays.stream(StringUtils.split(getSsrfWhiteList(), "\r\n"))
                    .filter(StringUtils::isNotBlank).map(String::trim).toList();
        }

        public String getPasswordPattern() {
            if (this.passwordStrength == PASSWORD_STRENGTH_1) {
                return "(?=.*[0-9])(?=.*[A-Za-z]).*";
            }
            if (this.passwordStrength == PASSWORD_STRENGTH_2) {
                return "(?=.*[0-9])(?=.*[A-Z])(?=.*[a-z]).*";
            }
            if (this.passwordStrength == PASSWORD_STRENGTH_3) {
                return "(?=.*[0-9])(?=.*[A-Za-z])(?=.*[^a-zA-Z0-9]).*";
            }
            if (this.passwordStrength == 4) {
                return "(?=.*[0-9])(?=.*[A-Z])(?=.*[a-z])(?=.*[^a-zA-Z0-9]).*";
            }
            return ".*";
        }

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

        public boolean isForceChangePasswordOnFirstLogin() {
            return forceChangePasswordOnFirstLogin;
        }

        public void setForceChangePasswordOnFirstLogin(boolean forceChangePasswordOnFirstLogin) {
            this.forceChangePasswordOnFirstLogin = forceChangePasswordOnFirstLogin;
        }

        public String getSsrfWhiteList() {
            return ssrfWhiteList;
        }

        public void setSsrfWhiteList(String ssrfWhiteList) {
            this.ssrfWhiteList = ssrfWhiteList;
        }
    }

    @Schema(name = "Config.Sms", description = "短信配置")
    public static class Sms implements Serializable {
        private static final long serialVersionUID = 1L;
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
         * 验证码过期时间。单位：分钟。带外（短信）验证码的有效期不宜超过 10 分钟（NIST SP 800-63B）
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

    @Schema(name = "Config.Ai", description = "AI配置")
    public static class Ai implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 机器翻译引擎保留代码：百度翻译。服务商代码正则不允许冒号，保留代码不会与用户配置冲突
         */
        public static final String MT_BAIDU = "mt:baidu";
        /**
         * 机器翻译引擎保留代码：有道翻译
         */
        public static final String MT_YOUDAO = "mt:youdao";
        /**
         * 机器翻译引擎保留代码：DeepL
         */
        public static final String MT_DEEPL = "mt:deepl";

        /**
         * 是否为机器翻译引擎代码（{@code mt:} 前缀保留给机器翻译，仅翻译服务商可选）
         *
         * @param code 服务商代码
         * @return 是否机器翻译引擎
         */
        public static boolean isMtProvider(@Nullable String code) {
            return code != null && code.startsWith("mt:");
        }

        /**
         * 是否开启
         */
        private boolean enabled = false;
        /**
         * 服务商配置列表。均为 OpenAI 兼容协议（POST {baseUrl}/chat/completions）
         */
        @Valid
        @Size(max = 10)
        private List<Provider> providers = new java.util.ArrayList<>();
        /**
         * 默认服务商代码
         */
        @Nullable
        @Length(max = 30)
        private String defaultProvider;
        /**
         * 写作服务商代码。为空则使用默认服务商
         */
        @Nullable
        @Length(max = 30)
        private String writingProvider;
        /**
         * 审核服务商代码。为空则使用默认服务商
         */
        @Nullable
        @Length(max = 30)
        private String moderationProvider;
        /**
         * 翻译服务商代码（文章预翻译）。为空则使用默认服务商
         */
        @Nullable
        @Length(max = 30)
        private String translationProvider;
        /**
         * 机器翻译引擎配置。翻译服务商选择 {@code mt:*} 保留代码时使用对应引擎凭证
         */
        @Valid
        private Mt mt = new Mt();
        /**
         * 日志保留天数。网络安全法第21条要求日志留存不少于六个月，故默认180天、最低30天
         */
        @Min(30)
        private int logRetentionDays = 180;
        /**
         * 全局每日token上限（提示词+生成，全部站点合计）。0为不限制
         */
        @Min(0)
        private long dailyTokenLimit = 0;

        /**
         * 按代码查找服务商。代码为空时回落到默认服务商
         *
         * @param code 服务商代码。可为 {@code null}
         * @return 服务商配置。没有找到则返回 {@code null}
         */
        @Nullable
        @JsonIgnore
        public Provider resolveProvider(@Nullable String code) {
            String actual = StringUtils.isBlank(code) ? defaultProvider : code;
            if (StringUtils.isBlank(actual)) {
                return null;
            }
            return providers.stream().filter(p -> actual.equals(p.getCode())).findFirst().orElse(null);
        }

        /**
         * 服务商是否可解析（代码为空时按默认服务商判断）。站点设置页据此提示“开关不生效”
         *
         * @param code 服务商代码。可为 {@code null}
         * @return 是否可解析
         */
        public boolean resolvesProvider(@Nullable String code) {
            return resolveProvider(code) != null;
        }

        /**
         * 翻译服务商默认路径（未按次指定服务商时）是否可用：机器翻译引擎看凭证是否完整，大模型服务商看是否可解析
         *
         * @return 是否可用
         */
        public boolean resolvesTranslationProvider() {
            String code = translationProvider;
            if (code != null && isMtProvider(code)) {
                return getMt().isConfigured(code);
            }
            return resolvesProvider(code);
        }

        /**
         * 密钥留空的服务商沿用本配置中同代码服务商的已存密钥（配合后台“只写不回显”脱敏）。
         * 仅按代码精确匹配，不回退到默认服务商
         *
         * @param provider 表单提交的服务商配置
         */
        public void fillStoredApiKey(Provider provider) {
            if (StringUtils.isNotBlank(provider.getApiKey()) || StringUtils.isBlank(provider.getCode())) {
                return;
            }
            providers.stream().filter(p -> provider.getCode().equals(p.getCode())).findFirst()
                    .ifPresent(stored -> provider.setApiKey(stored.getApiKey()));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<Provider> getProviders() {
            return providers;
        }

        public void setProviders(List<Provider> providers) {
            this.providers = providers;
        }

        @Nullable
        public String getDefaultProvider() {
            return defaultProvider;
        }

        public void setDefaultProvider(@Nullable String defaultProvider) {
            this.defaultProvider = defaultProvider;
        }

        @Nullable
        public String getWritingProvider() {
            return writingProvider;
        }

        public void setWritingProvider(@Nullable String writingProvider) {
            this.writingProvider = writingProvider;
        }

        @Nullable
        public String getModerationProvider() {
            return moderationProvider;
        }

        public void setModerationProvider(@Nullable String moderationProvider) {
            this.moderationProvider = moderationProvider;
        }

        @Nullable
        public String getTranslationProvider() {
            return translationProvider;
        }

        public void setTranslationProvider(@Nullable String translationProvider) {
            this.translationProvider = translationProvider;
        }

        public Mt getMt() {
            // 旧配置 JSON 无 mt 字段或显式为 null 时兜底，调用方无须判空
            return mt != null ? mt : new Mt();
        }

        public void setMt(Mt mt) {
            this.mt = mt;
        }

        public int getLogRetentionDays() {
            return logRetentionDays;
        }

        public void setLogRetentionDays(int logRetentionDays) {
            this.logRetentionDays = logRetentionDays;
        }

        public long getDailyTokenLimit() {
            return dailyTokenLimit;
        }

        public void setDailyTokenLimit(long dailyTokenLimit) {
            this.dailyTokenLimit = dailyTokenLimit;
        }

        @Schema(name = "Config.Ai.Provider", description = "AI服务商配置")
        public static class Provider implements Serializable {
            private static final long serialVersionUID = 1L;

            /**
             * 服务商内部标识。由后台自动生成、不向运营者展示；列表内唯一，
             * 作为默认/写作/审核服务商的引用键及保存时密钥合并的匹配键
             */
            @NotNull
            @Pattern(regexp = "[a-z0-9-]{1,30}")
            private String code = "";
            /**
             * 服务商名称。运营者可见的标识，列表内唯一；日志按调用时的名称快照记录
             */
            @NotNull
            @Length(max = 50)
            private String name = "";
            /**
             * 接口地址。不含末尾斜杠，如 https://api.deepseek.com/v1
             */
            @NotNull
            @Length(max = 255)
            private String baseUrl = "";
            /**
             * API密钥。可为空（Ollama/vLLM 等内网部署无需密钥）
             */
            @Length(max = 256)
            private String apiKey = "";
            /**
             * 模型
             */
            @NotNull
            @Length(max = 100)
            private String model = "";
            /**
             * 读取超时时间。单位：秒
             */
            @Min(5)
            @Max(600)
            private int timeoutSeconds = 60;
            /**
             * 温度(0-2)。为空使用服务商默认值
             */
            @Nullable
            private Double temperature;
            /**
             * 请求附加参数。JSON 对象文本，原样合并进每次 chat/completions 请求体，协议字段（model、messages 等）随后覆盖，
             * 因此只能补充服务商特有参数。最常见用途是关闭推理模型的深度思考：思考 token 计入 max_tokens，会耗尽元信息、
             * 候选标题、审核等小预算 JSON 调用（2026-09-05 glm-5.3-flash 实测返回空内容）。各家字段不同：
             * 智谱/火山方舟 {"thinking":{"type":"disabled"}}、阿里云百炼 {"enable_thinking":false}、Ollama {"think":false}、
             * vLLM/SGLang {"chat_template_kwargs":{"enable_thinking":false}}，控制台提供预设
             */
            @Length(max = 1000)
            private String extraParams = "";

            public String getCode() {
                return code;
            }

            public void setCode(String code) {
                this.code = code;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String apiKey) {
                this.apiKey = apiKey;
            }

            public String getModel() {
                return model;
            }

            public void setModel(String model) {
                this.model = model;
            }

            public int getTimeoutSeconds() {
                return timeoutSeconds;
            }

            public void setTimeoutSeconds(int timeoutSeconds) {
                this.timeoutSeconds = timeoutSeconds;
            }

            @Nullable
            public Double getTemperature() {
                return temperature;
            }

            public void setTemperature(@Nullable Double temperature) {
                this.temperature = temperature;
            }

            public String getExtraParams() {
                return extraParams;
            }

            public void setExtraParams(String extraParams) {
                this.extraParams = extraParams;
            }

            /**
             * 解析附加参数为 JSON 对象。空白返回 null；不是合法 JSON 或不是对象抛出 IllegalArgumentException，
             * 保存（GlobalSettingsController.updateAi）与调用（AiClient.buildBody）两处据此拒绝
             *
             * @return 附加参数对象，未配置时为 null
             */
            @Nullable
            public ObjectNode extraParamsObject() {
                if (StringUtils.isBlank(extraParams)) {
                    return null;
                }
                try {
                    JsonNode node = MAPPER.readTree(extraParams);
                    if (node instanceof ObjectNode object) {
                        return object;
                    }
                    throw new IllegalArgumentException("extraParams must be a JSON object: " + extraParams);
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("extraParams is not valid JSON: " + extraParams, e);
                }
            }

            /**
             * 规范化接口地址：去除首尾空白与末尾斜杠
             */
            public void normalizeBaseUrl() {
                baseUrl = Strings.CS.removeEnd(StringUtils.trimToEmpty(baseUrl), "/");
            }
        }

        /**
         * 机器翻译引擎凭证（百度翻译/有道翻译/DeepL 固定三家，Sms 式固定厂商而非通用列表）。
         * 随 {@code ai_settings_} 整体 SM4-GCM 加密存储；密钥字段后台“只写不回显”
         */
        @Schema(name = "Config.Ai.Mt", description = "机器翻译引擎配置")
        public static class Mt implements Serializable {
            private static final long serialVersionUID = 1L;

            /**
             * 百度翻译开放平台 APP ID
             */
            @Length(max = 50)
            private String baiduAppId = "";
            /**
             * 百度翻译密钥
             */
            @Length(max = 100)
            private String baiduSecret = "";
            /**
             * 有道智云应用ID
             */
            @Length(max = 50)
            private String youdaoAppKey = "";
            /**
             * 有道智云应用密钥
             */
            @Length(max = 100)
            private String youdaoSecret = "";
            /**
             * DeepL认证密钥。{@code :fx} 结尾自动使用免费版接口地址
             */
            @Length(max = 100)
            private String deeplAuthKey = "";

            /**
             * 引擎凭证是否完整：百度需 APP ID 与密钥，有道需应用ID与应用密钥，DeepL 需认证密钥。
             * 带参方法，不会被 Jackson 当作属性序列化
             *
             * @param code 引擎保留代码（{@code mt:*}）
             * @return 是否已配置
             */
            public boolean isConfigured(String code) {
                return switch (code) {
                    case MT_BAIDU -> StringUtils.isNoneBlank(baiduAppId, baiduSecret);
                    case MT_YOUDAO -> StringUtils.isNoneBlank(youdaoAppKey, youdaoSecret);
                    case MT_DEEPL -> StringUtils.isNotBlank(deeplAuthKey);
                    default -> false;
                };
            }

            /**
             * 密钥留空时沿用已存值（配合后台“只写不回显”脱敏；ID类字段回显，不参与合并）
             *
             * @param stored 库中已存配置
             */
            public void fillStoredSecrets(Mt stored) {
                if (StringUtils.isBlank(baiduSecret)) {
                    baiduSecret = stored.getBaiduSecret();
                }
                if (StringUtils.isBlank(youdaoSecret)) {
                    youdaoSecret = stored.getYoudaoSecret();
                }
                if (StringUtils.isBlank(deeplAuthKey)) {
                    deeplAuthKey = stored.getDeeplAuthKey();
                }
            }

            public String getBaiduAppId() {
                return baiduAppId;
            }

            public void setBaiduAppId(String baiduAppId) {
                this.baiduAppId = baiduAppId;
            }

            public String getBaiduSecret() {
                return baiduSecret;
            }

            public void setBaiduSecret(String baiduSecret) {
                this.baiduSecret = baiduSecret;
            }

            public String getYoudaoAppKey() {
                return youdaoAppKey;
            }

            public void setYoudaoAppKey(String youdaoAppKey) {
                this.youdaoAppKey = youdaoAppKey;
            }

            public String getYoudaoSecret() {
                return youdaoSecret;
            }

            public void setYoudaoSecret(String youdaoSecret) {
                this.youdaoSecret = youdaoSecret;
            }

            public String getDeeplAuthKey() {
                return deeplAuthKey;
            }

            public void setDeeplAuthKey(String deeplAuthKey) {
                this.deeplAuthKey = deeplAuthKey;
            }
        }
    }

    /**
     * 第三方登录（OAuth）设置（features.yaml oauthLogin，商业 T2 起）。各服务商的应用凭据随 {@code oauth_settings_}
     * 整体 SM4-GCM 加密存储，应用密钥后台“只写不回显”。回调地址不入库：登录时按默认站点地址推导
     * （{@code {默认站点地址}/oauth/code/{服务商代码}}），站点域名变更后无需另行维护。
     */
    @Schema(name = "Config.Oauth", description = "第三方登录配置")
    public static class Oauth implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 支持的服务商代码，按设置页的展示顺序。代码即登录地址 {@code /oauth/login/{代码}} 中的路径段
         */
        public static final List<String> PROVIDERS = List.of(
                WeixinOauthClient.PROVIDER_WEIXIN, QqOauthClient.PROVIDER_QQ, WeiboOauthClient.PROVIDER_WEIBO);

        /**
         * 微信（微信开放平台“网站应用”，扫码登录）
         */
        @Valid
        private Client weixin = new Client();
        /**
         * QQ（QQ 互联）
         */
        @Valid
        private Client qq = new Client();
        /**
         * 微博（微博开放平台）
         */
        @Valid
        private Client weibo = new Client();

        /**
         * 按代码取服务商设置
         *
         * @param provider 服务商代码
         * @return 服务商设置。代码不在 {@link #PROVIDERS} 之列时返回 {@code null}
         */
        @Nullable
        public Client client(String provider) {
            return switch (provider) {
                case WeixinOauthClient.PROVIDER_WEIXIN -> weixin;
                case QqOauthClient.PROVIDER_QQ -> qq;
                case WeiboOauthClient.PROVIDER_WEIBO -> weibo;
                default -> null;
            };
        }

        /**
         * 可用的服务商代码：已启用且应用ID、应用密钥齐全，按 {@link #PROVIDERS} 的顺序
         */
        public List<String> usableProviders() {
            return PROVIDERS.stream().filter(provider -> {
                Client client = client(provider);
                return client != null && client.isUsable();
            }).toList();
        }

        /**
         * 应用密钥留空的服务商沿用本设置中的已存密钥（配合后台“只写不回显”脱敏）
         *
         * @param bean 表单提交的设置
         */
        public void fillStoredSecrets(Oauth bean) {
            for (String provider : PROVIDERS) {
                Client submitted = bean.client(provider);
                Client stored = client(provider);
                if (submitted != null && stored != null && StringUtils.isBlank(submitted.getClientSecret())) {
                    submitted.setClientSecret(stored.getClientSecret());
                }
            }
        }

        public Client getWeixin() {
            return weixin;
        }

        public void setWeixin(@Nullable Client weixin) {
            this.weixin = weixin != null ? weixin : new Client();
        }

        public Client getQq() {
            return qq;
        }

        public void setQq(@Nullable Client qq) {
            this.qq = qq != null ? qq : new Client();
        }

        public Client getWeibo() {
            return weibo;
        }

        public void setWeibo(@Nullable Client weibo) {
            this.weibo = weibo != null ? weibo : new Client();
        }

        @Schema(name = "Config.Oauth.Client", description = "第三方登录服务商设置")
        public static class Client implements Serializable {
            @Serial
            private static final long serialVersionUID = 1L;

            /**
             * 是否启用
             */
            private boolean enabled = false;
            /**
             * 应用ID（微信 AppID、QQ APP ID、微博 App Key）。标识类字段，后台可见
             */
            @Length(max = 100)
            private String clientId = "";
            /**
             * 应用密钥（微信 AppSecret、QQ APP Key、微博 App Secret）。后台“只写不回显”
             */
            @Length(max = 200)
            private String clientSecret = "";

            /**
             * 是否可用：已启用且应用ID、应用密钥齐全。登录入口只对可用的服务商显示
             */
            @JsonIgnore
            public boolean isUsable() {
                return enabled && StringUtils.isNoneBlank(clientId, clientSecret);
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getClientId() {
                return clientId;
            }

            public void setClientId(@Nullable String clientId) {
                this.clientId = StringUtils.trimToEmpty(clientId);
            }

            public String getClientSecret() {
                return clientSecret;
            }

            public void setClientSecret(@Nullable String clientSecret) {
                this.clientSecret = StringUtils.trimToEmpty(clientSecret);
            }
        }
    }

    @Schema(name = "Config.Email", description = "邮件配置")
    public static class Email implements Serializable {
        private static final long serialVersionUID = 1L;

        @Nullable
        @Length(max = 50)
        private String host;
        @Nullable
        private Integer port;
        private boolean ssl = true;
        @Nullable
        private Integer timeout;
        @Length(max = 50)
        private String from = "username@email.com";
        @Length(max = 50)
        private String username = "username";
        @Length(max = 50)
        private String password = "password";
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
        private int codeExpires = 20;
        @NotNull
        @Length(max = 100)
        private String subject = "邮件验证码";
        @NotNull
        @Length(max = 400)
        private String text = "验证码：${code}。如非本人操作，请忽略本邮件。";
        @Length(max = 50)
        private String testTo = "";

        public void sendMail(String[] to, String subject, String text) {
            if (host == null) {
                throw new IllegalStateException("Email SMTP host is not set");
            }
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(host);
            mailSender.setUsername(username);
            mailSender.setPassword(password);
            mailSender.setDefaultEncoding("UTF-8");
            mailSender.setPort(port != null ? port : JavaMailSenderImpl.DEFAULT_PORT);

            Properties prop = new Properties();
            prop.setProperty("mail.smtp.auth", "true");
            if (ssl) {
                prop.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            }
            if (timeout != null) {
                prop.setProperty("mail.smtp.timeout", timeout.toString());
            }
            mailSender.setJavaMailProperties(prop);
            MimeMessage msg = mailSender.createMimeMessage();
            try {
                MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(text, false);
            } catch (MessagingException e) {
                throw new IllegalStateException(e);
            }
            mailSender.send(msg);
        }

        @Nullable
        public String getHost() {
            return host;
        }

        public void setHost(@Nullable String host) {
            this.host = host;
        }

        @Nullable
        public Integer getPort() {
            return port;
        }

        public void setPort(@Nullable Integer port) {
            this.port = port;
        }

        public boolean isSsl() {
            return ssl;
        }

        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }

        @Nullable
        public Integer getTimeout() {
            return timeout;
        }

        public void setTimeout(@Nullable Integer timeout) {
            this.timeout = timeout;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
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

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getTestTo() {
            return testTo;
        }

        public void setTestTo(String testTo) {
            this.testTo = testTo;
        }
    }

    @Schema(name = "Config.Storage", description = "存储点配置")
    public static class Storage implements Serializable {
        private static final long serialVersionUID = 1L;
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

        private String hostname = "";
        @Nullable
        private Integer port;
        private String username = "";
        private String password = "";
        private String encoding = "";
        /**
         * 被动模式
         */
        private boolean passive = true;
        /**
         * 加密方式。0:不加密, 1: TLS隐式加密, 2: TLS显式加密
         */
        private int encryption;

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
            } else if (getType() == TYPE_FTP) {
                FtpClientProperties properties = new FtpClientProperties(hostname, port, username, password,
                        encoding, passive, encryption);
                return new FtpFileHandler(properties, path, url);
            }
            return new LocalFileHandler(pathResolver, path, url);
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

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        @Nullable
        public Integer getPort() {
            return port;
        }

        public void setPort(@Nullable Integer port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getEncoding() {
            return encoding;
        }

        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }

        public boolean isPassive() {
            return passive;
        }

        public void setPassive(boolean passive) {
            this.passive = passive;
        }

        public int getEncryption() {
            return encryption;
        }

        public void setEncryption(int encryption) {
            this.encryption = encryption;
        }

        /**
         * 存储类型：本地服务器
         */
        public static final int TYPE_LOCAL = 0;
        /**
         * 存储类型：FTP
         */
        public static final int TYPE_FTP = 1;
        /**
         * 存储类型：MinIO
         */
        public static final int TYPE_MINIO = 10;
        /**
         * 存储类型：阿里云
         */
        public static final int TYPE_ALIYUN = 11;
        /**
         * 存储类型：腾讯云
         */
        public static final int TYPE_QCLOUD = 12;
        /**
         * 存储类型：七牛云
         */
        public static final int TYPE_QINIU = 13;
    }

    /**
     * 控制台默认外观（docs/spec/console-shell/plan.md D7）：新用户及未在“外观设置”自选外观的用户所见的侧栏风格与主题色，
     * 登录页也按此着色。用户自选值存于 {@link UserExt.Preferences}，其外观两项为空即跟随此处。
     * 随 /api/env/config（匿名）与 backend-state 下发；预设色板由控制台维护，此处只校验格式。
     */
    @Schema(name = "Config.Console", description = "控制台默认外观")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Console implements Serializable {
        private static final long serialVersionUID = 1L;

        public static final String SIDEBAR_LIGHT = "light";
        public static final String SIDEBAR_DARK = "dark";
        public static final String DEFAULT_THEME_COLOR = "#409eff";

        /**
         * 默认侧栏风格：light（浅色）| dark（深色）。代码默认深色（docs/spec/console-shell/plan.md D1）
         */
        @NotNull
        @Pattern(regexp = "^(light|dark)$")
        @Schema(description = "默认侧栏风格：light | dark")
        private String sidebarTheme = SIDEBAR_DARK;
        /**
         * 默认主题色，#rrggbb
         */
        @NotNull
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$")
        @Schema(description = "默认主题色 #rrggbb")
        private String themeColor = DEFAULT_THEME_COLOR;

        public String getSidebarTheme() {
            return sidebarTheme;
        }

        public void setSidebarTheme(String sidebarTheme) {
            this.sidebarTheme = sidebarTheme;
        }

        public String getThemeColor() {
            return themeColor;
        }

        public void setThemeColor(String themeColor) {
            this.themeColor = themeColor;
        }
    }
}