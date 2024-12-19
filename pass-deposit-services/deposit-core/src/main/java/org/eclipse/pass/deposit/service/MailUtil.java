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
import java.util.Objects;

import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;

/**
 * @author Russ Poetker (rpoetke1@jh.edu)
 */
public class MailUtil {

    private MailUtil() {}

    static String getHtmlText(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/html")) {
            return part.getContent().toString();
        }

        if (part.isMimeType("multipart/alternative")) {
            Multipart multipart = (Multipart) part.getContent();
            int count = multipart.getCount();
            for (int i = 0; i < count; i++) {
                Part bodyPart = multipart.getBodyPart(i);
                if (bodyPart.isMimeType("text/html")) {
                    return bodyPart.getContent().toString();
                } else if (bodyPart.isMimeType("multipart/*")) {
                    return getHtmlText(bodyPart);
                }
            }
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            int count = multipart.getCount();
            for (int i = 0; i < count; i++) {
                Part bodyPart = multipart.getBodyPart(i);
                String content = getHtmlText(bodyPart);
                if (Objects.nonNull(content)) {
                    return content;
                }
            }
        }

        return null;
    }
}
