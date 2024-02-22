package org.eclipse.pass.loader.nihms;

import org.eclipse.pass.support.client.SubmissionStatusService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NihmsTransformLoadConfig {

    @Bean
    public SubmissionStatusService submissionStatusService() {
        return new SubmissionStatusService();
    }
}
