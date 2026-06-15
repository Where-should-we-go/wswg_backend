package com.ssafy.wswg.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@link org.springframework.scheduling.annotation.Scheduled @Scheduled} 활성화를
 * 메인 애플리케이션 클래스와 분리해 격리한다(테스트 슬라이스에서 영향 최소화).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
