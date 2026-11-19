package com.lab.service;

import com.lab.config.QwenAiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class QwenConfigurationTest {
    @Test void sourceAndDockerConfigsBindTheSameImageVariables() {
        for (Resource resource : new Resource[]{new ClassPathResource("application.yml"), new FileSystemResource("docker/application.yml")}) {
            YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
            factory.setResources(resource);
            StandardEnvironment environment = new StandardEnvironment();
            Map<String, Object> values = new HashMap<>();
            values.put("QWEN_AI_ENABLED", "true");
            values.put("QWEN_AI_API_KEY", "fixture-key-not-real");
            values.put("QWEN_AI_MODEL", "qwen3-vl-plus");
            values.put("BAIDU_AI_ENABLED", "false");
            environment.getPropertySources().addFirst(new MapPropertySource("fixture", values));
            environment.getPropertySources().addLast(new PropertiesPropertySource("yaml", factory.getObject()));
            QwenAiProperties properties = Binder.get(environment).bind("app.ai.qwen", QwenAiProperties.class).orElse(new QwenAiProperties());
            assertTrue(properties.isEnabled(), resource.getDescription());
            assertEquals("fixture-key-not-real", properties.getApiKey());
            assertEquals("qwen3-vl-plus", properties.getModel());
            assertEquals("false", environment.getProperty("app.ai.baidu.enabled"));
            assertFalse(properties.toString().contains("fixture-key-not-real"));
        }
    }
}
