package org.eclipse.pass.deposit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Service
public class NihmsReceiveMailService {
    private static final Logger LOG = LoggerFactory.getLogger(NihmsReceiveMailService.class);

    public void handleReceivedMail(MimeMessage receivedMessage) {
        try {
            String subject = receivedMessage.getSubject();
            String content = receivedMessage.getContent().toString();
            LOG.info(subject + "::" + content);
            // TODO
            // send content to be parsed for state transitions
            // send deposit update message based on state transition
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
