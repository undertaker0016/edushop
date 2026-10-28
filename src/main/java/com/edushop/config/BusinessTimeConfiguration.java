package com.edushop.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Provides one business clock for checkout, payment and scheduled work. */
@Configuration
@EnableScheduling
public class BusinessTimeConfiguration {

	@Bean
	Clock businessClock() {
		return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
	}
}
