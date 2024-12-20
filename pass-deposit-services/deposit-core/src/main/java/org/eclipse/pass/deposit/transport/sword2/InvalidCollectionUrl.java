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

package org.eclipse.pass.deposit.transport.sword2;

/**
 * Exception indicating that a SWORD Collection URL selected by the client for deposit is invalid.
 * For example, when the Collection URL is no present in the SWORD service document.
 */
class InvalidCollectionUrl extends RuntimeException {
    private static final long serialVersionUID = 1L;

    InvalidCollectionUrl(String message) {
        super(message);
    }

    InvalidCollectionUrl(String message, Throwable cause) {
        super(message, cause);
    }

}
