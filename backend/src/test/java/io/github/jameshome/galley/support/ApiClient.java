package io.github.jameshome.galley.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 集成测试用的 HTTP 客户端。
 *
 * <p>用 JDK 自带的 {@link HttpClient} 而不是 MockMvc：集成测试要覆盖过滤器链、会话、CSRF 与真实序列化， 这些都只有在真正走一遍 HTTP 时才成立。会话
 * Cookie 由 {@link CookieManager} 自动维护。
 */
public class ApiClient {

    private final HttpClient http;
    private final CookieManager cookies = new CookieManager();
    private final String base;
    private final ObjectMapper json;

    public ApiClient(int port, ObjectMapper json) {
        this.base = "http://127.0.0.1:" + port;
        // 读树时把小数解析成 BigDecimal：默认会转 double，10.000 变成 10.0，断言分不清精度
        this.json =
                json.rebuild().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    public ApiClient login(String username, String password) {
        get("/api/v1/auth/csrf");
        Response response =
                post("/api/v1/auth/login", Map.of("username", username, "password", password));
        if (response.status() != 200) {
            throw new IllegalStateException(
                    "登录失败 " + username + "：" + response.status() + " " + response.body());
        }
        return this;
    }

    public Response get(String path) {
        return send("GET", path, null, null);
    }

    public Response post(String path, Object body) {
        return send("POST", path, body, null);
    }

    public Response patch(String path, Object body) {
        return send("PATCH", path, body, null);
    }

    public Response delete(String path) {
        return send("DELETE", path, null, null);
    }

    public Response upload(String path, String filename, byte[] content, String contentType) {
        String boundary = "----galley" + System.nanoTime();
        byte[] body = multipart(boundary, filename, content, contentType);
        return send("POST", path, body, "multipart/form-data; boundary=" + boundary);
    }

    private Response send(String method, String path, Object body, String contentType) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path));
            if (!"GET".equals(method)) {
                builder.header("X-XSRF-TOKEN", csrfToken());
            }
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else if (body instanceof byte[] bytes) {
                builder.header("Content-Type", contentType);
                builder.method(method, HttpRequest.BodyPublishers.ofByteArray(bytes));
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(
                        method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            }
            HttpResponse<String> response =
                    http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(), json);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求被中断：" + method + " " + path, e);
        } catch (IOException e) {
            throw new IllegalStateException("请求失败：" + method + " " + path, e);
        }
    }

    /** 每次写请求前都取一次：登录会轮换会话，令牌可能随之更新。 */
    private String csrfToken() {
        String token = cookie("XSRF-TOKEN");
        if (token != null) {
            return token;
        }
        get("/api/v1/auth/csrf");
        token = cookie("XSRF-TOKEN");
        if (token == null) {
            throw new IllegalStateException("服务端未下发 XSRF-TOKEN");
        }
        return token;
    }

    private String cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> name.equals(c.getName()))
                .map(java.net.HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private static byte[] multipart(String boundary, String filename, byte[] content, String type) {
        String head =
                "--"
                        + boundary
                        + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                        + filename
                        + "\"\r\nContent-Type: "
                        + type
                        + "\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        byte[] prefix = head.getBytes(StandardCharsets.UTF_8);
        byte[] suffix = tail.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[prefix.length + content.length + suffix.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(content, 0, result, prefix.length, content.length);
        System.arraycopy(suffix, 0, result, prefix.length + content.length, suffix.length);
        return result;
    }

    /** 一次响应：状态码 + 原始体，另带 JSON 取值方法。 */
    public record Response(int status, String body, ObjectMapper json) {

        public JsonNode node() {
            return json.readTree(body);
        }

        public String text(String pointer) {
            JsonNode node = node().at(pointer);
            // 数字按十进制原文返回：asString() 会先转 double，10.000 会变成 10.0
            return node.isNumber() ? node.decimalValue().toPlainString() : node.asString();
        }

        public int number(String pointer) {
            return node().at(pointer).asInt();
        }

        public boolean bool(String pointer) {
            return node().at(pointer).asBoolean();
        }
    }
}
