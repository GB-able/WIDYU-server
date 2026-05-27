package com.widyu.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SeniorSignUpRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 최대 50자입니다.")
        String name,

        @NotNull(message = "생년월일은 필수입니다.")
        @Past(message = "생년월일은 오늘 이전 날짜여야 합니다.")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate birthDate,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(
                regexp = "^01[016789][0-9]{7,8}$",
                message = "전화번호는 하이픈 없이 10~11자리 숫자여야 합니다. (예: 01012345678)"
        )
        String phoneNumber,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 200, message = "주소는 최대 200자입니다.")
        String address,

        @Size(max = 200, message = "상세주소는 최대 200자입니다.")
        String detailAddress,

        @NotBlank(message = "초대코드는 필수입니다.")
        @Pattern(regexp = "^\\d{7}$", message = "초대코드는 숫자 7자리여야 합니다.")
        String inviteCode
) { }
