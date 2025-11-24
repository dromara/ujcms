package com.ujcms.cms.core.component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.ujcms.commons.sms.SmsUtils;

import jakarta.xml.bind.DatatypeConverter;

/**
 * 阿里云短信服务
 * <p>
 * API文档: https://api.aliyun.com/document/Dysmsapi/2017-05-25/SendSms 签名机制:
 * https://help.aliyun.com/zh/sdk/product-overview/v3-request-structure-and-signature
 * 
 * @author PONY
 */
@Service
public class AliyunSmsService {
    public static final String HOST = "dysmsapi.aliyuncs.com";
    private static final String ALGORITHM = "ACS3-HMAC-SHA256";
    private static final String SMS_ACTION = "SendSms";
    private static final String SMS_VERSION = "2017-05-25";
    private static final String SUCCESS_CODE = "OK";
    private static final String HTTP_METHOD = "POST";
    private static final String PATH = "/";
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String EMPTY_BODY = "";

    // 请求头名称常量
    private static final String HEADER_HOST = "host";
    private static final String HEADER_ACS_ACTION = "x-acs-action";
    private static final String HEADER_ACS_VERSION = "x-acs-version";
    private static final String HEADER_ACS_DATE = "x-acs-date";
    private static final String HEADER_ACS_NONCE = "x-acs-signature-nonce";
    private static final String HEADER_ACS_CONTENT_SHA256 = "x-acs-content-sha256";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "content-type";

    // 查询参数名称常量
    private static final String PARAM_PHONE_NUMBERS = "PhoneNumbers";
    private static final String PARAM_SIGN_NAME = "SignName";
    private static final String PARAM_TEMPLATE_CODE = "TemplateCode";
    private static final String PARAM_TEMPLATE_PARAM = "TemplateParam";

    // 响应字段名称常量
    private static final String RESPONSE_CODE = "Code";
    private static final String RESPONSE_MESSAGE = "Message";

    // 线程安全的日期格式化器（DateTimeFormatter是线程安全的，无需ThreadLocal）
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT)
            .withZone(ZoneOffset.UTC);

    private final RestClient restClient;

    public AliyunSmsService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://" + HOST).build();
    }


    /**
     * 发送短信
     * 
     * @param accessKeyId     阿里云 AccessKey ID
     * @param accessKeySecret 阿里云 AccessKey Secret
     * @param signName        短信签名
     * @param templateCode    短信模板CODE
     * @param codeName        模板参数名称
     * @param code            验证码
     * @param mobile          手机号
     * @return 返回 {@code null} 代表成功，否则返回错误信息
     */
    @Nullable
    public String sendSms(String accessKeyId, String accessKeySecret, String signName, String templateCode,
            String codeName, String code, String mobile) {
        try {
            // 构建查询参数
            Map<String, Object> processedQueryParams = buildQueryParams(signName, templateCode, codeName, code, mobile);

            // 构建请求头
            Map<String, String> headers = buildHeaders();

            // 计算签名并添加到请求头
            SignatureResult signatureResult = calculateSignature(accessKeySecret, processedQueryParams, headers);
            addAuthorizationHeader(headers, accessKeyId, signatureResult.signature, signatureResult.signedHeaders);

            // 发送请求并解析响应
            JsonNode response = sendRequest(processedQueryParams, headers);
            return parseResponse(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send SMS", e);
        }
    }

    /**
     * 构建查询参数
     */
    private Map<String, Object> buildQueryParams(String signName, String templateCode, String codeName, String code,
            String mobile) {
        String templateParam = buildTemplateParamJson(codeName, code);

        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put(PARAM_PHONE_NUMBERS, mobile);
        queryParams.put(PARAM_SIGN_NAME, signName);
        queryParams.put(PARAM_TEMPLATE_CODE, templateCode);
        queryParams.put(PARAM_TEMPLATE_PARAM, templateParam);

        Map<String, Object> processedQueryParams = new TreeMap<>();
        processObject(processedQueryParams, "", queryParams);
        return processedQueryParams;
    }

    /**
     * 构建模板参数JSON
     */
    private String buildTemplateParamJson(String codeName, String code) {
        return "{\"" + codeName + "\":\"" + code + "\"}";
    }

    /**
     * 构建请求头
     */
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new TreeMap<>();
        headers.put(HEADER_HOST, HOST);
        headers.put(HEADER_ACS_ACTION, SMS_ACTION);
        headers.put(HEADER_ACS_VERSION, SMS_VERSION);
        headers.put(HEADER_ACS_DATE, formatCurrentDate());
        headers.put(HEADER_ACS_NONCE, UUID.randomUUID().toString());

        String hashedRequestPayload = SmsUtils.sha256Hex(EMPTY_BODY);
        headers.put(HEADER_ACS_CONTENT_SHA256, hashedRequestPayload);
        return headers;
    }

    /**
     * 格式化当前日期为UTC时间
     */
    private String formatCurrentDate() {
        return DATE_FORMATTER.format(Instant.now());
    }

    /**
     * 计算签名
     */
    private SignatureResult calculateSignature(String accessKeySecret, Map<String, Object> processedQueryParams,
            Map<String, String> headers) {
        CanonicalHeadersResult canonicalHeadersResult = buildCanonicalHeaders(headers);
        String canonicalQueryString = buildCanonicalQueryString(processedQueryParams);
        String hashedRequestPayload = SmsUtils.sha256Hex(EMPTY_BODY);

        String canonicalRequest = buildCanonicalRequest(canonicalQueryString, canonicalHeadersResult,
                hashedRequestPayload);
        String stringToSign = buildStringToSign(canonicalRequest);

        byte[] signatureBytes = SmsUtils.hmac256(accessKeySecret.getBytes(StandardCharsets.UTF_8), stringToSign);
        String signature = DatatypeConverter.printHexBinary(signatureBytes).toLowerCase();
        return new SignatureResult(signature, canonicalHeadersResult.signedHeaders);
    }

    /**
     * 签名结果
     */
    private static class SignatureResult {
        final String signature;
        final String signedHeaders;

        SignatureResult(String signature, String signedHeaders) {
            this.signature = signature;
            this.signedHeaders = signedHeaders;
        }
    }

    /**
     * 构建规范请求
     */
    private String buildCanonicalRequest(String canonicalQueryString, CanonicalHeadersResult canonicalHeadersResult,
            String hashedRequestPayload) {
        return String.join("\n", HTTP_METHOD, PATH, canonicalQueryString, canonicalHeadersResult.canonicalHeaders,
                canonicalHeadersResult.signedHeaders, hashedRequestPayload);
    }

    /**
     * 构建待签名字符串
     */
    private String buildStringToSign(String canonicalRequest) {
        String hashedCanonicalRequest = SmsUtils.sha256Hex(canonicalRequest);
        return ALGORITHM + "\n" + hashedCanonicalRequest;
    }

    /**
     * 添加Authorization请求头
     */
    private void addAuthorizationHeader(Map<String, String> headers, String accessKeyId, String signature,
            String signedHeaders) {
        String authorization = String.format("%s Credential=%s,SignedHeaders=%s,Signature=%s", ALGORITHM, accessKeyId,
                signedHeaders, signature);
        headers.put(HEADER_AUTHORIZATION, authorization);
    }

    /**
     * 发送HTTP请求
     */
    @Nullable
    private JsonNode sendRequest(Map<String, Object> processedQueryParams, Map<String, String> headers) {
        URI uri = buildUri(processedQueryParams);
        RestClient.RequestBodySpec requestSpec = restClient.post().uri(uri);

        // 添加请求头
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestSpec = requestSpec.header(entry.getKey(), entry.getValue());
        }

        return requestSpec.retrieve().body(JsonNode.class);
    }

    /**
     * 构建URI（包含查询参数）
     */
    private URI buildUri(Map<String, Object> processedQueryParams) {
        StringBuilder uriBuilder = new StringBuilder(PATH);
        if (!processedQueryParams.isEmpty()) {
            uriBuilder.append("?");
            uriBuilder.append(processedQueryParams.entrySet().stream()
                    .map(entry -> percentCode(entry.getKey()) + "=" + percentCode(String.valueOf(entry.getValue())))
                    .collect(Collectors.joining("&")));
        }
        return URI.create(uriBuilder.toString());
    }

    /**
     * 处理复杂对象参数
     */
    private void processObject(Map<String, Object> map, String key, @Nullable Object value) {
        if (value == null) {
            return;
        }

        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); ++i) {
                processObject(map, key + "." + (i + 1), list.get(i));
            }
        } else if (value instanceof Map<?, ?> subMap) {
            for (Map.Entry<?, ?> entry : subMap.entrySet()) {
                processObject(map, key + "." + entry.getKey().toString(), entry.getValue());
            }
        } else {
            String finalKey = key.startsWith(".") ? key.substring(1) : key;

            if (value instanceof byte[] byteArray) {
                map.put(finalKey, new String(byteArray, StandardCharsets.UTF_8));
            } else {
                map.put(finalKey, String.valueOf(value));
            }
        }
    }

    /**
     * 构建规范查询字符串
     */
    private String buildCanonicalQueryString(Map<String, Object> queryParams) {
        return queryParams.entrySet().stream()
                .map(entry -> percentCode(entry.getKey()) + "=" + percentCode(String.valueOf(entry.getValue())))
                .collect(Collectors.joining("&"));
    }

    /**
     * 构建规范头部信息
     */
    private CanonicalHeadersResult buildCanonicalHeaders(Map<String, String> headers) {
        List<Map.Entry<String, String>> signedHeaders = headers.entrySet().stream()
                .filter(this::isSignedHeader)
                .sorted(Map.Entry.comparingByKey())
                .toList();

        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeadersString = new StringBuilder();

        for (Map.Entry<String, String> entry : signedHeaders) {
            String lowerKey = entry.getKey().toLowerCase();
            String value = entry.getValue().trim();
            canonicalHeaders.append(lowerKey).append(":").append(value).append("\n");
            signedHeadersString.append(lowerKey).append(";");
        }

        // 移除最后的分号
        if (signedHeadersString.length() > 0) {
            signedHeadersString.setLength(signedHeadersString.length() - 1);
        }

        return new CanonicalHeadersResult(canonicalHeaders.toString(), signedHeadersString.toString());
    }

    /**
     * 判断是否为需要签名的请求头
     */
    private boolean isSignedHeader(Map.Entry<String, String> entry) {
        String key = entry.getKey().toLowerCase();
        return key.startsWith("x-acs-") || HEADER_HOST.equals(key) || HEADER_CONTENT_TYPE.equals(key);
    }

    private static class CanonicalHeadersResult {
        final String canonicalHeaders;
        final String signedHeaders;

        CanonicalHeadersResult(String canonicalHeaders, String signedHeaders) {
            this.canonicalHeaders = canonicalHeaders;
            this.signedHeaders = signedHeaders;
        }
    }


    /**
     * URL编码
     */
    private String percentCode(@Nullable String str) {
        if (str == null) {
            return "";
        }
        try {
            return URLEncoder.encode(str, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E",
                    "~");
        } catch (Exception e) {
            throw new IllegalStateException("UTF-8 encoding not supported", e);
        }
    }

    /**
     * 解析响应结果
     */
    @Nullable
    private String parseResponse(@Nullable JsonNode response) {
        if (response == null) {
            return "Invalid response from SMS service";
        }

        String code = response.has(RESPONSE_CODE) ? response.get(RESPONSE_CODE).asText() : null;
        String message = response.has(RESPONSE_MESSAGE) ? response.get(RESPONSE_MESSAGE).asText() : null;

        if (SUCCESS_CODE.equals(code)) {
            return null; // 成功
        }

        return "code: " + (code != null ? code : "Unknown") + ", message: "
                + (message != null ? message : "Unknown error");
    }
}