package com.sanitation.management.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Required for @Scheduled in OtpEmailOutboxWorker. Remove if you already enable scheduling elsewhere. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
