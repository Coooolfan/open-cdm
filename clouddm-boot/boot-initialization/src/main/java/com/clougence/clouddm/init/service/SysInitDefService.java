package com.clougence.clouddm.init.service;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import com.clougence.clouddm.init.model.InitFieldDef;
import com.clougence.rdp.util.RdpI18nUtils;
import com.clougence.utils.JsonUtils;
import com.clougence.utils.ResourcesUtils;
import com.clougence.utils.StringUtils;
import com.clougence.utils.io.IOUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 系统初始化核心服务。
 * - 合并 init-fields.json schema + classpath 运行时配置值
 * - DB 连接自检判断系统状态
 * - 应用配置（写入 classpath properties / 执行 Flyway 迁移）
 */
@Slf4j
@Service
public class SysInitDefService {

    private static final String ALONE_CONFIG           = "alone.properties";
    private static final String DEFAULT_ALONE_CONFIG   = "default_alone.properties";
    private static final String CONSOLE_CONFIG         = "console.properties";
    private static final String DEFAULT_CONSOLE_CONFIG = "default_console.properties";
    private static final String INIT_FIELDS_JSON       = "config/init-fields.json";
    private List<InitFieldDef>  fieldDefsCache;

    @PostConstruct
    public void init() throws IOException {
        ObjectMapper mapper = JsonUtils.defaultObjectMapper();
        String json = IOUtils.toString(ResourcesUtils.getResourceAsStream(INIT_FIELDS_JSON), StandardCharsets.UTF_8);
        Map<String, Object> root = mapper.readValue(json, Map.class);
        List<Map<String, Object>> fields = (List<Map<String, Object>>) root.get("fields");

        List<InitFieldDef> defs = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            InitFieldDef def = new InitFieldDef();
            def.setPropertyKey((String) f.get("propertyKey"));
            def.setCategory((String) f.get("category"));
            def.setInputType((String) f.get("inputType"));
            def.setRequired(Boolean.TRUE.equals(f.get("required")));
            String labelKey = (String) f.get("labelKey");
            String descKey = (String) f.get("descriptionKey");
            def.setLabel(labelKey != null ? RdpI18nUtils.getMessage(labelKey) : "");
            def.setDescription(descKey != null ? RdpI18nUtils.getMessage(descKey) : "");
            defs.add(def);
        }
        this.fieldDefsCache = defs;
    }

    public List<InitFieldDef> loadInitFieldDefs() {
        List<InitFieldDef> schema = getFieldDefsSchema();
        Properties runtimeProps = loadSystemProperties();

        List<InitFieldDef> result = new ArrayList<>();
        for (InitFieldDef def : schema) {
            InitFieldDef copy = new InitFieldDef();
            copy.setPropertyKey(def.getPropertyKey());
            copy.setCategory(def.getCategory());
            copy.setInputType(def.getInputType());
            copy.setRequired(def.isRequired());
            copy.setLabel(def.getLabel());
            copy.setDescription(def.getDescription());

            //
            String value = runtimeProps.getProperty(def.getPropertyKey());
            if (StringUtils.isNotBlank(value)) {
                copy.setDefaultValue(value);
            } else {
                copy.setDefaultValue("");
            }
            result.add(copy);
        }
        return result;
    }

    public List<InitFieldDef> getFieldDefsSchema() { return this.fieldDefsCache; }

    //

    /** 加载运行时配置（根据 app.mode 选择 alone 或 console 配置文件） */
    public Properties loadSystemProperties() {
        Properties props = new Properties();
        try {
            if (isAloneMode()) {
                loadInto(props, DEFAULT_ALONE_CONFIG);
                loadInto(props, ALONE_CONFIG);
            } else {
                loadInto(props, DEFAULT_CONSOLE_CONFIG);
                loadInto(props, CONSOLE_CONFIG);
            }
        } catch (Exception e) {
            log.error("[SysInitService] Failed to load runtime properties", e);
        }
        return props;
    }

    private void loadInto(Properties props, String resourcePath) throws IOException {
        Map<String, String> map = ResourcesUtils.getProperty(resourcePath);
        if (map != null) {
            props.putAll(map);
        }
    }

    /** 读取原始配置文件内容（供高级选项编辑）  */
    public String readRawConfigFile() throws IOException {
        String configName = isAloneMode() ? ALONE_CONFIG : CONSOLE_CONFIG;
        URL resource = ResourcesUtils.getResource(configName);
        if (resource != null && "file".equals(resource.getProtocol())) {
            try {
                return new String(Files.readAllBytes(Paths.get(resource.toURI())), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IOException("Failed to read config file: " + configName, e);
            }
        }
        // fallback: 通过 classpath stream 读取
        return IOUtils.toString(ResourcesUtils.getResourceAsStream(configName), StandardCharsets.UTF_8);
    }

    private boolean isAloneMode() { return "embedded".equals(System.getProperty("app.mode")); }
}
