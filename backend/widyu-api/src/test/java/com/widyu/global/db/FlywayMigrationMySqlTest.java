package com.widyu.global.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.global.crypto.AesGcmStringConverter;
import java.util.List;
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

    @Test
    @DisplayName("버전 파일을 모두 적용하면 native ENUM 컬럼과 enum 값 CHECK 제약이 남지 않는다")
    void 버전_파일을_모두_적용하면_native_ENUM_컬럼과_enum_값_CHECK_제약이_남지_않는다() {
        // given: enum 컬럼은 VARCHAR여야 값을 더할 때 ALTER가 필요 없다(LLD-0155)

        // when
        List<String> enumColumns = jdbc.queryForList(
                "SELECT CONCAT(table_name, '.', column_name) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND data_type = 'enum'", String.class);
        List<String> checks = jdbc.queryForList(
                "SELECT tc.constraint_name FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.check_constraints cc ON cc.constraint_schema = tc.constraint_schema "
                        + "AND cc.constraint_name = tc.constraint_name "
                        + "WHERE tc.constraint_schema = DATABASE() AND tc.constraint_type = 'CHECK' "
                        + "AND cc.check_clause LIKE '% in (%'", String.class);

        // then
        assertThat(enumColumns).isEmpty();
        assertThat(checks).isEmpty();
    }

    @Test
    @DisplayName("버전 파일을 모두 적용하면 버린 구간의 데이터 종류 칸은 기본값 없는 NOT NULL이다")
    void 버전_파일을_모두_적용하면_버린_구간의_데이터_종류_칸은_기본값_없는_NOT_NULL이다() {
        // given: 기존 행은 imu_watch로 채우고 기본값은 지운다(LLD-0166 4.1)

        // when
        List<String> column = jdbc.queryForList(
                "SELECT CONCAT(is_nullable, ':', COALESCE(column_default, 'none')) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'sensor_dropped_range' "
                        + "AND column_name = 'dropped_stream'", String.class);

        // then
        assertThat(column).containsExactly("NO:none");
    }

    @Test
    @DisplayName("버전 파일을 모두 적용하면 체류 상태의 구역 상태가 바뀐 시각 칸은 기본값이 지금인 NOT NULL이다")
    void 버전_파일을_모두_적용하면_구역_상태가_바뀐_시각_칸은_기본값이_지금인_NOT_NULL이다() {
        // given: 기존 행은 stay_started_at으로 채우고, 이전 버전 INSERT를 받도록 기본값을 지금으로 둔다(LLD-0191 8절)

        // when
        List<String> column = jdbc.queryForList(
                "SELECT CONCAT(is_nullable, ':', COALESCE(column_default, 'none')) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'senior_stay_state' "
                        + "AND column_name = 'zone_changed_at'", String.class);

        // then
        assertThat(column).containsExactly("NO:CURRENT_TIMESTAMP(6)");
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
    @EntityScan("com.widyu")
    @Import(AesGcmStringConverter.class)
    static class Config {
    }
}
