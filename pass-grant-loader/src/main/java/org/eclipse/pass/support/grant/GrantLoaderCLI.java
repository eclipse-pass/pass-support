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
package org.eclipse.pass.support.grant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * This Class manages the command line interaction for the loading and updating processes
 *
 * @author jrm@jhu.edu
 */
@SpringBootApplication
@SuppressWarnings({"checkstyle:hideutilityclassconstructor"})
public class GrantLoaderCLI {

    public static void main(String[] args) {
        SpringApplication.run(GrantLoaderCLI.class, args);
    }

}