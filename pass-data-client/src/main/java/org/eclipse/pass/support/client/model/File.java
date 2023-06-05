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

import java.net.URI;
import java.util.Objects;

import jsonapi.Id;
import jsonapi.Resource;
import jsonapi.ToOne;

/**
 * Files are associated with a Submissions to be used to form Deposits into Repositories
 *
 * @author Karen Hanson
 */

@Resource(type = "file")
public class File implements PassEntity {
    /**
     * Unique id for the resource.
     */
    @Id
    private String id;

    /**
     * Name of file, defaults to filesystem.name
     */
    private String name;

    /**
     * URI to the bytestream that Deposit services will use to retrieve the bytestream for Deposit
     */
    private URI uri;

    /**
     * Description of file provided by User
     */
    private String description;

    /**
     * Role of the file e.g. manuscript, supplemental
     */
    private FileRole fileRole;

    /**
     * Mime-type of file
     */
    private String mimeType;

    /**
     * The Submission the File is a part of
     */
    @ToOne(name = "submission")
    private Submission submission;

    /**
     * File constructor
     */
    public File() {
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param file the file to copy
     */
    public File(File file) {
        this.id = file.id;
        this.name = file.name;
        this.uri = file.uri;
        this.description = file.description;
        this.fileRole = file.fileRole;
        this.mimeType = file.mimeType;
        this.submission = file.submission;
    }

    /**
     * Constructor that sets id.
     *
     * @param id identifier
     */
    public File(String id) {
        this.id = id;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the fileRole
     */
    public FileRole getFileRole() {
        return fileRole;
    }

    /**
     * @param fileRole the fileRole to set
     */
    public void setFileRole(FileRole fileRole) {
        this.fileRole = fileRole;
    }

    /**
     * @return the mimeType
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * @param mimeType the mimeType to set
     */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * @return the submission
     */
    public Submission getSubmission() {
        return submission;
    }

    /**
     * @param submission the submission to set
     */
    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        File other = (File) obj;
        return Objects.equals(description, other.description) && fileRole == other.fileRole
                && Objects.equals(id, other.id) && Objects.equals(mimeType, other.mimeType)
                && Objects.equals(name, other.name) && Objects.equals(submission, other.submission)
                && Objects.equals(uri, other.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "File [id=" + id + ", name=" + name + ", uri=" + uri + ", description=" + description + ", fileRole="
                + fileRole + ", mimeType=" + mimeType + ", submission=" + submission + "]";
    }
}
