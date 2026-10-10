package org.pipelineframework.config.boundary;

import java.util.List;
import java.util.Objects;

/** Pinned authoring contract for one generated HTTP payload boundary. */
public record PipelineHttpPayloadBoundaryConfig(
    String name,
    Direction direction,
    String objectName,
    String canonicalType,
    String referenceField,
    List<String> contentTypes,
    long maxBytes,
    String authorizationScope
) {
    public enum Direction { UPLOAD, DOWNLOAD }

    public PipelineHttpPayloadBoundaryConfig {
        name = require(name, "name");
        direction = Objects.requireNonNull(direction, "HTTP payload direction must not be null");
        objectName = require(objectName, "objectName");
        canonicalType = require(canonicalType, "canonicalType");
        referenceField = require(referenceField, "referenceField");
        authorizationScope = require(authorizationScope, "authorizationScope");
        contentTypes = contentTypes == null ? List.of() : contentTypes.stream()
            .map(type -> require(type, "content type"))
            .distinct().sorted().toList();
        if (contentTypes.isEmpty()) {
            throw new IllegalArgumentException("HTTP payload boundary requires contentTypes");
        }
        for (String contentType : contentTypes) {
            if (!contentType.matches("[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*/[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*")) {
                throw new IllegalArgumentException("HTTP payload content type must be a concrete media type: "
                    + contentType);
            }
        }
        if (direction == Direction.UPLOAD && maxBytes < 1) {
            throw new IllegalArgumentException("HTTP payload upload maxBytes must be positive");
        }
        if (direction == Direction.DOWNLOAD && maxBytes != 0) {
            throw new IllegalArgumentException("HTTP payload download maxBytes must be zero");
        }
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("HTTP payload " + label + " must not be blank");
        }
        return value.trim();
    }
}
