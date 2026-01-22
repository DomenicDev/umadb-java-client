package io.umadb.client;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class UmaDbContainer extends GenericContainer<UmaDbContainer> {

    public static final String DEFAULT_TAG = "latest";

    public static final String ENV_UMADB_API_KEY = "UMADB_API_KEY";
    public static final String ENV_UMADB_TLS_CERT = "UMADB_TLS_CERT";
    public static final String ENV_UMADB_TLS_KEY = "UMADB_TLS_KEY";

    public static final String TLS_CERT_CONTAINER_PATH = "/etc/secrets/server.pem";
    public static final String TLS_KEY_CONTAINER_PATH = "/etc/secrets/server.key";

    public static final DockerImageName DEFAULT_IMAGE_NAME =
            DockerImageName.parse("umadb/umadb:%s".formatted(DEFAULT_TAG));

    public static final int UMADB_GRPC_PORT = 50051;


    public UmaDbContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        addExposedPort(UMADB_GRPC_PORT);
    }

    public UmaDbContainer() {
        this(DEFAULT_IMAGE_NAME);
    }

    public UmaDbContainer withApiKey(String apiKey) {
        addEnv(ENV_UMADB_API_KEY, apiKey);
        return this;
    }

    public UmaDbContainer withTlsCert(String tlsCert) {
        addEnv(ENV_UMADB_TLS_CERT, TLS_CERT_CONTAINER_PATH);
        withCopyFileToContainer(
                MountableFile.forClasspathResource(tlsCert),
                TLS_CERT_CONTAINER_PATH
        );
        return this;
    }

    public UmaDbContainer withTlsKey(String pathToTlsKey) {
        addEnv(ENV_UMADB_TLS_KEY, TLS_KEY_CONTAINER_PATH);
        withCopyFileToContainer(
                MountableFile.forClasspathResource(pathToTlsKey),
                TLS_KEY_CONTAINER_PATH
        );
        return this;
    }

    public int getExposedGrpcPort() {
        return getMappedPort(UMADB_GRPC_PORT);
    }
}
