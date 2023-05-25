/*
 *
 *  * Copyright 2023 Johns Hopkins University
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.eclipse.pass.loader.nihms;

/**
 * Enum for NIHMS URL types
 */
public enum UrlType {

    /**
     * Compliant status for NIHMS publication
     */
    COMPLIANT("c"),
    /**
     * Non-compliant status for NIHMS publication
     */
    NON_COMPLIANT("n"),
    /**
     * In process status for NIHMS publication
     */
    IN_PROCESS("p");

    private String code;

    UrlType(String code) {
        this.code = code;
    }

    /**
     * Get the code for the URL type
     * @return the code
     */
    public String getCode() {
        return code;
    }
}
