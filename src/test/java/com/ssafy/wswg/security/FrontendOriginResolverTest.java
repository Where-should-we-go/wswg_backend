package com.ssafy.wswg.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class FrontendOriginResolverTest {

    private MockHttpServletRequest request(String scheme, String host) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme(scheme);
        if (host != null) {
            request.addHeader("Host", host);
        }
        return request;
    }

    @Test
    void 허용목록에_있는_요청_origin_을_그대로_쓴다() {
        FrontendOriginResolver resolver =
                new FrontendOriginResolver("http://localhost:3000,http://192.168.0.5:3000");

        assertThat(resolver.resolve(request("http", "192.168.0.5:3000")))
                .isEqualTo("http://192.168.0.5:3000");
    }

    @Test
    void 같은_백엔드라도_localhost_접속은_localhost_로_되돌린다() {
        FrontendOriginResolver resolver =
                new FrontendOriginResolver("http://localhost:3000,http://192.168.0.5:3000");

        assertThat(resolver.resolve(request("http", "localhost:3000")))
                .isEqualTo("http://localhost:3000");
    }

    @Test
    void 허용목록에_없는_호스트는_기본_origin_으로_폴백한다() {
        FrontendOriginResolver resolver =
                new FrontendOriginResolver("http://localhost:3000,http://192.168.0.5:3000");

        // 스푸핑된 Host 헤더로 accessToken 이 새는 것을 막는다.
        assertThat(resolver.resolve(request("http", "evil.example.com")))
                .isEqualTo("http://localhost:3000");
    }

    @Test
    void Host_헤더가_없으면_기본_origin_을_쓴다() {
        FrontendOriginResolver resolver = new FrontendOriginResolver("http://localhost:3000");

        assertThat(resolver.resolve(request("http", null))).isEqualTo("http://localhost:3000");
    }

    @Test
    void 공백과_빈_항목은_무시하고_파싱한다() {
        FrontendOriginResolver resolver =
                new FrontendOriginResolver(" http://localhost:3000 , , http://10.0.0.2:3000 ");

        assertThat(resolver.resolve(request("http", "10.0.0.2:3000")))
                .isEqualTo("http://10.0.0.2:3000");
        assertThat(resolver.defaultOrigin()).isEqualTo("http://localhost:3000");
    }
}
