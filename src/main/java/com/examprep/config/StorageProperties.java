package com.examprep.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Private document storage settings. Uploaded files are written to a directory that is never
 * exposed as a static web resource.
 */
@ConfigurationProperties(prefix = "examprep.storage")
public class StorageProperties {

    private Path location = Path.of("./data/uploads");
    private int maxFileSizeMb = 25;

    public Path getLocation() {
        return location;
    }

    public void setLocation(Path location) {
        this.location = location;
    }

    public int getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(int maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public long maxFileSizeBytes() {
        return (long) maxFileSizeMb * 1024L * 1024L;
    }
}
