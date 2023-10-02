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
package org.eclipse.pass.support.grant.data;

import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.User;

/**
 * This interface defines update methods for existing (stored) grants, users and funders. In practice,
 * implementations will generally
 * split this functionalite into two steps - the first step will reason over a stored object and the object developed
 * in a system pull,
 * and make th decision whether the object needs to be updated. The update methods eill act on that decision
 * appropriately.
 */
public interface PassEntityUtil {

    /**
     * This method takes a Funder from the data source, calculates whether it needs to be updated, and if so, returns
     * the updated object
     * to be be ingested into the repository. if not, returns null.
     *
     * @param stored the Funder as it is stored in the PASS backend
     * @param system the version of the Funder from the data sourcee pull
     * @return the updated Funder - null if the Funder does not need to be updated
     */
    Funder update(Funder system, Funder stored);

    /**
     * This method takes a User from the data source, calculates whether it needs to be updated, and if so, returns
     * the updated object
     * to be be ingested into the repository. if not, returns null.
     *
     * @param stored the User as it is stored in the PASS backend
     * @param system the version of the User from the data sourcee pull
     * @return the updated User - null if the User does not need to be updated
     */
    User update(User system, User stored);

    /**
     * This method takes a Grantfrom the data source, calculates whether it needs to be updated, and if so, returns
     * the updated object
     * to be be ingested into the repository. if not, returns null.
     *
     * @param stored the Grant as it is stored in the PASS backend
     * @param system the version of the Grant from the data sourcee pull
     * @return the updated Grant - null if the Grant does not need to be updated
     */
    Grant update(Grant system, Grant stored);

    /**
     * Set any institutional User properties that are needed.
     * @param user the User
     * @throws GrantDataException if problem occurs setting props
     */
    void setInstitutionalUserProps(User user) throws GrantDataException;

    /**
     * Returns the employee locator ID of the user.
     * @param user the user
     * @return the employee id
     * @throws GrantDataException of the employee ID is not found
     */
    String getEmployeeLocatorId(User user) throws GrantDataException;

}
