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

package org.eclipse.pass.deposit.builder.fs;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.pass.deposit.builder.InvalidModel;
import org.eclipse.pass.deposit.builder.StreamingSubmissionBuilder;
import org.eclipse.pass.deposit.builder.SubmissionBuilder;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Submission;
import org.springframework.stereotype.Component;

/**
 * Builds an instance of the Deposit Services model (i.e. a {@link DepositSubmission} from a file on a locally mounted
 * filesystem.
 * <p>
 * The file is JSON data representing a graph of PassEntity objects.  Each object must have a unique ID. The entities in
 * the JSON are linked together by their identifiers to form the graph, rooted with the Submission object.  The file
 * must contain exactly one Submission object, which is the root of the data tree for a deposit.
 * </p>
 * <p>
 * If a {@link #isUseFedora()} is {@code true}, the resources present in the local graph will be deposited to
 * the PASS repository.  This results in a new Fedora resource for each resource present in the JSON graph.  The
 * resulting {@code DepositSubmission} will be built from the Fedora resources, not the local resources from the JSON
 * graph.
 * </p>
 *
 * @author Ben Trumbore (wbt3@cornell.edu)
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class FilesystemModelBuilder extends ModelBuilder implements SubmissionBuilder, StreamingSubmissionBuilder {

    private final PassJsonFedoraAdapter passJsonFedoraAdapter;

    public FilesystemModelBuilder(PassJsonFedoraAdapter passJsonFedoraAdapter) {
        this.passJsonFedoraAdapter = passJsonFedoraAdapter;
    }

    /***
     * Build a DepositSubmission from the JSON data in named file.
     * <p>
     * Supported forms of {@code formDataUrl} include:
     * </p>
     * <ul>
     *     <li>{@code http://} or {@code https//}</li>
     *     <li>{@code file:/}</li>
     *     <li>{@code jar:/}</li>
     * </ul>
     * <p>
     * If there is no scheme present, {@code formDataUrl} is interpreted as a path to a local file containing the JSON
     * data.
     * </p>
     *
     * @param formDataUrl url containing the JSON data
     * @return a deposit submission data model
     * @throws InvalidModel if the JSON data cannot be successfully parsed into a valid submission model
     */
    @Override
    public DepositSubmission build(String formDataUrl) throws InvalidModel {
        InputStream is = null;
        try {
            URI resource = new URI(formDataUrl);

            if (resource.getScheme() == null) {
                is = new FileInputStream(formDataUrl);
            } else if (resource.getScheme().startsWith("http") ||
                       resource.getScheme().startsWith("file") ||
                       resource.getScheme().startsWith("jar")) {
                is = resource.toURL().openStream();
            } else {
                throw new InvalidModel(String.format("Unknown scheme '%s' for URL '%s'",
                                                     resource.getScheme(), formDataUrl));
            }

            return build(is, Collections.emptyMap());
        } catch (FileNotFoundException e) {
            throw new InvalidModel(String.format("Could not open the data file '%s'.", formDataUrl), e);
        } catch (IOException e) {
            throw new InvalidModel(String.format("Failed to close the data file '%s'.", formDataUrl), e);
        } catch (URISyntaxException e) {
            throw new InvalidModel(String.format("Malformed URL '%s'.", formDataUrl), e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /***
     * Build a DepositSubmission from JSON data provided in an {@code InputStream}.  This method can be used to bypass
     * the URL resolution logic in {@link #build(String)}.
     *
     * @param stream the InputStream containing the submission graph
     * @return a deposit submission data model
     * @throws InvalidModel if the JSON data cannot be successfully parsed into a valid submission model
     */
    @Override
    public DepositSubmission build(InputStream stream, Map<String, String> streamMd) throws InvalidModel, IOException {
        List<PassEntity> entities = new LinkedList<>();
        Submission submissionEntity = passJsonFedoraAdapter.jsonToFcrepo(stream, entities);
        return createDepositSubmission(submissionEntity, entities);
    }

}
