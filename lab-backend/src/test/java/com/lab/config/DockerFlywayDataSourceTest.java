package com.lab.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import javax.sql.DataSource;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

/** Boot wiring regression only. Real least-privilege MySQL migration is verified separately. */
class DockerFlywayDataSourceTest {
    @Test void dockerMigrationsUseIndependentUnpooledConnectionsWithoutBroaderDatabaseGrants() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("docker/application.yml"));
        Properties publicTemplate = yaml.getObject();
        assertNotNull(publicTemplate);
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
            .withInitializer(context -> context.getEnvironment().getPropertySources()
                .addLast(new PropertiesPropertySource("public-docker-template", publicTemplate)))
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:flyway_config_only",
                "spring.datasource.username=sa", "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver")
            .withBean("businessDataSource", DataSource.class, DruidDataSource::new)
            // Do not connect to a database in this wiring test.
            .withBean(FlywayMigrationStrategy.class, () -> flyway -> { })
            .run(context -> {
                assertNull(context.getStartupFailure());
                DataSource migrationDataSource = context.getBean(Flyway.class)
                    .getConfiguration().getDataSource();
                assertTrue(migrationDataSource instanceof SimpleDriverDataSource,
                    "Migration probing must not share Druid's fatal-error sorter");
                assertNotSame(context.getBean("businessDataSource"), migrationDataSource);
                assertEquals("jdbc:h2:mem:flyway_config_only",
                    ((SimpleDriverDataSource) migrationDataSource).getUrl());
                assertEquals("sa", ((SimpleDriverDataSource) migrationDataSource).getUsername());
            });
    }
}
