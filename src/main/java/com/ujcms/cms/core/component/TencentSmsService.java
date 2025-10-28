package com.ujcms.cms.core.component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujcms.commons.sms.SmsUtils;

import jakarta.xml.bind.DatatypeConverter;

@Service
public class TencentSmsService {
    public static final String ACTION = "SendSms";
    public static final String VERSION = "2021-01-11";
    public static final String ALGORITHM = "TC3-HMAC-SHA256";
    public static final String HOST = "sms.tencentcloudapi.com";
    public static final String SERVICE = "sms";
    public static final String CONTENT_TYPE = "application/json; charset=utf-8";

    private static final String SUCCESS_CODE = "Ok";
    private static final String RESPONSE_KEY = "Response";
    private static final String ERROR_KEY = "Error";
    private static final String CODE_KEY = "Code";
    private static final String MESSAGE_KEY = "Message";
    private static final String SEND_STATUS_SET_KEY = "SendStatusSet";
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TencentSmsService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl("https://" + HOST).build();
        this.objectMapper = objectMapper;
    }

    /**
     * 发送短信
     * 
     * @param config
     * @param mobile
     * @param code
     * @return 返回 {@code null} 代表成功，否则返回错误信息
     * 
     * @see https://cloud.tencent.com/document/product/382/55981
     */
    @Nullable
    public String sendSms(SmsConfig config, String mobile, String code) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String date = getUtcDate(timestamp);
            String payload = buildPayload(config, mobile, code);

            // 构建签名和授权信息
            String authorization = buildAuthorization(config, date, timestamp, payload);

            // 发送请求
            JsonNode response = sendRequest(config, timestamp, authorization, payload);

            // 处理响应
            return processResponse(response);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return "Signature error: " + e.getMessage();
        } catch (Exception e) {
            return "SMS send error: " + e.getMessage();
        }
    }

    /**
     * 获取 UTC 日期字符串
     */
    private String getUtcDate(String timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(Long.parseLong(timestamp) * 1000));
    }

    /**
     * 构建授权信息
     */
    private String buildAuthorization(SmsConfig config, String date, String timestamp, String payload)
            throws NoSuchAlgorithmException, InvalidKeyException {
        // ************* 步骤 1：拼接规范请求串 *************
        String canonicalHeaders = "content-type:" + CONTENT_TYPE + "\n" + "host:" + HOST + "\n" + "x-tc-action:"
                + ACTION.toLowerCase() + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String hashedRequestPayload = SmsUtils.sha256Hex(payload);
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + hashedRequestPayload;

        // ************* 步骤 2：拼接待签名字符串 *************
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String hashedCanonicalRequest = SmsUtils.sha256Hex(canonicalRequest);
        String stringToSign = ALGORITHM + "\n" + timestamp + "\n" + credentialScope + "\n" + hashedCanonicalRequest;

        // ************* 步骤 3：计算签名 *************
        String signature = calculateSignature(config.secretKey, date, stringToSign);

        // ************* 步骤 4：拼接 Authorization *************
        return ALGORITHM + " Credential=" + config.secretId + "/" + credentialScope + ", " + "SignedHeaders="
                + signedHeaders + ", Signature=" + signature;
    }

    /**
     * 计算签名
     */
    private String calculateSignature(String secretKey, String date, String stringToSign)
            throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] secretDate = SmsUtils.hmac256(("TC3" + secretKey).getBytes(UTF8), date);
        byte[] secretService = SmsUtils.hmac256(secretDate, SERVICE);
        byte[] secretSigning = SmsUtils.hmac256(secretService, "tc3_request");
        return DatatypeConverter.printHexBinary(SmsUtils.hmac256(secretSigning, stringToSign)).toLowerCase();
    }

    /**
     * 发送 HTTP 请求
     */
    @Nullable
    private JsonNode sendRequest(SmsConfig config, String timestamp, String authorization, String payload) {
        return restClient.post().uri("/").header("Authorization", authorization).header("Content-Type", CONTENT_TYPE)
                .header("Host", HOST).header("X-TC-Action", ACTION).header("X-TC-Timestamp", timestamp)
                .header("X-TC-Version", VERSION).header("X-TC-Region", config.region).body(payload).retrieve()
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
        // 确保手机号格式正确（需要包含国际区号，例如：+86）
        String phoneNumber = mobile.startsWith("+") ? mobile : "+86" + mobile;

        // 使用 Jackson ObjectMapper 构建 JSON
        Map<String, Object> payload = new HashMap<>();
        payload.put("SmsSdkAppId", config.sdkAppId);
        payload.put("SignName", config.signName);
        payload.put("TemplateId", config.templateId);
        payload.put("TemplateParamSet", new String[] { code });
        payload.put("PhoneNumberSet", new String[] { phoneNumber });

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
