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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.provider.dspace.DSpaceMetadataMapper;
import org.eclipse.pass.deposit.support.dspace.DSpaceDepositService;
import org.eclipse.pass.deposit.support.dspace.DSpaceDepositService.AuthContext;
import org.eclipse.pass.deposit.transport.TransportResponse;
import org.eclipse.pass.deposit.transport.TransportSession;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.Deposit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In order to do a deposit to DSpace, first a workspace item is created with the files.
 * Then the workspace item is patched with the correct metadata.
 * Finally a workflow item is created referencing the workspace item in order to trigger submission.
 */
class DSpaceSession implements TransportSession {
    private static final String DEPOSIT_STATUS_REF_PREFIX = "wsi:";
    private static final Logger LOG = LoggerFactory.getLogger(DSpaceSession.class);

    private final DSpaceDepositService dspaceDepositService;
    private final DSpaceMetadataMapper dspaceMetadataMapper;
    private final PassClient passClient;

    public DSpaceSession(DSpaceDepositService dspaceDepositService, DSpaceMetadataMapper dspaceMetadataMapper,
            PassClient passClient) {
        this.dspaceDepositService = dspaceDepositService;
        this.dspaceMetadataMapper = dspaceMetadataMapper;
        this.passClient = passClient;
    }

    @Override
    public TransportResponse send(PackageStream packageStream, Map<String, String> metadata, Deposit deposit) {
        try {
            DepositSubmission depositSubmission = packageStream.getDepositSubmission();

            LOG.info("Processing Dspace Deposit for Submission: {}", depositSubmission.getId());

            AuthContext authContext = dspaceDepositService.authenticate();

            // Create WorkspaceItem if it does not already exist

            int workspaceItemId = -1;
            DocumentContext workspaceItemContext = null;

            try {
                String ref = deposit.getDepositStatusRef();

                if (ref != null && ref.startsWith(DEPOSIT_STATUS_REF_PREFIX)) {
                    workspaceItemId = Integer.parseInt(ref.substring(DEPOSIT_STATUS_REF_PREFIX.length()));
                }
            } catch (NumberFormatException e) {
                // nop
            }

            if (workspaceItemId == -1) {
                workspaceItemContext = JsonPath.parse(dspaceDepositService.createWorkspaceItem(
                        packageStream.getCustodialContent(), authContext));
                workspaceItemId = workspaceItemContext.read("$._embedded.workspaceitems[0].id");

                // Use the deposit status ref to mark that the workspace item was created
                deposit.setDepositStatusRef(DEPOSIT_STATUS_REF_PREFIX + workspaceItemId);
                passClient.updateObject(deposit);

                LOG.debug("Created WorkspaceItem: {}", workspaceItemId);
            } else {
                LOG.info("DSpace WorkspaceItem already exists for Submission: {}", depositSubmission.getId());

                workspaceItemContext = JsonPath.parse(dspaceDepositService.
                        getWorkspaceItem(workspaceItemId, authContext));
            }

            // Check metadata to see if it needs to be patched.

            Map<String, Object> itemMetadata = workspaceItemContext.
                    read("$._embedded.workspaceitems[0]._embedded.item.metadata");

            if (!itemMetadata.containsKey("dc.title")) {
                String patchJson = dspaceMetadataMapper.patchWorkspaceItem(depositSubmission);

                LOG.debug("Patching WorkspaceItem to add metadata {}", patchJson);
                dspaceDepositService.patchWorkspaceItem(workspaceItemId, patchJson, authContext);
            }

            LOG.debug("Creating WorkflowItem for WorkspaceItem {}", workspaceItemId);
            dspaceDepositService.createWorkflowItem(workspaceItemId, authContext);

            // Item which should be published
            String itemUuid = workspaceItemContext.read("$._embedded.workspaceitems[0]._embedded.item.uuid");
            String accessUrl = dspaceDepositService.createAccessUrlFromItemUuid(itemUuid);

            LOG.info("Completed DSpace Deposit for Submission: {}, accessUrl: {}",
                    depositSubmission.getId(), accessUrl);
            return new DSpaceResponse(true, accessUrl);
        } catch (Exception e) {
            LOG.error("Error depositing into DSpace", e);
            return new DSpaceResponse(false, null, e);
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
