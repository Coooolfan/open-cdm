package com.clougence.drivers.def;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.clougence.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResDef {

    private String        resourceType;
    private String        coordinate;
    private boolean       prepared;
    private List<FileDef> fileDefList;

    public long getFilesIndexId() {
        String resourceTypeValue = StringUtils.trimToEmpty(this.resourceType);
        String coordinateValue = StringUtils.trimToEmpty(this.coordinate);
        byte[] source = (resourceTypeValue + "\n" + coordinateValue).getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(source).getMostSignificantBits() & Long.MAX_VALUE;
    }
}
