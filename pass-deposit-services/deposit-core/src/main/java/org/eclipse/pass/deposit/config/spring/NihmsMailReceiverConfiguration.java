package org.eclipse.pass.deposit.config.spring;

import java.util.Properties;

import jakarta.mail.internet.MimeMessage;
import org.eclipse.pass.deposit.service.NihmsReceiveMailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.mail.ImapMailReceiver;
import org.springframework.integration.mail.MailReceiver;
import org.springframework.integration.mail.MailReceivingMessageSource;
import org.springframework.messaging.Message;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
@Configuration
@EnableIntegration
@ConditionalOnProperty(name = "pass.deposit.nihms.email.enabled", havingValue = "true")
public class NihmsMailReceiverConfiguration {

    @Value("${nihms.mail.username}")
    private String nihmsMailUsername;

    @Value("${nihms.mail.password}")
    private String nihmsMailPassword;

    @Value("${nihms.mail.host}")
    private String nihmsImapHost;

    @Value("${nihms.mail.port}")
    private String nihmsImapPort;

    private final NihmsReceiveMailService nihmsReceiveMailService;

    public NihmsMailReceiverConfiguration(NihmsReceiveMailService nihmsReceiveMailService) {
        this.nihmsReceiveMailService = nihmsReceiveMailService;
    }

    @ServiceActivator(inputChannel = "receiveEmailChannel")
    public void receive(Message<?> message) {
        nihmsReceiveMailService.handleReceivedMail((MimeMessage) message.getPayload());
    }

    @Bean("receiveEmailChannel")
    public DirectChannel defaultChannel() {
        DirectChannel directChannel = new DirectChannel();
        directChannel.setDatatypes(jakarta.mail.internet.MimeMessage.class);
        return directChannel;
    }

    @Bean()
    @InboundChannelAdapter(
        channel = "receiveEmailChannel",
        poller = @Poller(fixedDelay = "${pass.deposit.nihms.email.delay}")
    )
    public MailReceivingMessageSource mailMessageSource(MailReceiver mailReceiver) {
        return new MailReceivingMessageSource(mailReceiver);
    }

    @Bean
    public MailReceiver imapMailReceiver() {
        // TODO may be imaps, need to confirm with Jeff
        String storeUrl = String.format("imap://%s:%s@%s:%s/inbox",
            nihmsMailUsername, nihmsMailPassword, nihmsImapHost, nihmsImapPort);
        ImapMailReceiver imapMailReceiver = new ImapMailReceiver(storeUrl);
        imapMailReceiver.setShouldMarkMessagesAsRead(true);
        imapMailReceiver.setShouldDeleteMessages(false);
        imapMailReceiver.setMaxFetchSize(10);
        Properties javaMailProperties = new Properties();
        // TODO may be imaps, need to confirm with Jeff
//        javaMailProperties.put("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
//        javaMailProperties.put("mail.imap.socketFactory.fallback", false);
        javaMailProperties.put("mail.store.protocol", "imap");
        javaMailProperties.put("mail.debug", true);
        imapMailReceiver.setJavaMailProperties(javaMailProperties);
        return imapMailReceiver;
    }

}
