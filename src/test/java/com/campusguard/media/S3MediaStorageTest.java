package com.campusguard.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The durable backend, against a real S3 server rather than a stub.
 *
 * <p>MinIO speaks the S3 API, so the assertions here are about what the protocol
 * actually does — a delete of a key that was never there succeeds, a head of a
 * missing key raises {@code NoSuchKey}, listing is lexicographic — rather than
 * about what a hand-written double was told to do. Those three behaviours are
 * exactly where this implementation could be wrong while passing a stubbed test.
 */
class S3MediaStorageTest extends MediaStorageContract {

    private static final String BUCKET = "campusguard-test";

    /**
     * S3Mock rather than MinIO, because MinIO is no longer pullable.
     *
     * <p>Its images went from Docker Hub first — a pull there answers "repository
     * does not exist" — and the pin to quay.io that replaced them now answers
     * {@code unauthorized: access to the requested resource is not authorized}.
     * CI failed on that and failed identically on a rerun, at 412 seconds each
     * time, while this machine kept passing on a copy cached weeks earlier: the
     * exact shape of a dependency that has disappeared without anybody noticing.
     *
     * <p>Still a server rather than a stub, which is the whole point of this
     * class. The assertions below are about what the S3 protocol does — a delete
     * of a key that was never there succeeds, a head of a missing key raises
     * NoSuchKey, listing is lexicographic — and a hand-written double would only
     * confirm what somebody told it to say. S3Mock implements the HTTP API, so
     * those answers still come from an implementation of the protocol rather than
     * from this repository's idea of it.
     *
     * <p>And it is checked against the real thing: {@link RealBucketMediaStorageTest}
     * runs this same contract against the bucket a deployment uses, so a place
     * where S3Mock is wrong about S3 shows up as those two disagreeing.
     */
    private static final GenericContainer<?> S3MOCK = new GenericContainer<>(
                    DockerImageName.parse("adobe/s3mock:4.7.0"))
            .withExposedPorts(9090)
            .withEnv("initialBuckets", BUCKET);

    private static S3Client client;

    private static final String PREFIX = "media/";

    private String namespace;
    private MediaStorage storage;

    @BeforeAll
    static void startServer() {
        S3MOCK.start();
        client = S3Client.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .endpointOverride(URI.create(
                        "http://" + S3MOCK.getHost() + ":" + S3MOCK.getMappedPort(9090)))
                .region(Region.of("us-east-1"))
                // S3Mock accepts any credentials; these exist because the SDK
                // refuses to sign a request without them.
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .forcePathStyle(true)
                .build();
    }

    /**
     * One container for the whole class, with each test writing under its own
     * prefix. Starting MinIO per test would triple the class's runtime for
     * isolation that a unique prefix already provides.
     */
    @BeforeEach
    void setUp() {
        namespace = UUID.randomUUID().toString().substring(0, 8) + "/";
        storage = new S3MediaStorage(client, BUCKET, PREFIX + namespace);
    }

    @Override
    protected MediaStorage storage() {
        return storage;
    }

    /**
     * A stored key is opaque above this class. One containing a slash would
     * quietly create a folder and then sit outside what a prefixed sweep can see,
     * which is the S3 equivalent of the filesystem's traversal problem.
     */
    @Test
    void refusesAKeyCarryingAPathSeparator() {
        assertThatThrownBy(() -> storage.put("nested/key", "x".getBytes(StandardCharsets.UTF_8), "image/png"))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("path separator");
    }

    /**
     * The prefix is the sweep's blast radius. If listing reached outside it, a
     * bucket shared with backups would have its backups deleted by a job that
     * could find no media row for them.
     */
    @Test
    void seesNothingOutsideItsOwnPrefix() {
        client.putObject(
                request -> request.bucket(BUCKET).key("backups/monday.dump"),
                software.amazon.awssdk.core.sync.RequestBody.fromString("not media"));
        storage.put("mine", "x".getBytes(StandardCharsets.UTF_8), "image/png");

        assertThat(storage.list(null, 50)).singleElement()
                .satisfies(object -> assertThat(object.key()).isEqualTo("mine"));
        assertThat(client.getObjectAsBytes(request -> request.bucket(BUCKET).key("backups/monday.dump"))
                .asUtf8String()).isEqualTo("not media");
    }

    /** Two prefixes in one bucket must not see each other's keys. */
    @Test
    void keepsTwoPrefixesApart() {
        MediaStorage other = new S3MediaStorage(client, BUCKET, PREFIX + "somebody-else/");
        storage.put("shared-name", "mine".getBytes(StandardCharsets.UTF_8), "image/png");

        assertThat(other.get("shared-name")).isEmpty();
        other.put("shared-name", "theirs".getBytes(StandardCharsets.UTF_8), "image/png");

        assertThat(storage.get("shared-name")).contains("mine".getBytes(StandardCharsets.UTF_8));
        assertThat(other.get("shared-name")).contains("theirs".getBytes(StandardCharsets.UTF_8));
    }
}
