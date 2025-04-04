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
package org.eclipse.pass.deposit.config.spring;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.pass.deposit.DepositServiceErrorHandler;
import org.eclipse.pass.deposit.assembler.Assembler;
import org.eclipse.pass.deposit.assembler.ExceptionHandlingThreadPoolExecutor;
import org.eclipse.pass.deposit.config.repository.Repositories;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.deposit.model.InMemoryMapRegistry;
import org.eclipse.pass.deposit.model.Packager;
import org.eclipse.pass.deposit.model.Registry;
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

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Configuration
@EnableAutoConfiguration(exclude = {RestTemplateAutoConfiguration.class})
@Import(RepositoriesFactoryBeanConfig.class)
public class DepositConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DepositConfig.class);

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
    public Registry<Packager> packagerRegistry(Repositories repositories, ApplicationContext appCtx) {
        Map<String, Assembler> assemblers = getAssemblers(appCtx);
        Map<String, Transport> transports = getTransports(appCtx);
        Map<String, Packager> packagers = repositories.getAllConfigs().stream()
            .map(repoConfig -> {
                String dspBeanName = null;

                String repositoryKey = repoConfig.getRepositoryKey();
                String transportProtocol = repoConfig.getTransportConfig()
                    .getProtocolBinding()
                    .getProtocol();
                String assemblerBean = repoConfig.getAssemblerConfig()
                    .getBeanName();

                // Resolve the Transport impl from the protocol binding,
                // currently assumes a 1:1 protocol binding to transport impl
                Transport transport = transports.values()
                    .stream()
                    .filter(
                        candidate -> candidate.protocol()
                            .name()
                            .equalsIgnoreCase(
                                transportProtocol))
                    .findAny()
                    .orElseThrow(() ->
                        new RuntimeException(
                            "Missing Transport implementation for protocol binding " +
                                transportProtocol));

                LOG.info(
                    "Configuring Packager for Repository configuration {}",
                    repoConfig.getRepositoryKey());
                LOG.info("  Repository Key: {}", repositoryKey);
                LOG.info("  Assembler: {}", assemblerBean);
                LOG.info("  Transport Binding: {}", transportProtocol);
                LOG.info("  Transport Implementation: {}", transport);
                if (dspBeanName != null) {
                    LOG.info("  Deposit Status Processor: {}", dspBeanName);
                }

                return new Packager(repositoryKey,
                    assemblers.get(assemblerBean),
                    transport,
                    repoConfig);
            })
            .collect(
                Collectors.toMap(Packager::getName, Function.identity()));

        Map<String, Packager> packagerTreeMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        packagerTreeMap.putAll(packagers);
        return new InMemoryMapRegistry<>(packagerTreeMap);
    }

    private Map<String, Transport> getTransports(ApplicationContext appCtx) {

        Map<String, Transport> transports = appCtx.getBeansOfType(Transport.class);

        if (transports.size() == 0) {
            LOG.error("No Transport implementations found; Deposit Services will not properly process deposits");
            return transports;
        }

        transports.forEach((beanName, impl) -> {
            LOG.debug("Discovered Transport implementation {}: {}", beanName, impl.getClass().getName());
            if (!appCtx.isSingleton(beanName)) {
                LOG.warn("Transport implementation with beanName {} is *not* a singleton; this will likely " +
                         "result in corrupted packages being streamed to downstream Repositories.", beanName);
            }
        });

        return transports;
    }

    private Map<String, Assembler> getAssemblers(ApplicationContext appCtx) {
        Map<String, Assembler> assemblers = appCtx.getBeansOfType(Assembler.class);

        if (assemblers.size() == 0) {
            LOG.error("No Assembler implementations found; Deposit Services will not properly process deposits.");
            return assemblers;
        }

        assemblers.forEach((beanName, impl) -> {
            LOG.debug("Discovered Assembler implementation {}: {}", beanName, impl.getClass().getName());
            if (!appCtx.isSingleton(beanName)) {
                LOG.warn("Assembler implementation with beanName {} is *not* a singleton; this will likely " +
                         "result in corrupted packages being streamed to downstream Repositories.", beanName);
            }
        });

        return assemblers;
    }

    @Bean
    @SuppressWarnings("SpringJavaAutowiringInspection")
    DepositServiceErrorHandler errorHandler(CriticalRepositoryInteraction cri) {
        return new DepositServiceErrorHandler(cri);
    }

    @Bean
    ExceptionHandlingThreadPoolExecutor executorService() {
        return new ExceptionHandlingThreadPoolExecutor(1, 2, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(10));
    }

}
