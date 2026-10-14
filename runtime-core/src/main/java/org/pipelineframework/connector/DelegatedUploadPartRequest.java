package org.pipelineframework.connector;

public record DelegatedUploadPartRequest(String token, int partNumber) { }
