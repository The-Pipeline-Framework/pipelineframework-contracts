package org.pipelineframework.connector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import org.pipelineframework.config.boundary.PipelineHttpPayloadBoundaryConfig;
import org.pipelineframework.config.boundary.PipelineObjectPublishConfig;
import org.pipelineframework.config.boundary.PipelineObjectSourceConfig;
import org.pipelineframework.objectpublish.ObjectTargetProvider;
import org.pipelineframework.objectpublish.ObjectWriteCloseRequest;
import org.pipelineframework.objectpublish.ObjectWriteOpenRequest;
import org.pipelineframework.objectpublish.ObjectWriteResult;
import org.pipelineframework.objectpublish.ObjectWriteSession;
import org.pipelineframework.repository.PayloadReference;

/** Bounded transfer engine used by generated HTTP payload adapters. */
public final class OwnedPayloadTransfer {
    public static final String OWNER_TENANT = "tpf.payload.owner.tenant";
    public static final String OWNER_SCOPE = "tpf.payload.owner.scope";
    public static final String OWNER_BINDING = "tpf.payload.owner.binding";
    public static final String OWNER_ORIGIN = "tpf.payload.owner.origin-sha256";
    private static final int CHUNK_BYTES = 64 * 1024;
    private final ConnectorBindingRegistry bindings;
    private final ConnectorRuntimeContext runtimeContext;
    private final PayloadBoundaryAuthorizer authorizer;

    public OwnedPayloadTransfer(ConnectorBindingRegistry bindings, ConnectorRuntimeContext runtimeContext,
                                PayloadBoundaryAuthorizer authorizer) {
        this.bindings = Objects.requireNonNull(bindings);
        this.runtimeContext = Objects.requireNonNull(runtimeContext);
        this.authorizer = Objects.requireNonNull(authorizer);
    }

    /** A retry is a fresh upload with a fresh object key; no pipeline admission occurs here. */
    public PayloadReference upload(PipelineHttpPayloadBoundaryConfig boundary, PipelineObjectPublishConfig target,
                                   String principal, String tenant, String scope, String mediaType, InputStream body)
        throws IOException {
        return upload(boundary, target, principal, tenant, scope, mediaType, body, () -> false);
    }

    /** Cancels and aborts the provider session when the transport reports a disconnected request. */
    public PayloadReference upload(PipelineHttpPayloadBoundaryConfig boundary, PipelineObjectPublishConfig target,
                                   String principal, String tenant, String scope, String mediaType, InputStream body,
                                   BooleanSupplier cancelled) throws IOException {
        requireDirection(boundary, PipelineHttpPayloadBoundaryConfig.Direction.UPLOAD);
        Objects.requireNonNull(body, "upload body must not be null");
        Objects.requireNonNull(cancelled, "upload cancellation signal must not be null");
        requireMediaType(boundary, mediaType);
        String normalizedMediaType = mediaType.toLowerCase(Locale.ROOT);
        PayloadBoundaryOwner owner = authorize(boundary, PayloadBoundaryAuthorizationRequest.Action.UPLOAD,
            principal, tenant, scope, Optional.empty());
        ConnectorBindingName binding = ConnectorBindingName.of(target.binding().orElseThrow(() ->
            new IllegalStateException("owned upload target requires a connector binding")));
        bindings.activate(binding, runtimeContext).toCompletableFuture().join();
        ObjectTargetProvider provider = (ObjectTargetProvider) bindings.requireOperation(
            binding, target.provider(), ConnectorOperationKind.OBJECT_TARGET, 1);
        ConnectorPayloadOrigin sourceOrigin = bindings.objectSourceOrigin(binding, target.provider(), 1);
        Map<String, String> metadata = Map.of(OWNER_TENANT, owner.tenantId(), OWNER_SCOPE, owner.scopeId(),
            OWNER_BINDING, binding.value(), OWNER_ORIGIN, originDigest(sourceOrigin));
        String key = "uploads/" + UUID.randomUUID();
        ObjectWriteSession session = provider.open(new ObjectWriteOpenRequest(
            target.name(), target, key, normalizedMediaType, metadata, key)).toCompletableFuture().join();
        try {
            MessageDigest digest = sha256();
            byte[] buffer = new byte[CHUNK_BYTES];
            long bytes = 0;
            int count;
            while (true) {
                requireActiveUpload(cancelled);
                count = body.read(buffer);
                if (count == -1) {
                    break;
                }
                if (count == 0) {
                    continue;
                }
                if (count > boundary.maxBytes() - bytes) {
                    throw new IllegalArgumentException("upload exceeds configured maximum bytes");
                }
                digest.update(buffer, 0, count);
                session.write(ByteBuffer.wrap(buffer, 0, count)).toCompletableFuture().join();
                bytes += count;
            }
            requireActiveUpload(cancelled);
            String checksum = HexFormat.of().formatHex(digest.digest());
            ObjectWriteResult result = session.close(new ObjectWriteCloseRequest(bytes, checksum, metadata))
                .toCompletableFuture().join();
            PayloadReference issued = result.reference();
            if (issued == null || result.bytes() != bytes || issued.sizeBytes() != bytes
                || !normalizedMediaType.equals(issued.contentType().toLowerCase(Locale.ROOT))
                || !checksum.equals(issued.checksum())
                || !owner.tenantId().equals(issued.metadata().get(OWNER_TENANT))
                || !owner.scopeId().equals(issued.metadata().get(OWNER_SCOPE))
                || !binding.value().equals(issued.metadata().get(OWNER_BINDING))
                || !originDigest(sourceOrigin).equals(issued.metadata().get(OWNER_ORIGIN))) {
                throw new IllegalStateException("object target returned inconsistent owned payload metadata");
            }
            return bindings.ownPayloadReference(binding, target.provider(), 1, issued);
        } catch (Throwable failure) {
            try {
                session.abort(failure).toCompletableFuture().join();
            } catch (Throwable abortFailure) {
                if (abortFailure != failure) {
                    failure.addSuppressed(abortFailure);
                }
            }
            throw failure;
        }
    }

    /** Authorisation and binding checks complete before the provider stream is opened. */
    public void download(PipelineHttpPayloadBoundaryConfig boundary, PipelineObjectSourceConfig source,
                         String principal, String tenant, String scope, PayloadReference reference,
                         OutputStream response) throws IOException {
        try (DownloadLease lease = openDownload(boundary, source, principal, tenant, scope, reference)) {
            lease.writeTo(response);
        }
    }

    /** Opens only after authorisation and provider capability verification. */
    public DownloadLease openDownload(PipelineHttpPayloadBoundaryConfig boundary, PipelineObjectSourceConfig source,
                                      String principal, String tenant, String scope, PayloadReference reference) {
        checkDownload(boundary, source, principal, tenant, scope, reference);
        try {
            ConnectorBindingName binding = ConnectorBindingName.of(source.binding().orElseThrow());
            bindings.activate(binding, runtimeContext).toCompletableFuture().join();
            return new DownloadLease(bindings.openRead(reference).toCompletableFuture().join(),
                reference.sizeBytes(), reference.checksum());
        } catch (RuntimeException failure) {
            throw new SecurityException("payload is unavailable or provenance is invalid", failure);
        }
    }

    public static final class DownloadLease implements AutoCloseable {
        private final ObjectReadSession session;
        private final long expectedBytes;
        private final String expectedChecksum;
        DownloadLease(ObjectReadSession session, long expectedBytes, String expectedChecksum) {
            this.session = Objects.requireNonNull(session);
            this.expectedBytes = expectedBytes;
            this.expectedChecksum = expectedChecksum;
        }

        public void writeTo(OutputStream response) throws IOException {
            Objects.requireNonNull(response, "response stream must not be null");
            long bytes = 0;
            MessageDigest digest = expectedChecksum == null ? null : sha256();
            while (true) {
                Optional<ByteBuffer> next = session.read(CHUNK_BYTES).toCompletableFuture().join();
                if (next.isEmpty()) {
                    break;
                }
                ByteBuffer chunk = next.orElseThrow();
                int count = chunk.remaining();
                if (count == 0 || count > CHUNK_BYTES || count > expectedBytes - bytes) {
                    throw new IllegalStateException("provider read exceeded reference length or demand");
                }
                byte[] copy = new byte[count];
                chunk.get(copy);
                if (digest != null) {
                    digest.update(copy);
                }
                response.write(copy);
                bytes += count;
            }
            if (bytes != expectedBytes) {
                throw new IllegalStateException("provider read length differs from reference");
            }
            if (digest != null && !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedChecksum)) {
                throw new IllegalStateException("provider read checksum differs from reference");
            }
        }

        @Override
        public void close() {
            session.close();
        }
    }

    /** Checks access before an HTTP response is committed. */
    public void checkDownload(PipelineHttpPayloadBoundaryConfig boundary, PipelineObjectSourceConfig source,
                              String principal, String tenant, String scope, PayloadReference reference) {
        requireDirection(boundary, PipelineHttpPayloadBoundaryConfig.Direction.DOWNLOAD);
        Objects.requireNonNull(reference, "payload reference must not be null");
        PayloadBoundaryOwner owner = authorize(boundary, PayloadBoundaryAuthorizationRequest.Action.DOWNLOAD,
            principal, tenant, scope, Optional.of(reference));
        if (!owner.tenantId().equals(reference.metadata().get(OWNER_TENANT))
            || !owner.scopeId().equals(reference.metadata().get(OWNER_SCOPE))) {
            throw new SecurityException("payload ownership mismatch");
        }
        String expiry = reference.metadata().get("tpf.payload.expiresAt");
        if (expiry != null) {
            try {
                if (!java.time.Instant.parse(expiry).isAfter(runtimeContext.clock().instant())) {
                    throw new SecurityException("payload reference has expired");
                }
            } catch (java.time.format.DateTimeParseException failure) {
                throw new SecurityException("payload reference expiry is invalid", failure);
            }
        }
        requireMediaType(boundary, reference.contentType());
        ConnectorBindingName binding = ConnectorBindingName.of(source.binding().orElseThrow(() ->
            new IllegalStateException("owned download source requires a connector binding")));
        ConnectorPayloadOrigin origin = reference.connectorOrigin().orElseThrow(() ->
            new SecurityException("payload is not connector-owned"));
        if (!binding.equals(origin.bindingName())
            || !binding.value().equals(reference.metadata().get(OWNER_BINDING))
            || !originDigest(origin).equals(reference.metadata().get(OWNER_ORIGIN))
            || !source.provider().equalsIgnoreCase(origin.operation().operationId())
            || origin.operation().majorVersion() != 1
            || !source.provider().equalsIgnoreCase(reference.provider())) {
            throw new SecurityException("payload provenance does not match the download source");
        }
    }

    private PayloadBoundaryOwner authorize(PipelineHttpPayloadBoundaryConfig boundary,
                                           PayloadBoundaryAuthorizationRequest.Action action,
                                           String principal, String tenant, String scope,
                                           Optional<PayloadReference> reference) {
        if (principal == null || principal.isBlank()) {
            throw new SecurityException("authenticated principal required");
        }
        if (tenant == null || tenant.isBlank() || scope == null || scope.isBlank()) {
            throw new SecurityException("payload owner identity required");
        }
        return Objects.requireNonNull(authorizer.authorize(new PayloadBoundaryAuthorizationRequest(
            action, boundary.name(), boundary.authorizationScope(), principal, tenant, scope, reference)),
            "payload authorizer must return an owner");
    }

    private static void requireDirection(PipelineHttpPayloadBoundaryConfig boundary,
                                         PipelineHttpPayloadBoundaryConfig.Direction expected) {
        if (boundary.direction() != expected) {
            throw new IllegalArgumentException("payload boundary direction mismatch");
        }
    }

    private static void requireMediaType(PipelineHttpPayloadBoundaryConfig boundary, String mediaType) {
        if (mediaType == null || !boundary.contentTypes().contains(mediaType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("payload media type is not allowed");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static void requireActiveUpload(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("payload upload request was cancelled");
        }
    }

    private static String originDigest(ConnectorPayloadOrigin origin) {
        MessageDigest digest = sha256();
        digestField(digest, origin.bindingName().value());
        digestField(digest, origin.operation().providerId().value());
        digestField(digest, origin.operation().operationId());
        digestField(digest, origin.operation().kind().value());
        digestField(digest, Integer.toString(origin.operation().majorVersion()));
        digestField(digest, Integer.toString(origin.providerMajorVersion()));
        if (origin.configuration().isPresent()) {
            ConnectorConfigurationSnapshot configuration = origin.configuration().orElseThrow();
            digestField(digest, configuration.schemaId());
            digestField(digest, Integer.toString(configuration.schemaVersion()));
            digestField(digest, configuration.digest());
            digestField(digest, Integer.toString(configuration.connectionReferences().size()));
            configuration.connectionReferences().forEach(ref -> digestField(digest, ref.value()));
        } else {
            digestField(digest, "no-configuration");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void digestField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
