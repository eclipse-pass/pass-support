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
package org.eclipse.pass.deposit.assembler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;

import org.eclipse.pass.deposit.model.DepositFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * A Spring {@code Resource} paired with its {@link DepositFile}, providing access to resource metadata (e.g. file name
 * or resource location) and the {@link Resource#getInputStream() byte stream} for the resource.
 * <p>
 * Users of {@code DepositFileResource} may prefer the {@link #getDepositFile() DepositFile} for metadata rather than
 * than the metadata provided by the Spring {@code Resource}.  For example, {@link Resource#getFilename()} will return
 * a <em>URL path</em> as a resource name when the {@code Resource} is remote (i.e. implemented as {@link UrlResource})
 * vs a <em>filesystem path</em> when the resource is local (i.e. implemented as a {@link FileSystemResource}).  The
 * {@link DepositFile} associated with the {@code Resource} is independent of the {@code Resource}
 * <em>implementation</em>.  This allows the user of {@code DepositFileResource} to obtain the logical name of a
 * resource (via {@link DepositFile#getName()}) independent of its location.  The underlying Spring {@code Resource}
 * insures that the bytes for the {@code DepositFile} are available, even if the {@code Resource} requires authorization
 * to retrieve.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositFileResource implements Resource {

    private final Resource resource;

    private final DepositFile depositFile;

    /**
     * Create a new instance with the {@code resource} supplying the bytes for the {@code depositFile}.
     *
     * @param depositFile the DepositFile
     * @param resource    the underlying Resource
     */
    public DepositFileResource(DepositFile depositFile, Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Spring Resource must not be null.");
        }

        if (depositFile == null) {
            throw new IllegalArgumentException("DepositFile must not be null.");
        }

        this.resource = resource;
        this.depositFile = depositFile;
    }

    /**
     * Obtain the underlying {@code DepositFile}
     *
     * @return the {@code DepositFile}
     */
    public DepositFile getDepositFile() {
        return depositFile;
    }

    /**
     * Obtain the underlying Spring {@code Resource}.  All {@link Resource} methods on this class forward to the
     * instance returned by this method.
     *
     * @return the Spring {@code Resource}
     */
    public Resource getResource() {
        return resource;
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public boolean exists() {
        return resource.exists();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public boolean isReadable() {
        return resource.isReadable();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public boolean isOpen() {
        return resource.isOpen();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public boolean isFile() {
        return resource.isFile();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @NonNull
    @Override
    public URL getURL() throws IOException {
        return resource.getURL();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @NonNull
    @Override
    public URI getURI() throws IOException {
        return resource.getURI();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @NonNull
    @Override
    public File getFile() throws IOException {
        return resource.getFile();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @NonNull
    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        return resource.readableChannel();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public long contentLength() throws IOException {
        return resource.contentLength();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public long lastModified() throws IOException {
        return resource.lastModified();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @NonNull
    @Override
    public Resource createRelative(@NonNull String relativePath) throws IOException {
        return resource.createRelative(relativePath);
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Nullable
    @Override
    public String getFilename() {
        return resource.getFilename();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @NonNull
    @Override
    public String getDescription() {
        return resource.getDescription();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @NonNull
    @Override
    public InputStream getInputStream() throws IOException {
        return resource.getInputStream();
    }

    @Override
    public String toString() {
        return "DepositFileResource{" + "resource=" + resource + ", depositFile=" + depositFile + '}';
    }
}
