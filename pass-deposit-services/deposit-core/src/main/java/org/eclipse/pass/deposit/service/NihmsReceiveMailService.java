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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Service
public class NihmsReceiveMailService {
    private static final Logger LOG = LoggerFactory.getLogger(NihmsReceiveMailService.class);

    private final PassClient passClient;
    private final String nihmsRepositoryKey;
    private final Address nihmsFromEmail;

    public NihmsReceiveMailService(PassClient passClient,
                                   @Value("${pass.deposit.pmc.repo.key}") String nihmsRepositoryKey,
                                   @Value("${pass.deposit.nihms.email.from}") String nihmsFromEmail)
        throws AddressException {
        this.passClient = passClient;
        this.nihmsRepositoryKey = nihmsRepositoryKey;
        this.nihmsFromEmail = new InternetAddress(nihmsFromEmail);
    }

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

    private final Pattern depositSuccessPattern =
        Pattern.compile("package id=(.*) for Manuscript ID (.*) was submitted successfully.*",
            Pattern.CASE_INSENSITIVE);

    public void handleReceivedMail(MimeMessage receivedMessage) {
        try {
            if (isEmailNotNihms(receivedMessage)) {
                return;
            }
            String content = receivedMessage.getContent().toString();
            Document document = Jsoup.parse(content);
            document.select(".message").forEach(element -> {
                String elementText = element.text();
                depositFailurePatterns.forEach(pattern -> {
                    Matcher matcher = pattern.matcher(elementText);
                    matcher.results().forEach(matchResult -> {
                        String message =  matchResult.group(0);
                        String packageId =  matchResult.group(1);
                        String submissionId = parseSubmissionId(packageId);
                        try {
                            updateDepositRejected(submissionId, message);
                        } catch (Exception e) {
                            LOG.error("Error updating nihms deposit for submission ID " + submissionId, e);
                        }
                    });
                });
                Matcher successMatcher = depositSuccessPattern.matcher(elementText);
                successMatcher.results().forEach(matchResult -> {
                    String packageId =  matchResult.group(1);
                    String submissionId = parseSubmissionId(packageId);
                    String nihmsId =  matchResult.group(2);
                    try {
                        updateDepositAccepted(submissionId, nihmsId);
                    } catch (Exception e) {
                        LOG.error("Error updating nihms deposit for submission ID " + submissionId, e);
                    }
                });
            });

        } catch (Exception e) {
            LOG.error("Error processing nihms email", e);
        }
    }

    private boolean isEmailNotNihms(MimeMessage mimeMessage) throws MessagingException {
        boolean fromNihms = Arrays.asList(mimeMessage.getFrom()).contains(nihmsFromEmail);
        return !fromNihms || !mimeMessage.getSubject().startsWith("Bulk submission");
    }

    private void updateDepositRejected(String submissionId, String message) throws IOException {
        getDeposits(submissionId).forEach(deposit -> {
            deposit.setDepositStatus(DepositStatus.REJECTED);
            deposit.setStatusMessage(message);
            updateDeposit(deposit);
        });
    }

    private void updateDepositAccepted(String submissionId, String nihmsId) throws IOException {
        getDeposits(submissionId).forEach(deposit -> {
            deposit.setDepositStatus(DepositStatus.ACCEPTED);
            deposit.setDepositStatusRef(nihmsId);
            updateDeposit(deposit);
        });
    }

    private Stream<Deposit> getDeposits(String submissionId) throws IOException {
        PassClientSelector<Deposit> sel = new PassClientSelector<>(Deposit.class);
        sel.setFilter(RSQL.and(
            RSQL.equals("submission.id",  submissionId),
            RSQL.equals("repository.repositoryKey", nihmsRepositoryKey)
        ));
        return passClient.streamObjects(sel);
    }

    private void updateDeposit(Deposit deposit) {
        try {
            passClient.updateObject(deposit);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String parseSubmissionId(String packageId) {
        String[] parts = packageId.split("_");
        if (parts.length < 4) {
            throw new RuntimeException("Invalid packageId, no submissionId part: " + packageId);
        }
        return parts[3];
    }
}
