package com.widyu.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("아키텍처 경계 규칙 검증")
class ArchitectureBoundaryTest {

    private static final String BASE_PACKAGE = "com.widyu";

    @Test
    @DisplayName("Controller는 Repository를 직접 의존하지 않는다")
    void 컨트롤러는_리포지토리를_직접_의존하지_않는다() throws ClassNotFoundException {
        List<Class<?>> controllerClasses = scanClasses(".*\\.controller\\..*");
        List<String> violations = new ArrayList<>();

        for (Class<?> controllerClass : controllerClasses) {
            for (Field field : controllerClass.getDeclaredFields()) {
                String fieldTypeName = field.getType().getName();
                if (fieldTypeName.contains(".repository.")) {
                    violations.add(controllerClass.getSimpleName()
                            + " → " + field.getType().getSimpleName()
                            + " (Controller가 Repository를 직접 주입받습니다)");
                }
            }
        }

        assertThat(violations)
                .as("Controller에서 Repository 직접 의존 위반 목록")
                .isEmpty();
    }

    @Test
    @DisplayName("Application Service는 Controller를 역방향 의존하지 않는다")
    void 애플리케이션_계층은_컨트롤러를_역방향_의존하지_않는다() throws ClassNotFoundException {
        List<Class<?>> applicationClasses = scanClasses(".*\\.application\\..*");
        List<String> violations = new ArrayList<>();

        for (Class<?> appClass : applicationClasses) {
            for (Field field : appClass.getDeclaredFields()) {
                String fieldTypeName = field.getType().getName();
                if (fieldTypeName.contains(".controller.")) {
                    violations.add(appClass.getSimpleName()
                            + " → " + field.getType().getSimpleName()
                            + " (Application 계층이 Controller를 역방향 참조합니다)");
                }
            }
            for (Class<?> iface : appClass.getInterfaces()) {
                if (iface.getName().contains(".controller.")) {
                    violations.add(appClass.getSimpleName()
                            + " implements " + iface.getSimpleName()
                            + " (Application 계층이 Controller 인터페이스를 구현합니다)");
                }
            }
        }

        assertThat(violations)
                .as("Application 계층에서 Controller 역방향 의존 위반 목록")
                .isEmpty();
    }

    @Test
    @DisplayName("응용·웹·DTO 패키지에 @Entity 클래스가 없다")
    void 엔티티_클래스는_응용_계층_패키지에_위치하지_않는다() throws ClassNotFoundException {
        List<Class<?>> apiLayerClasses = new ArrayList<>();
        apiLayerClasses.addAll(scanClasses(".*\\.application\\..*"));
        apiLayerClasses.addAll(scanClasses(".*\\.controller\\..*"));
        apiLayerClasses.addAll(scanClasses(".*\\.dto\\..*"));
        apiLayerClasses.addAll(scanClasses(".*\\.validator\\..*"));

        List<String> violations = new ArrayList<>();
        for (Class<?> clazz : apiLayerClasses) {
            if (clazz.isAnnotationPresent(jakarta.persistence.Entity.class)) {
                violations.add(clazz.getName()
                        + " (@Entity는 widyu-domain 모듈에만 위치해야 합니다)");
            }
        }

        assertThat(violations)
                .as("API 계층 패키지 내 @Entity 위반 목록")
                .isEmpty();
    }

    private List<Class<?>> scanClasses(String packageRegex) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(packageRegex)));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(BASE_PACKAGE)) {
            classes.add(Class.forName(bd.getBeanClassName()));
        }
        return classes;
    }
}
