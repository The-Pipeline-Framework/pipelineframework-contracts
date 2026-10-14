package org.pipelineframework.connector;

/** Small control-plane request; payload bytes are sent to object storage separately. */
public record DelegatedUploadInitiation(String contentType, long bytes, String sha256,
                                        String idempotencyKey) { }
