package com.widyu.global.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.global.crypto.AesGcmStringConverter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 빈 MySQL에 db/migration 버전 파일을 모두 적용한 뒤 Hibernate 스키마 검증(validate)이 통과하는지 확인한다(LLD-0154).
 * 운영 기동과 같은 순서다. Flyway가 먼저 버전 파일을 적용하고, 엔티티 매니저가 뜨면서 엔티티와 테이블을 대조한다.
 * 어긋나면 컨텍스트가 뜨지 않아 테스트가 실패한다.
 *
 * <p>실행: {@code SCHEMA_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/schema_check_<name> DB_USERNAME=root DB_PASSWORD=
 * ./gradlew :backend:widyu-api:test --tests 'com.widyu.global.db.FlywayMigrationMySqlTest'}
 */
@SpringBootTest(classes = FlywayMigrationMySqlTest.Config.class, properties = {
        "spring.config.location=optional:classpath:/schema-check-empty.properties",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "widyu.encryption.aes-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@EnabledIfEnvironmentVariable(named = "SCHEMA_MYSQL_URL", matches = ".+")
@Tag("schema-mysql")
class FlywayMigrationMySqlTest {

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("SCHEMA_MYSQL_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/schema_check_[a-zA-Z0-9_]+(\\?.*)?")) {
            throw new IllegalArgumentException("Requires loopback and a disposable schema_check_ database");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv("DB_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
    }

    @Test
    @DisplayName("빈 DB에 버전 파일을 모두 적용하면 엔티티 스키마 검증을 통과한다")
    void 빈_DB에_버전_파일을_모두_적용하면_엔티티_스키마_검증을_통과한다() {
        // given: 컨텍스트 기동 중에 Flyway 적용과 Hibernate validate가 끝났다

        // when
        int failed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0", Integer.class);

        // then
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current()).isNotNull();
        assertThat(failed).isZero();
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
    @EntityScan("com.widyu")
    @Import(AesGcmStringConverter.class)
    static class Config {
    }
}
