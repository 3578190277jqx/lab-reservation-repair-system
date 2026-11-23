package com.lab.config;

import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.FileSystemResource;

import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 只读取版本管理中的公开模板和合成环境值，不加载私有环境配置。 */
class DockerSchemaAdoptionConfigTest {
    private static Properties yaml(String path) {
        // Spring 的 YAML 调试日志会打印整个模板；只检查目标属性，不打印其他配置。
        Logger logger = (Logger) LoggerFactory.getLogger(YamlPropertiesFactoryBean.class);
        Level previous = logger.getLevel();
        logger.setLevel(Level.OFF);
        try {
            YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
            factory.setResources(new FileSystemResource(path));
            Properties properties = factory.getObject();
            assertNotNull(properties);
            return properties;
        } finally {
            logger.setLevel(previous);
        }
    }

    private static String resolveAdoption(String syntheticValue) {
        MutablePropertySources sources = new MutablePropertySources();
        if (syntheticValue != null) {
            sources.addLast(new MapPropertySource("synthetic",
                    Collections.<String, Object>singletonMap("SCHEMA_ADOPT_EXISTING", syntheticValue)));
        }
        sources.addLast(new PropertiesPropertySource("docker-template", yaml("docker/application.yml")));
        return new PropertySourcesPropertyResolver(sources)
                .resolveRequiredPlaceholders("${app.schema.adopt-existing:false}");
    }

    @Test void dockerTemplateRequiresExplicitOptIn() {
        assertEquals("false", resolveAdoption(null));
        assertEquals("true", resolveAdoption("true"));
        assertEquals("false", resolveAdoption("false"));
    }

    @Test void bothComposeVariantsPassAnExplicitDefaultFalse() {
        for (String path : new String[] {"../docker-compose.yml", "../docker-compose.ecs.yml"}) {
            assertEquals("${SCHEMA_ADOPT_EXISTING:-false}",
                    yaml(path).getProperty("services.backend.environment.SCHEMA_ADOPT_EXISTING"), path);
        }
    }
}
