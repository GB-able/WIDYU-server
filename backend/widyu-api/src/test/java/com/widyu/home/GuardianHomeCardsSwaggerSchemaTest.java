package com.widyu.home;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.home.dto.response.GuardianHomeCardsResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("보호자 홈 카드 Swagger 스키마 테스트")
class GuardianHomeCardsSwaggerSchemaTest {

    @Test
    @DisplayName("보호자 홈의 약 복용과 건강 일정 스키마가 시니어 홈 스키마와 구분된다")
    void 보호자_홈의_약복용과_건강일정_스키마가_시니어_홈_스키마와_구분된다() {
        // when
        ResolvedSchema resolvedSchema = ModelConverters.getInstance().resolveAsResolvedSchema(
                new AnnotatedType(GuardianHomeCardsResponse.class).resolveAsRef(false)
        );

        // then
        assertThat(resolvedSchema.referencedSchemas)
                .containsKeys("GuardianHomeMedicineInfo", "GuardianHomeHealthScheduleInfo");
    }
}
