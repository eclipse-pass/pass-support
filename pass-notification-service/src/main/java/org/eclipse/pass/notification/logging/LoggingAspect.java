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
package org.eclipse.pass.notification.logging;

import static java.lang.String.join;
import static java.util.Optional.ofNullable;

import java.util.Collections;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.eclipse.pass.notification.dispatch.DispatchException;
import org.eclipse.pass.notification.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
@Aspect
public class LoggingAspect {

    private static final Logger NOTIFICATION_LOG = LoggerFactory.getLogger("NOTIFICATION_LOG");

    @Pointcut("execution(public * org.eclipse.pass.notification.dispatch.DispatchService.dispatch(..))")
    void dispatchApiMethod() {}

    /**
     * Pointcut for before dispatch logging.
     * @param jp the joinpoint
     */
    @Before("dispatchApiMethod()")
    public void logNotification(JoinPoint jp) {
        Object[] args = jp.getArgs();
        if (args == null || args.length == 0) {
            return;
        }

        Notification n = (Notification) args[0];

        NOTIFICATION_LOG.debug("Dispatching notification to [{}], cc [{}] bcc [{}] (Notification type: {}, Event " +
                               "ID: {}, Resource ID: {})",
                join(",", ofNullable(n.getRecipients()).orElseGet(Collections::emptyList)),
                join(",", ofNullable(n.getCc()).orElseGet(Collections::emptyList)),
                join(",", ofNullable(n.getBcc()).orElseGet(Collections::emptyList)),
                n.getType(),
                n.getEventId(),
                n.getResourceId());
    }

    /**
     * Pointcut for after dispatch logging.
     * @param jp the joinpoint
     */
    @After("dispatchApiMethod()")
    public void logNotificationReturn(JoinPoint jp) {
        Object[] args = jp.getArgs();
        if (args == null || args.length == 0) {
            return;
        }

        Notification n = (Notification) args[0];

        NOTIFICATION_LOG.info(
            "Successfully dispatched notification to [{}], cc [{}] bcc [{}] (Notification type: {}, " +
                "Event ID: {}, Resource ID: {})",
            join(",", ofNullable(n.getRecipients()).orElseGet(Collections::emptyList)),
            join(",", ofNullable(n.getCc()).orElseGet(Collections::emptyList)),
            join(",", ofNullable(n.getBcc()).orElseGet(Collections::emptyList)),
            n.getType(),
            n.getEventId(),
            n.getResourceId());

    }

    /**
     * Pointcut for exception during dispatch logging.
     * @param ex the exception
     */
    @AfterThrowing(pointcut = "dispatchApiMethod()", throwing = "ex")
    public void logNotificationError(Throwable ex) {
        Notification n;

        if (ex instanceof DispatchException && (n = ((DispatchException) ex).getNotification()) != null) {
            NOTIFICATION_LOG.warn(
                "FAILED dispatching notification to [{}], cc [{}] bcc [{}] (Notification type: {}, Event ID: {}, " +
                    "Resource ID: {})",
                join(",", ofNullable(n.getRecipients()).orElseGet(Collections::emptyList)),
                join(",", ofNullable(n.getCc()).orElseGet(Collections::emptyList)),
                join(",", ofNullable(n.getBcc()).orElseGet(Collections::emptyList)),
                n.getType(),
                n.getEventId(),
                n.getResourceId(),
                ex);
            return;
        }

        NOTIFICATION_LOG.warn("FAILED dispatching notification", ex);
    }

}