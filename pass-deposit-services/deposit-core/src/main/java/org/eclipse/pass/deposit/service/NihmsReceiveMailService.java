/*
 * Copyright 2023 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.pass.deposit.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.internet.MimeMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Service
public class NihmsReceiveMailService {
    private static final Logger LOG = LoggerFactory.getLogger(NihmsReceiveMailService.class);

    private final List<Pattern> depositFailurePatterns = List.of(
        Pattern.compile("package id=(.*) failed because.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("package id=(.*) was not submitted.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("package id=(.*) is corrupt.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("package id=(.*) was already used for Manuscript.*submission not created.*",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("package id=(.*) for Manuscript.*submitted with the following problem.*",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("package id=(.*) contains unrecognized.*", Pattern.CASE_INSENSITIVE)
    );

    private final Pattern depositSuccessPattern = Pattern.compile("package id=(.*) submitted successfully.*",
        Pattern.CASE_INSENSITIVE);

    public void handleReceivedMail(MimeMessage receivedMessage) {
        try {
            // TODO only update if email subject is for `Bulk Submission`
//            String subject = receivedMessage.getSubject();
            String content = receivedMessage.getContent().toString();
            Document document = Jsoup.parse(content);
            document.select(".message").forEach(element -> {
                String elementText = element.text();
                LOG.info("element message: " + elementText);
                depositFailurePatterns.forEach(pattern -> {
                    Matcher matcher = pattern.matcher(elementText);
                    matcher.results().forEach(matchResult -> {
                        LOG.info("Fail match result group 0: " + matchResult.group(0));
                        LOG.info("Fail match result group 1: " + matchResult.group(1));
                    });
                });
                Matcher successMatcher = depositSuccessPattern.matcher(elementText);
                successMatcher.results().forEach(matchResult -> {
                    LOG.info("Success match result group 0: " + matchResult.group(0));
                    LOG.info("Success match result group 1: " + matchResult.group(1));
                });
            });

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
