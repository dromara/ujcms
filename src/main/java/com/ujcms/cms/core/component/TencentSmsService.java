package com.ujcms.cms.core.component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujcms.commons.sms.SmsUtils;

import jakarta.xml.bind.DatatypeConverter;

/**
 * 腾讯云短信服务
 * <p>
 * API文档: https://cloud.tencent.com/document/product/382/55981
 * 
 * @author PONY
 */
@Service
public class TencentSmsService {
    public static final String ACTION = "SendSms";
    public static final String VERSION = "2021-01-11";
    public static final String ALGORITHM = "TC3-HMAC-SHA256";
    public static final String HOST = "sms.tencentcloudapi.com";
    public static final String SERVICE = "sms";
    public static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String HTTP_METHOD = "POST";
    private static final String PATH = "/";
    private static final String TC3_PREFIX = "TC3";
    private static final String TC3_REQUEST = "tc3_request";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    // 响应字段常量
    private static final String SUCCESS_CODE = "Ok";
    private static final String RESPONSE_KEY = "Response";
    private static final String ERROR_KEY = "Error";
    private static final String CODE_KEY = "Code";
    private static final String MESSAGE_KEY = "Message";
    private static final String SEND_STATUS_SET_KEY = "SendStatusSet";

    // 请求头名称常量
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_HOST = "Host";
    private static final String HEADER_X_TC_ACTION = "X-TC-Action";
    private static final String HEADER_X_TC_TIMESTAMP = "X-TC-Timestamp";
    private static final String HEADER_X_TC_VERSION = "X-TC-Version";
    private static final String HEADER_X_TC_REGION = "X-TC-Region";

    // 请求体字段常量
    private static final String PAYLOAD_SMS_SDK_APP_ID = "SmsSdkAppId";
    private static final String PAYLOAD_SIGN_NAME = "SignName";
    private static final String PAYLOAD_TEMPLATE_ID = "TemplateId";
    private static final String PAYLOAD_TEMPLATE_PARAM_SET = "TemplateParamSet";
    private static final String PAYLOAD_PHONE_NUMBER_SET = "PhoneNumberSet";

    // 规范请求头常量
    private static final String HEADER_X_TC_ACTION_LOWER = "x-tc-action";
    private static final String HEADER_CONTENT_TYPE_LOWER = "content-type";
    private static final String HEADER_HOST_LOWER = "host";
    private static final String SIGNED_HEADERS = "content-type;host;x-tc-action";

    // 默认国际区号
    private static final String DEFAULT_COUNTRY_CODE = "+86";

    private static final Charset UTF8 = StandardCharsets.UTF_8;

    // 线程安全的日期格式化器（DateTimeFormatter是线程安全的，无需ThreadLocal）
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT)
            .withZone(ZoneOffset.UTC);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TencentSmsService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl("https://" + HOST).build();
        this.objectMapper = objectMapper;
    }

    /**
     * 发送短信
     * 
     * @param config 短信配置
     * @param mobile 手机号
     * @param code   验证码
     * @return 返回 {@code null} 代表成功，否则返回错误信息
     */
    @Nullable
    public String sendSms(SmsConfig config, String mobile, String code) {
        try {
            String timestamp = getCurrentTimestamp();
            String date = getUtcDate(timestamp);
            String payload = buildPayload(config, mobile, code);
            String authorization = buildAuthorization(config, date, timestamp, payload);
            JsonNode response = sendRequest(config, timestamp, authorization, payload);
            return processResponse(response);
        } catch (Exception e) {
            return "SMS send error: " + e.getMessage();
        }
    }

    /**
     * 获取当前时间戳（秒）
     */
    private String getCurrentTimestamp() {
        return String.valueOf(System.currentTimeMillis() / 1000);
    }

    /**
     * 获取 UTC 日期字符串
     */
    private String getUtcDate(String timestamp) {
        long timestampMillis = Long.parseLong(timestamp) * 1000;
        return DATE_FORMATTER.format(Instant.ofEpochMilli(timestampMillis));
    }

    /**
     * 构建授权信息
     */
    private String buildAuthorization(SmsConfig config, String date, String timestamp, String payload) {
        String canonicalRequest = buildCanonicalRequest(payload);
        String credentialScope = buildCredentialScope(date);
        String stringToSign = buildStringToSign(timestamp, credentialScope, canonicalRequest);
        String signature = calculateSignature(config.secretKey, date, stringToSign);
        return buildAuthorizationHeader(config.secretId, credentialScope, signature);
    }

    /**
     * 构建规范请求串
     */
    private String buildCanonicalRequest(String payload) {
        String canonicalHeaders = buildCanonicalHeaders();
        String hashedRequestPayload = SmsUtils.sha256Hex(payload);
        return String.join("\n", HTTP_METHOD, PATH, "", canonicalHeaders, SIGNED_HEADERS, hashedRequestPayload);
    }

    /**
     * 构建规范请求头
     */
    private String buildCanonicalHeaders() {
        return HEADER_CONTENT_TYPE_LOWER + ":" + CONTENT_TYPE + "\n" + HEADER_HOST_LOWER + ":" + HOST + "\n"
                + HEADER_X_TC_ACTION_LOWER + ":" + ACTION.toLowerCase() + "\n";
    }

    /**
     * 构建凭证作用域
     */
    private String buildCredentialScope(String date) {
        return date + "/" + SERVICE + "/" + TC3_REQUEST;
    }

    /**
     * 构建待签名字符串
     */
    private String buildStringToSign(String timestamp, String credentialScope, String canonicalRequest) {
        String hashedCanonicalRequest = SmsUtils.sha256Hex(canonicalRequest);
        return String.join("\n", ALGORITHM, timestamp, credentialScope, hashedCanonicalRequest);
    }

    /**
     * 构建Authorization请求头
     */
    private String buildAuthorizationHeader(String secretId, String credentialScope, String signature) {
        return ALGORITHM + " Credential=" + secretId + "/" + credentialScope + ", SignedHeaders=" + SIGNED_HEADERS
                + ", Signature=" + signature;
    }

    /**
     * 计算签名
     */
    private String calculateSignature(String secretKey, String date, String stringToSign) {
        byte[] secretDate = SmsUtils.hmac256((TC3_PREFIX + secretKey).getBytes(UTF8), date);
        byte[] secretService = SmsUtils.hmac256(secretDate, SERVICE);
        byte[] secretSigning = SmsUtils.hmac256(secretService, TC3_REQUEST);
        byte[] signatureBytes = SmsUtils.hmac256(secretSigning, stringToSign);
        return DatatypeConverter.printHexBinary(signatureBytes).toLowerCase();
    }

    /**
     * 发送 HTTP 请求
     */
    @Nullable
    private JsonNode sendRequest(SmsConfig config, String timestamp, String authorization, String payload) {
        return restClient.post()
                .uri(PATH)
                .header(HEADER_AUTHORIZATION, authorization)
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE)
                .header(HEADER_HOST, HOST)
                .header(HEADER_X_TC_ACTION, ACTION)
                .header(HEADER_X_TC_TIMESTAMP, timestamp)
                .header(HEADER_X_TC_VERSION, VERSION)
                .header(HEADER_X_TC_REGION, config.region)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
    }

    /**
     * 处理响应结果
     */
    @Nullable
    private String processResponse(@Nullable JsonNode response) {
        if (response == null || !response.has(RESPONSE_KEY)) {
            return "Invalid response from SMS service";
        }

        JsonNode responseNode = response.get(RESPONSE_KEY);

        // 检查是否有错误
        String error = checkForError(responseNode);
        if (error != null) {
            return error;
        }

        // 检查发送状态
        return checkSendStatus(responseNode);
    }

    /**
     * 检查响应中的错误
     */
    @Nullable
    private String checkForError(JsonNode responseNode) {
        if (!responseNode.has(ERROR_KEY)) {
            return null;
        }

        JsonNode error = responseNode.get(ERROR_KEY);
        String errorCode = error.has(CODE_KEY) ? error.get(CODE_KEY).asText() : "Unknown";
        String errorMessage = error.has(MESSAGE_KEY) ? error.get(MESSAGE_KEY).asText() : "Unknown error";
        return "code: " + errorCode + ", message: " + errorMessage;
    }

    /**
     * 检查发送状态
     */
    @Nullable
    private String checkSendStatus(JsonNode responseNode) {
        if (!responseNode.has(SEND_STATUS_SET_KEY)) {
            return null;
        }

        JsonNode sendStatusSet = responseNode.get(SEND_STATUS_SET_KEY);
        if (!sendStatusSet.isArray() || sendStatusSet.isEmpty()) {
            return null;
        }

        JsonNode sendStatus = sendStatusSet.get(0);
        String statusCode = sendStatus.has(CODE_KEY) ? sendStatus.get(CODE_KEY).asText() : "";

        // 成功状态码为 "Ok"
        if (SUCCESS_CODE.equalsIgnoreCase(statusCode)) {
            return null;
        }

        String message = sendStatus.has(MESSAGE_KEY) ? sendStatus.get(MESSAGE_KEY).asText() : "Unknown error";
        return "code: " + statusCode + ", message: " + message;
    }

    /**
     * 构建短信发送请求体
     */
    private String buildPayload(SmsConfig config, String mobile, String code) {
        String phoneNumber = normalizePhoneNumber(mobile);
        Map<String, Object> payload = buildPayloadMap(config, phoneNumber, code);
        return serializePayload(payload);
    }

    /**
     * 规范化手机号（确保包含国际区号）
     */
    private String normalizePhoneNumber(String mobile) {
        return mobile.startsWith("+") ? mobile : DEFAULT_COUNTRY_CODE + mobile;
    }

    /**
     * 构建请求体Map
     */
    private Map<String, Object> buildPayloadMap(SmsConfig config, String phoneNumber, String code) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(PAYLOAD_SMS_SDK_APP_ID, config.sdkAppId);
        payload.put(PAYLOAD_SIGN_NAME, config.signName);
        payload.put(PAYLOAD_TEMPLATE_ID, config.templateId);
        payload.put(PAYLOAD_TEMPLATE_PARAM_SET, new String[] { code });
        payload.put(PAYLOAD_PHONE_NUMBER_SET, new String[] { phoneNumber });
        return payload;
    }

    /**
     * 序列化请求体为JSON字符串
     */
    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build SMS payload", e);
        }
    }
    
    public static class SmsConfig {
        public final String secretId;
        public final String secretKey;
        public final String sdkAppId;
        public final String region;
        public final String signName;
        public final String templateId;

        public SmsConfig(String secretId, String secretKey, String sdkAppId, String region, 
                String signName, String templateId) {
            this.secretId = secretId;
            this.secretKey = secretKey;
            this.sdkAppId = sdkAppId;
            this.region = region;
            this.signName = signName;
            this.templateId = templateId;
        }
    }    
}
