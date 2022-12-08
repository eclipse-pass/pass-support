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
package org.eclipse.pass.support.client.model;

/**
 * PMC route options. Full documentation here: https://publicaccess.nih.gov/submit_process.htm
 *
 * @author Karen Hanson
 */
public enum PmcParticipation {
    /**
     * PMC deposit route A. Journals automatically post the paper to PMC
     */
    A,

    /**
     * PMC deposit route B. Authors must make special arrangements for some journals and
     * publishers to post the paper directly to PMC
     */
    B,

    /**
     * PMC deposit route C. Authors or their designee must submit manuscripts to NIHMS
     */
    C,

    /**
     * PMC deposit route D. Some publishers will submit manuscripts to NIHMS
     */
    D
}
