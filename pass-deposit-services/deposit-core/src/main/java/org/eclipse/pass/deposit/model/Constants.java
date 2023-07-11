/*
 *
 *  * Copyright 2018 Johns Hopkins University
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
package org.eclipse.pass.deposit.model;

/**
 * Deposit and Notification Services constants.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public final class Constants {

    /**
     * Possible values for the {@link #SWORD_STATE} predicate as represented by URIs.
     */
    public static final class SWORD {

        /**
         * The predicate representing SWORD deposit state
         */
        public static final String SWORD_STATE = "http://purl.org/net/sword/terms/state";

        /**
         * SWORD state indicating the item is archived
         */
        public static final String SWORD_STATE_ARCHIVED = "http://dspace.org/state/archived";

        /**
         * SWORD state indicating the item has been withdrawn
         */
        public static final String SWORD_STATE_WD = "http://dspace.org/state/withdrawn";

        /**
         * SWORD state indicating the item is in progress
         */
        public static final String SWORD_STATE_INPROGRESS = "http://dspace.org/state/inprogress";

        /**
         * SWORD state indicating the item is under review
         */
        public static final String SWORD_STATE_INREVIEW = "http://dspace.org/state/inreview";

    }

}
