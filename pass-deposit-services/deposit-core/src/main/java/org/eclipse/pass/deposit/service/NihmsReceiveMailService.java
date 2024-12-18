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

import static org.eclipse.pass.deposit.service.MailUtil.getHtmlText;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.deposit.provider.nihms.NihmsAssembler;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@ConditionalOnProperty(name = "pass.deposit.nihms.email.enabled")
@Service
public class NihmsReceiveMailService {
    private static final Logger LOG = LoggerFactory.getLogger(NihmsReceiveMailService.class);

    static final String NIHMS_DEP_STATUS_REF_PREFIX = "nihms-id:";

    private final PassClient passClient;
    private final DepositTaskHelper depositTaskHelper;
    private final List<Address> nihmsFromEmail;

    public NihmsReceiveMailService(PassClient passClient,
                                   DepositTaskHelper depositTaskHelper,
                                   @Value("${pass.deposit.nihms.email.from}") String nihmsFromEmail) {
        this.passClient = passClient;
        this.depositTaskHelper = depositTaskHelper;
        this.nihmsFromEmail = Arrays.stream(nihmsFromEmail.split(","))
            .map(emailAddress -> {
                try {
                    return (Address) new InternetAddress(emailAddress);
                } catch (AddressException e) {
                    throw new RuntimeException(e);
                }
            })
            .toList();
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
            LOG.warn("Email received: " + receivedMessage.getSubject());
            if (isEmailNotNihms(receivedMessage)) {
                return;
            }
            LOG.warn("Email is from Nihms");
            String content = getHtmlText(receivedMessage);
            String cleansedContent = StringUtils.normalizeSpace(content);
            LOG.warn("Nihms Email content:" + cleansedContent);
            if (Objects.isNull(content)) {
                LOG.error("No HTML content found in nihms email: " + receivedMessage.getSubject());
                return;
            }
            Elements messageElements = getMessageElements(cleansedContent);
            if (messageElements.isEmpty()) {
                LOG.error("No messages found in nihms email: " + cleansedContent);
                return;
            }
            processMessages(messageElements);
        } catch (Exception e) {
            LOG.error("Error processing nihms email", e);
        }
    }

    private Elements getMessageElements(String content) {
        Document document = Jsoup.parse(content);
        Elements messageElements = document.select(".message");
        return messageElements.isEmpty() ? document.select("td:nth-child(2)") : messageElements;
    }

    private void processMessages(Elements messageElements) {
        messageElements.forEach(element -> {
            String elementText = element.text();
            AtomicBoolean matchFound = new AtomicBoolean(false);
            depositFailurePatterns.forEach(pattern -> {
                Matcher matcher = pattern.matcher(elementText);
                matcher.results().forEach(matchResult -> {
                    matchFound.set(true);
                    String message =  matchResult.group(0);
                    String packageId =  matchResult.group(1);
                    String submissionId = parseSubmissionId(packageId);
                    try {
                        updateDepositRejected(submissionId, packageId, message);
                    } catch (Exception e) {
                        LOG.error("Error updating nihms deposit for submission ID " + submissionId, e);
                    }
                });
            });
            Matcher successMatcher = depositSuccessPattern.matcher(elementText);
            successMatcher.results().forEach(matchResult -> {
                matchFound.set(true);
                String packageId =  matchResult.group(1);
                String submissionId = parseSubmissionId(packageId);
                String nihmsId =  matchResult.group(2);
                try {
                    updateDepositSuccess(submissionId, packageId, nihmsId);
                } catch (Exception e) {
                    LOG.error("Error updating nihms deposit for submission ID " + submissionId, e);
                }
            });
            if (!matchFound.get()) {
                LOG.error("No match found in nihms email message: " + elementText);
            }
        });
    }

    private boolean isEmailNotNihms(MimeMessage mimeMessage) throws MessagingException {
        boolean fromNihms = Arrays.stream(mimeMessage.getFrom()).anyMatch(nihmsFromEmail::contains);
        return !fromNihms || !mimeMessage.getSubject().contains("Bulk submission");
    }

    private void updateDepositRejected(String submissionId, String packageId, String message) throws IOException {
        getDeposits(submissionId, packageId).forEach(deposit -> {
            deposit.setDepositStatus(DepositStatus.REJECTED);
            deposit.setStatusMessage(message);
            updateDeposit(deposit);
        });
    }

    private void updateDepositSuccess(String submissionId, String packageId, String nihmsId) throws IOException {
        getDeposits(submissionId, packageId).forEach(deposit -> {
            deposit.setDepositStatus(DepositStatus.SUBMITTED);
            deposit.setDepositStatusRef(NIHMS_DEP_STATUS_REF_PREFIX + nihmsId);
            deposit.setStatusMessage("Accepted by the NIHMS workflow. NIHMS-ID: " + nihmsId);
            updateDeposit(deposit);
        });
    }

    private Stream<Deposit> getDeposits(String submissionId, String packageKey) throws IOException {
        PassClientSelector<Deposit> sel = new PassClientSelector<>(Deposit.class);
        sel.setFilter(RSQL.and(
            RSQL.equals("submission.id",  submissionId),
            RSQL.equals("depositStatusRef", NihmsAssembler.NIHMS_PKG_DEP_REF_PREFIX + packageKey)
        ));
        return passClient.streamObjects(sel);
    }

    private void updateDeposit(Deposit deposit) {
        try {
            passClient.updateObject(deposit);
            depositTaskHelper.updateDepositRepositoryCopyStatus(deposit);
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
