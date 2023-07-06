package org.eclipse.pass.deposit.messaging.config.spring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "pass.deposit.jobs.disabled", havingValue = "false")
public class SchedulerConfig {
}
