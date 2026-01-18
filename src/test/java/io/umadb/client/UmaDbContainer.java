package io.umadb.client;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class UmaDbContainer extends GenericContainer<UmaDbContainer> {

    public static final String DEFAULT_TAG = "latest";

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

    public int getExposedGrpcPort() {
        return getMappedPort(UMADB_GRPC_PORT);
    }
}
