package com.widyu.goal.addressbookmark.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.widyu.global.error.BusinessException;
import com.widyu.goal.addressbookmark.client.KakaoGeocodingClient;
import com.widyu.goal.addressbookmark.dto.external.KakaoGeocodingResponse;
import com.widyu.goal.addressbookmark.dto.response.GeocodingResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeocodingService 단위 테스트")
class GeocodingServiceTest {

    @Mock private KakaoGeocodingClient kakaoGeocodingClient;

    @InjectMocks
    private GeocodingService geocodingService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(geocodingService, "apiKey", "test-client-id");
    }

    @Test
    @DisplayName("주소를 좌표로 변환하면 Kakao x가 longitude, y가 latitude로 반환된다")
    void 주소_좌표_변환_x는_longitude_y는_latitude() {
        // given
        KakaoGeocodingResponse response = new KakaoGeocodingResponse(
                List.of(new KakaoGeocodingResponse.Document("126.9783882", "37.5666103"))
        );
        given(kakaoGeocodingClient.geocode("KakaoAK test-client-id", "서울특별시 마포구 성암로 301")).willReturn(response);

        // when
        GeocodingResponse result = geocodingService.geocode("서울특별시 마포구 성암로 301");

        // then
        assertThat(result.latitude()).isEqualTo(37.5666103);
        assertThat(result.longitude()).isEqualTo(126.9783882);
    }

    @Test
    @DisplayName("검색 결과가 없으면 BusinessException이 발생한다")
    void 검색_결과_없으면_예외가_발생한다() {
        // given
        given(kakaoGeocodingClient.geocode("KakaoAK test-client-id","존재하지않는주소")).willReturn(
                new KakaoGeocodingResponse(List.of())
        );

        // when & then
        assertThatThrownBy(() -> geocodingService.geocode("존재하지않는주소"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("documents가 null이면 BusinessException이 발생한다")
    void documents가_null이면_예외가_발생한다() {
        // given
        given(kakaoGeocodingClient.geocode("KakaoAK test-client-id","null주소")).willReturn(
                new KakaoGeocodingResponse(null)
        );

        // when & then
        assertThatThrownBy(() -> geocodingService.geocode("null주소"))
                .isInstanceOf(BusinessException.class);
    }
}
