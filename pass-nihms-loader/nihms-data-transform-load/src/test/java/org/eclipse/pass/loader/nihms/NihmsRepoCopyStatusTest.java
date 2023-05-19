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
package org.eclipse.pass.loader.nihms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.pass.loader.nihms.model.NihmsPublication;
import org.eclipse.pass.loader.nihms.model.NihmsStatus;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.junit.jupiter.api.Test;

/**
 * Ensure RepositoryCopy.copyStatus calculates appropriately
 *
 * @author Karen Hanson
 */
public class NihmsRepoCopyStatusTest {

    private static String dateStr = "12/11/2018";

    /**
     * If the NIHMS status is COMPLIANT, the RepositoryCopy status is
     * always COMPLETE regardless of what the other columns in the spreadsheet say
     * and independent of the current CopyStatus
     */
    @Test
    public void testCalCopyStatusComplete() {
        NihmsPublication pub = newTestPub();

        //status is compliant by default for newTestPub

        //no info other than is compliant, return complete
        CopyStatus status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, null);
        assertEquals(CopyStatus.COMPLETE, status);

        //dates in spreadsheet and is compliant, return complete
        pub = newTestPub();
        pub.setFileDepositedDate(dateStr);
        pub.setFinalApprovalDate(dateStr);
        pub.setTaggingCompleteDate(dateStr);
        pub.setInitialApprovalDate(dateStr);
        status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, null);
        assertEquals(CopyStatus.COMPLETE, status);

        //curr deposit status is "accepted", but spreadsheet says it's compliant, change to complete
        pub = newTestPub();
        status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, CopyStatus.ACCEPTED);
        assertEquals(CopyStatus.COMPLETE, status);

    }

    /**
     * The RepositoryCopy is accepted if there is a file deposit date and no other
     * indication of progress. If RepositoryCopy status says complete, it will move it
     * back to be aligned with what NIHMS says.
     */
    @Test
    public void testCalcCopyStatusAccepted() {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        pub.setNihmsId("NIHMS12345");
        pub.setFileDepositedDate(dateStr);

        //Current status is null, now we have an indication that it has been accepted (a nihms id)
        CopyStatus status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, null);
        assertEquals(CopyStatus.ACCEPTED, status);

        //status has gone out of alignment with PASS - PASS status is saying complete. This should not roll back
        //the status to accepted and it should log a warning that something is attempting to take status out of
        // complete.
        status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, CopyStatus.COMPLETE);
        assertEquals(CopyStatus.COMPLETE, status);

        //it was accepted, and is still accepted
        status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, CopyStatus.ACCEPTED);
        assertEquals(CopyStatus.ACCEPTED, status);
    }

    /**
     * Checks RepositoryCopy copyStatus is STALLED when non-compliant and had prior indications of being accepted
     * (i.e. it had a NihmsId), even if it was previously complete
     */
    @Test
    public void testCalcCopyStatusStalled() {

        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        pub.setNihmsId("NIHMS12345");

        CopyStatus status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, null);
        assertEquals(CopyStatus.STALLED, status);

        //if previous status was complete, should be complete regardless
        status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, CopyStatus.COMPLETE);
        assertEquals(CopyStatus.COMPLETE, status);
    }

    /**
     * The deposit has been received and it is being reviewed, metadata added etc.
     * The presence of an initial approval or tagging date indicates in-progress status
     * Check in-progress is appropriate assigned to CopyStatus in relevant conditions
     */
    @Test
    public void testCalcCopyStatusInProgress() {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        pub.setFileDepositedDate(dateStr);
        pub.setInitialApprovalDate(dateStr);

        //last update we saw the file was accepted, now it should be in progress as there is an initial approval date
        CopyStatus status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, CopyStatus.ACCEPTED);
        assertEquals(CopyStatus.IN_PROGRESS, status);

        //status has gone out of alignment with PASS - PASS is ahead sometime. This should roll back
        //the status to received and log a warning.
        status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, CopyStatus.COMPLETE);
        assertEquals(CopyStatus.COMPLETE, status);

        //this time, the submission has been tagged since it was accepted.
        pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        pub.setFileDepositedDate(dateStr);
        pub.setInitialApprovalDate(dateStr);
        pub.setTaggingCompleteDate(dateStr);
        status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, CopyStatus.ACCEPTED);
        assertEquals(CopyStatus.IN_PROGRESS, status);
    }

    /**
     * Tests the scenarios where the NIHMS spreadsheet does not have a definitive indication
     * that the deposit has had anything done to it. Method should return null copyStatus.
     */
    @Test
    public void testCalcRepoCopyStatusNoStatusFromNihms() {

        //the file was submitted, there is nothing to indicate anything has been done with
        //it yet so status should stay the same.
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        CopyStatus status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, null);
        assertEquals(null, status);

        //PASS system says file was in-preparation, but NIHMS says non compliant...
        //presumably NIHMS submission did not take place yet hence non-compliant.
        //it shouldn't touch status NIHMS is unaware of submission yet
        pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, null);
        assertEquals(null, status);

        //PASS system says file was ready-to-submit, but NIHMS says non compliant...
        //presumably NIHMS submission did not take place yet hence non-compliant.
        //it shouldn't touch status NIHMS is unaware of submission yet
        pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        status = NihmsPublicationToSubmission.calcRepoCopyStatus(pub, CopyStatus.ACCEPTED);
        assertEquals(null, status);

    }

    private NihmsPublication newTestPub() {
        return new NihmsPublication(NihmsStatus.COMPLIANT, "123456", "AB 12345", null, null, null, null, null, null,
                                    null);
    }

}
