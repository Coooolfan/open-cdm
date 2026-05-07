package com.clougence.drivers.factory.prepare;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.clougence.drivers.DriverPrepareProgress;
import com.clougence.drivers.DriverVersion;
import com.clougence.drivers.def.FileDef;
import com.clougence.drivers.def.ResDef;
import com.clougence.drivers.factory.ResourcePreparer;
import com.clougence.utils.CollectionUtils;
import com.clougence.utils.StringUtils;

public abstract class AbstractResourcePreparer implements ResourcePreparer {

    private static final String FILES_INDEX_NAME = "files.idx";

    protected final File       localDir;
    protected final Properties config;

    protected AbstractResourcePreparer(File localDir, Properties config){
        this.localDir = localDir;
        this.config = config != null ? config : new Properties();
    }

    @Override
    public void refresh(DriverVersion driverVersion, ResDef resDef, ClassLoader classLoader, DriverPrepareProgress progress) throws IOException {
        this.restoreFilesIndex(driverVersion, resDef);

        if (CollectionUtils.isEmpty(resDef.getFileDefList())) {
            resDef.setPrepared(true);
            return;
        }

        boolean allPrepared = true;
        for (FileDef fileDef : resDef.getFileDefList()) {
            String absolutePath = StringUtils.trimToNull(fileDef.getAbsolutePath());
            fileDef.setPrepared(absolutePath != null && new File(absolutePath).exists());
            allPrepared = allPrepared && fileDef.isPrepared();
        }
        resDef.setPrepared(allPrepared);
    }

    public final boolean restoreFilesIndex(DriverVersion driverVersion, ResDef resDef) throws IOException {
        if (resDef == null || !CollectionUtils.isEmpty(resDef.getFileDefList())) {
            return false;
        }

        List<FileDef> indexedFileDefs = this.loadFilesIndex(driverVersion, resDef);
        if (CollectionUtils.isEmpty(indexedFileDefs)) {
            return false;
        }

        resDef.setFileDefList(indexedFileDefs);
        return true;
    }

    public final void updateFilesIndex(DriverVersion driverVersion, ResDef resDef) throws IOException {
        if (driverVersion == null || resDef == null || CollectionUtils.isEmpty(resDef.getFileDefList())) {
            return;
        }

        File indexFile = this.resolveFilesIndex(driverVersion);
        if (indexFile == null) {
            return;
        }

        File parentDir = indexFile.getParentFile();
        if (parentDir != null) {
            Files.createDirectories(parentDir.toPath());
        }

        List<String> lines = new ArrayList<>();
        if (indexFile.isFile()) {
            for (String line : Files.readAllLines(indexFile.toPath(), StandardCharsets.UTF_8)) {
                FilesIndexEntry entry = FilesIndexEntry.parse(line);
                if (entry == null || !entry.matches(Long.toString(resDef.getFilesIndexId()))) {
                    String trimmed = StringUtils.trimToNull(line);
                    if (trimmed != null) {
                        lines.add(trimmed);
                    }
                }
            }
        }

        File versionDir = driverVersion.getAbsoluteDir();
        for (FileDef fileDef : resDef.getFileDefList()) {
            FilesIndexEntry entry = FilesIndexEntry.fromFileDef(Long.toString(resDef.getFilesIndexId()), versionDir, fileDef);
            if (entry != null) {
                this.addIndexLine(lines, entry.toLine());
            }
        }

        if (lines.isEmpty()) {
            return;
        }

        Files.write(indexFile.toPath(), lines, StandardCharsets.UTF_8);
    }

    protected List<FileDef> loadFilesIndex(DriverVersion driverVersion, ResDef resDef) throws IOException {
        File indexFile = this.resolveFilesIndex(driverVersion);
        if (indexFile == null || !indexFile.isFile() || resDef == null) {
            return null;
        }

        List<FileDef> fileDefs = new ArrayList<>();
        File versionDir = driverVersion != null ? driverVersion.getAbsoluteDir() : null;
        String filesIndexId = Long.toString(resDef.getFilesIndexId());
        for (String line : Files.readAllLines(indexFile.toPath(), StandardCharsets.UTF_8)) {
            FilesIndexEntry entry = FilesIndexEntry.parse(line);
            if (entry == null || !entry.matches(filesIndexId)) {
                continue;
            }
            fileDefs.add(entry.toFileDef(versionDir));
        }
        return fileDefs;
    }

    private File resolveFilesIndex(DriverVersion driverVersion) {
        if (driverVersion == null || driverVersion.getAbsoluteDir() == null) {
            return null;
        }
        return new File(driverVersion.getAbsoluteDir(), FILES_INDEX_NAME);
    }

    private void addIndexLine(List<String> lines, String line) {
        if (!lines.contains(line)) {
            lines.add(line);
        }
    }
}
