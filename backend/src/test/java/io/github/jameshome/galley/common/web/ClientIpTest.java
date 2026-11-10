package io.github.jameshome.galley.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 客户端 IP 取法的纯单元测试。 */
class ClientIpTest {

    @Test
    void 取XFF首跳而非整条链() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.9");
        assertThat(ClientIp.from(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void 无XFF回退到remoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.5");
        assertThat(ClientIp.from(request)).isEqualTo("192.168.1.5");
    }

    @Test
    void 空白XFF回退到remoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", " ");
        request.setRemoteAddr("192.168.1.6");
        assertThat(ClientIp.from(request)).isEqualTo("192.168.1.6");
    }
}
