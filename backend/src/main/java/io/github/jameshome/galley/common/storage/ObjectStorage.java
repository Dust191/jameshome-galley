package io.github.jameshome.galley.common.storage;

/**
 * 对象存储抽象（ADR-0005）。
 *
 * <p>业务代码只依赖本接口；开发环境用 MinIO，生产切 OSS 时新增实现并改装配，调用方零改动。
 *
 * <p>{@code key} 是对象在桶内的路径，数据库存 key 不存完整 URL——URL 由 {@link #signedUrl(String)} 在返回给前端时按需签发，
 * 桶保持私有读；换域名、换访问方式都不需要改数据。
 */
public interface ObjectStorage {

    /** 写入对象，同 key 覆盖。 */
    void put(String key, byte[] content, String contentType);

    /** 读取对象；不存在时抛业务异常而非返回 null。 */
    StoredObject get(String key);

    /** 删除对象；对象不存在视为成功（幂等）。 */
    void delete(String key);

    /**
     * 签发一个带有效期、可直接给浏览器访问的地址。
     *
     * <p>桶为私有读，地址里的签名参数就是访问凭据；有效期由 {@code galley.storage.signed-url-ttl-seconds} 决定。
     */
    String signedUrl(String key);

    record StoredObject(byte[] content, String contentType) {}
}
