package com.widyu.goal.medicineschedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.medicineschedule.dto.response.MedicationProofResponse;
import com.widyu.member.Member;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicationProofService 업로드 단위 테스트")
class MedicationProofServiceTest {

    @Mock private MedicationProofTransactionService transactionService;
    @Mock private MemberUtil memberUtil;
    @Mock private S3Service s3Service;

    @InjectMocks private MedicationProofService medicationProofService;

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    @Test
    @DisplayName("클라이언트가 보낸 이름과 Content-Type이 아니라 파일 시그니처로 저장 확장자와 형식을 정한다")
    void 파일_시그니처로_저장_확장자와_형식을_정한다() {
        // given
        Member member = mock(Member.class);
        MultipartFile image = new MockMultipartFile("image", "proof.html", "text/html", JPEG_BYTES);
        given(member.getId()).willReturn(10L);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(s3Service.generateFilePath("medication-proof/10", "proof.jpg"))
                .willReturn("medication-proof/10/a.jpg");
        given(s3Service.uploadFile(image, "medication-proof/10/a.jpg", "image/jpeg"))
                .willReturn("https://cdn/a.jpg");
        given(transactionService.verifyMedication(10L, 1L, List.of("https://cdn/a.jpg")))
                .willReturn(MedicationProofResponse.of(10L, 10L));

        // when
        MedicationProofResponse response = medicationProofService.verifyMedication(1L, List.of(image));

        // then
        assertThat(response.earnedPoints()).isEqualTo(10L);
    }

    @Test
    @DisplayName("한 장이라도 SVG면 INVALID_FILE_TYPE 예외가 발생하고 어떤 이미지도 올리지 않는다")
    void 한_장이라도_SVG면_어떤_이미지도_올리지_않는다() {
        // given
        Member member = mock(Member.class);
        MultipartFile jpeg = new MockMultipartFile("image", "proof.jpg", "image/jpeg", JPEG_BYTES);
        MultipartFile svg = new MockMultipartFile(
                "image", "proof.svg", "image/svg+xml", "<svg onload=\"alert(1)\"/>".getBytes());
        given(member.getId()).willReturn(10L);
        given(memberUtil.getCurrentMember()).willReturn(member);

        // when & then
        assertThatThrownBy(() -> medicationProofService.verifyMedication(1L, List.of(jpeg, svg)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE_TYPE);
        then(s3Service).shouldHaveNoInteractions();
        then(transactionService).should(never()).verifyMedication(any(), any(), any());
    }

    @Test
    @DisplayName("업로드 뒤 최종 검증이 실패하면 업로드한 이미지를 삭제한다")
    void 업로드_뒤_최종_검증이_실패하면_업로드한_이미지를_삭제한다() {
        // given
        Member member = mock(Member.class);
        MultipartFile image = new MockMultipartFile("image", "proof.jpg", "image/jpeg", JPEG_BYTES);
        given(member.getId()).willReturn(10L);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(s3Service.generateFilePath("medication-proof/10", "proof.jpg"))
                .willReturn("medication-proof/10/proof.jpg");
        given(s3Service.uploadFile(image, "medication-proof/10/proof.jpg", "image/jpeg"))
                .willReturn("https://cdn/proof.jpg");
        given(transactionService.verifyMedication(eq(10L), eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.BAD_REQUEST, "중복 인증"));

        // when & then
        assertThatThrownBy(() -> medicationProofService.verifyMedication(1L, List.of(image)))
                .isInstanceOf(BusinessException.class);
        then(s3Service).should().deleteFile("https://cdn/proof.jpg");
    }

    @Test
    @DisplayName("업로드 뒤 트랜잭션 서비스로 인증을 요청한다")
    void 업로드_뒤_트랜잭션_서비스로_인증을_요청한다() {
        // given
        Member member = mock(Member.class);
        given(member.getId()).willReturn(10L);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(transactionService.verifyMedication(10L, 1L, List.of()))
                .willReturn(MedicationProofResponse.of(0L, 10L));

        // when
        medicationProofService.verifyMedication(1L, List.of());

        // then
        then(transactionService).should().verifyMedication(10L, 1L, List.of());
    }

    @Test
    @DisplayName("사전 검증에 실패하면 이미지를 업로드하지 않는다")
    void 사전_검증에_실패하면_이미지를_업로드하지_않는다() {
        // given
        Member member = mock(Member.class);
        MultipartFile image = mock(MultipartFile.class);
        given(member.getId()).willReturn(10L);
        given(memberUtil.getCurrentMember()).willReturn(member);
        org.mockito.BDDMockito.willThrow(new BusinessException(ErrorCode.BAD_REQUEST, "중복 인증"))
                .given(transactionService).validateBeforeUpload(10L, 1L);

        // when & then
        assertThatThrownBy(() -> medicationProofService.verifyMedication(1L, List.of(image)))
                .isInstanceOf(BusinessException.class);
        then(s3Service).shouldHaveNoInteractions();
    }
}
