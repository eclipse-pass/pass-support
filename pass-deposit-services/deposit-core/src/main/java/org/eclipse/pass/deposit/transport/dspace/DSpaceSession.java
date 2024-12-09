/*
 * Copyright 2024 Johns Hopkins University
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
package org.eclipse.pass.deposit.transport.dspace;

import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.provider.dspace.DSpaceMetadataMapper;
import org.eclipse.pass.deposit.service.DepositUtil.DepositWorkerContext;
import org.eclipse.pass.deposit.support.dspace.DspaceDepositService;
import org.eclipse.pass.deposit.support.dspace.DspaceDepositService.AuthContext;
import org.eclipse.pass.deposit.transport.TransportResponse;
import org.eclipse.pass.deposit.transport.TransportSession;
import org.eclipse.pass.support.client.PassClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In order to do a deposit to DSpace, first a workspace item is created with the files.
 * Then the workspace item is patched with the correct metadata.
 * Finally a workflow item is created referencing the workspace item in order to trigger submission.
 */
class DSpaceSession implements TransportSession {
    private static final Logger LOG = LoggerFactory.getLogger(DSpaceSession.class);

    private final DspaceDepositService dspaceDepositService;
    private final DSpaceMetadataMapper dspaceMetadataMapper;
    private final PassClient passClient;

    public DSpaceSession(DspaceDepositService dspaceDepositService, DSpaceMetadataMapper dspaceMetadataMapper,
            PassClient passClient) {
        this.dspaceDepositService = dspaceDepositService;
        this.dspaceMetadataMapper = dspaceMetadataMapper;
        this.passClient = passClient;
    }

    @Override
    public TransportResponse send(PackageStream packageStream, Map<String, String> metadata, DepositWorkerContext dc) {
        try {
            DepositSubmission depositSubmission = packageStream.getDepositSubmission();

            LOG.warn("Processing Dspace Deposit for Submission: {}", depositSubmission.getId());

            AuthContext authContext = dspaceDepositService.authenticate();

            String patchJson = dspaceMetadataMapper.patchWorkspaceItem(depositSubmission);
            String workspaceItemJson = dspaceDepositService.createWorkspaceItem(
                    packageStream.getCustodialContent(), authContext);

            LOG.debug("Created WorkspaceItem: {}", workspaceItemJson);

            // TODO Set workspace item id on Deposit.depositStatusRef so can check if it already exists.
            // TODO Then check metadata to see if it needs to be patched.

            int workspaceItemId = JsonPath.parse(workspaceItemJson).read("$._embedded.workspaceitems[0].id");
            String itemUuid = JsonPath.parse(workspaceItemJson).read(
                    "$._embedded.workspaceitems[0]._embedded.item.uuid");

            LOG.debug("Patching WorkspaceItem to add metadata {}", patchJson);
            dspaceDepositService.patchWorkspaceItem(workspaceItemId, patchJson, authContext);

            LOG.debug("Creating WorkflowItem for WorkspaceItem {}", workspaceItemId);
            dspaceDepositService.createWorkflowItem(workspaceItemId, authContext);

            String accessUrl = dspaceDepositService.createAccessUrlFromItemUuid(itemUuid);

            // TODO 422 indicates validation errors. Should mark the deposit as failed and not retry.

            LOG.warn("Completed DSpace Deposit for Submission: {}, accessUrl: {}",
                    depositSubmission.getId(), accessUrl);
            return new DspaceResponse(true, accessUrl);
        } catch (Exception e) {
            LOG.error("Error depositing into DSpace", e);
            return new DspaceResponse(false, null, e);
        }
    }

    @Override
    public boolean closed() {
        return true;
    }

    @Override
    public void close() {
        // no-op resources are closed with try-with-resources
    }
}
