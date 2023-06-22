/*
 * Copyright 2018 Johns Hopkins University
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
package org.eclipse.pass.deposit.messaging.config.spring;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.DocumentBuilderFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.abdera.parser.Parser;
import org.apache.abdera.parser.stax.FOMParserFactory;
import org.eclipse.pass.support.messaging.cri.CriticalPath;
import org.eclipse.pass.deposit.assembler.Assembler;
import org.eclipse.pass.deposit.assembler.ExceptionHandlingThreadPoolExecutor;
import org.eclipse.pass.deposit.messaging.DepositServiceErrorHandler;
import org.eclipse.pass.deposit.messaging.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.messaging.config.repository.Repositories;
import org.eclipse.pass.deposit.messaging.model.InMemoryMapRegistry;
import org.eclipse.pass.deposit.messaging.model.Packager;
import org.eclipse.pass.deposit.messaging.model.Registry;
import org.eclipse.pass.deposit.messaging.policy.DirtyDepositPolicy;
import org.eclipse.pass.deposit.messaging.service.DepositTask;
import org.eclipse.pass.deposit.messaging.status.DefaultDepositStatusProcessor;
import org.eclipse.pass.deposit.messaging.status.DepositStatusResolver;
import org.eclipse.pass.deposit.messaging.support.swordv2.AtomFeedStatusResolver;
import org.eclipse.pass.deposit.messaging.support.swordv2.ResourceResolver;
import org.eclipse.pass.deposit.messaging.support.swordv2.ResourceResolverImpl;
import org.eclipse.pass.deposit.transport.Transport;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.SubmissionStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Configuration
@EnableAutoConfiguration(exclude = {RestTemplateAutoConfiguration.class})
@Import(RepositoriesFactoryBeanConfig.class)
public class DepositConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DepositConfig.class);

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    @Value("${pass.deposit.workers.concurrency}")
    private int depositWorkersConcurrency;

    @Value("${pass.deposit.http.agent}")
    private String passHttpAgent;

    @Value("${pass.deposit.repository.configuration}")
    private Resource repositoryConfigResource;

    @Value("${pass.client.url}")
    private String passClientUrl;

    @Value("${pass.client.user}")
    private String passClientUser;

    @Value("${pass.client.password}")
    private String passClientPassword;

    @Bean
    public PassClient passClient() {
        return PassClient.newInstance(passClientUrl, passClientUser, passClientPassword);
    }

    @Bean
    public SubmissionStatusService submissionStatusService() {
        return new SubmissionStatusService(passClient());
    }

    @Bean
    @Scope(SCOPE_PROTOTYPE)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public Registry<Packager> packagerRegistry(Map<String, Packager> packagers) {
        return new InMemoryMapRegistry<>(packagers);
    }

    @Bean
    public Map<String, Packager> packagers(@Value("#{assemblers}") Map<String, Assembler> assemblers,
                                           @Value("#{transports}") Map<String, Transport> transports,
                                           Repositories repositories,
                                           ApplicationContext appCtx) {
        // TODO Deposit service port pending

//        Map<String, Packager> packagers = repositories.keys().stream().map(repositories::getConfig)
//              .map(repoConfig -> {
//                  String dspBeanName = null;
//                  DepositStatusProcessor dsp = null;
//                  if (repoConfig.getRepositoryDepositConfig() != null &&
//                          repoConfig.getRepositoryDepositConfig().getDepositProcessing() != null) {
//                      dspBeanName = repoConfig.getRepositoryDepositConfig()
//                                              .getDepositProcessing()
//                                              .getBeanName();
//                      dsp = null;
//                      if (dspBeanName != null) {
//                          dsp = appCtx.getBean(dspBeanName, DepositStatusProcessor.class);
//                          repoConfig.getRepositoryDepositConfig()
//                                    .getDepositProcessing().setProcessor(dsp);
//                      }
//                  }
//
//                  String repositoryKey = repoConfig.getRepositoryKey();
//                  String transportProtocol = repoConfig.getTransportConfig()
//                                                       .getProtocolBinding()
//                                                       .getProtocol();
//                  String assemblerBean = repoConfig.getAssemblerConfig()
//                                                   .getBeanName();
//
//                  // Resolve the Transport impl from the protocol binding,
//                  // currently assumes a 1:1 protocol binding to transport impl
//                  Transport transport = transports.values()
//                      .stream()
//                      .filter(
//                          candidate -> candidate.protocol()
//                                                .name()
//                                                .equalsIgnoreCase(
//                                                    transportProtocol))
//                      .findAny()
//                      .orElseThrow(() ->
//                                       new RuntimeException(
//                                           "Missing Transport implementation for protocol binding " +
//                                           transportProtocol));
//
//                  LOG.info(
//                      "Configuring Packager for Repository configuration {}",
//                      repoConfig.getRepositoryKey());
//                  LOG.info("  Repository Key: {}", repositoryKey);
//                  LOG.info("  Assembler: {}", assemblerBean);
//                  LOG.info("  Transport Binding: {}", transportProtocol);
//                  LOG.info("  Transport Implementation: {}", transport);
//                  if (dspBeanName != null) {
//                      LOG.info("  Deposit Status Processor: {}", dspBeanName);
//                  }
//
//                  return new Packager(repositoryKey,
//                                      assemblers.get(assemblerBean),
//                                      transport,
//                                      repoConfig,
//                                      dsp);
//              })
//              .collect(
//                  Collectors.toMap(Packager::getName, Function.identity()));
//
//        return packagers;
        return new HashMap<>();
    }

    @Bean
    public Map<String, Transport> transports(ApplicationContext appCtx) {

        Map<String, Transport> transports = appCtx.getBeansOfType(Transport.class);

        if (transports.size() == 0) {
            LOG.error("No Transport implementations found; Deposit Services will not properly process deposits");
            return transports;
        }

        transports.forEach((beanName, impl) -> {
            LOG.debug("Discovered Transport implementation {}: {}", beanName, impl.getClass().getName());
            if (!appCtx.isSingleton(beanName)) {
                LOG.warn("Transport implementation {} with beanName {} is *not* a singleton; this will likely " +
                         "result in corrupted packages being streamed to downstream Repositories.");
            }
        });

        return transports;
    }

    @Bean
    public Map<String, Assembler> assemblers(ApplicationContext appCtx) {
        Map<String, Assembler> assemblers = appCtx.getBeansOfType(Assembler.class);

        if (assemblers.size() == 0) {
            LOG.error("No Assembler implementations found; Deposit Services will not properly process deposits.");
            return assemblers;
        }

        assemblers.forEach((beanName, impl) -> {
            LOG.debug("Discovered Assembler implementation {}: {}", beanName, impl.getClass().getName());
            if (!appCtx.isSingleton(beanName)) {
                LOG.warn("Assembler implementation {} with beanName {} is *not* a singleton; this will likely " +
                         "result in corrupted packages being streamed to downstream Repositories.");
            }
        });

        return assemblers;
    }

    @Bean
    public DocumentBuilderFactory dbf() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf;
    }

    @Bean
    public ThreadPoolTaskExecutor depositWorkers(DepositServiceErrorHandler errorHandler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(depositWorkersConcurrency);
        executor.setQueueCapacity(depositWorkersConcurrency * 2);
        executor.setRejectedExecutionHandler((rejectedTask, exe) -> {
            String msg = String.format("Task %s@%s rejected, will be retried later.",
                                       rejectedTask.getClass().getSimpleName(),
                                       toHexString(identityHashCode(rejectedTask)));
            if (rejectedTask instanceof DepositTask && ((DepositTask) rejectedTask).getDepositWorkerContext() != null) {
                DepositServiceRuntimeException ex = new DepositServiceRuntimeException(msg,
                        ((DepositTask) rejectedTask).getDepositWorkerContext().deposit());
                errorHandler.handleError(ex);
            } else {
                LOG.error(msg);
            }
        });

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setThreadNamePrefix("Deposit-Worker-");
        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setName("Deposit-Worker-" + THREAD_COUNTER.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, throwable) -> errorHandler.handleError(throwable));
            return t;
        };
        executor.setThreadFactory(tf);
        return executor;
    }

    @Bean
    public AtomFeedStatusResolver atomFeedStatusParser(Parser abderaParser, ResourceResolver resourceResolver) {
        return new AtomFeedStatusResolver(abderaParser, resourceResolver);
    }

    @Bean
    public ResourceResolverImpl resourceResolver(
        @Value("${pass.deposit.transport.swordv2.followRedirects}") boolean followRedirects) {
        return new ResourceResolverImpl(followRedirects);
    }

    @Bean({
        "defaultDepositStatusProcessor",
        "org.dataconservancy.pass.deposit.messaging.status.DefaultDepositStatusProcessor"
    })
    public DefaultDepositStatusProcessor defaultDepositStatusProcessor(DepositStatusResolver<URI, URI> statusResolver) {
        return new DefaultDepositStatusProcessor(statusResolver);
    }

    @Bean
    DirtyDepositPolicy dirtyDepositPolicy() {
        return new DirtyDepositPolicy();
    }

    @Bean
    Parser abderaParser() {
        return new FOMParserFactory().getParser();
    }

    @Bean
    @SuppressWarnings("SpringJavaAutowiringInspection")
        // TODO Deposit service port pending
//    DepositServiceErrorHandler errorHandler(CriticalRepositoryInteraction cri) {
//        return new DepositServiceErrorHandler(cri);
    DepositServiceErrorHandler errorHandler(PassClient passClient) {
        return new DepositServiceErrorHandler(new CriticalPath(passClient));
    }

    @Bean
    ExceptionHandlingThreadPoolExecutor executorService() {
        return new ExceptionHandlingThreadPoolExecutor(1, 2, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(10));
    }

}
