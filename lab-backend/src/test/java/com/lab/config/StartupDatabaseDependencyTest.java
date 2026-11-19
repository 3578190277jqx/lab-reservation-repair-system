package com.lab.config;

import com.lab.mapper.*;
import com.lab.service.*;
import com.lab.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 真正刷新Spring上下文，防止只靠直接new/Mockito测试遗漏失效的启动依赖。 */
class StartupDatabaseDependencyTest {
    @Test void userServiceStartsAfterFlywayWithoutLegacyMigratorBean() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
                .withUserConfiguration(UserServiceImpl.class)
                .withPropertyValues("spring.flyway.locations=classpath:db/startup-smoke")
                .withBean(DataSource.class, () -> new DriverManagerDataSource(
                        "jdbc:h2:mem:startup_dependency;DB_CLOSE_DELAY=-1", "sa", ""))
                .withBean(UserMapper.class, () -> mock(UserMapper.class))
                .withBean(VerificationCodeService.class, () -> mock(VerificationCodeService.class))
                .withBean(RateLimitService.class, () -> mock(RateLimitService.class))
                .withBean(LabMapper.class, () -> mock(LabMapper.class))
                .withBean(ReservationMapper.class, () -> mock(ReservationMapper.class))
                .withBean(AdminMutationExecutor.class, () -> mock(AdminMutationExecutor.class))
                .withBean(RepairOrderMapper.class, () -> mock(RepairOrderMapper.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(UserService.class);
                    String beanName = context.getBeanNamesForType(UserService.class)[0];
                    assertThat(context.getBeanFactory().getBeanDefinition(beanName).getDependsOn())
                            .contains("flywayInitializer");
                    Integer value = new JdbcTemplate(context.getBean(DataSource.class))
                            .queryForObject("SELECT marker FROM application_startup_probe", Integer.class);
                    assertThat(value).isEqualTo(1);
                });
    }
}
