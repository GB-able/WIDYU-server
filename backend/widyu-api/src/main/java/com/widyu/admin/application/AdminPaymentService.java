package com.widyu.admin.application;

import com.widyu.admin.dto.response.AdminPageResponse;
import com.widyu.admin.dto.response.AdminPaymentResponse;
import com.widyu.pay.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminPaymentResponse> getPaymentPage(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return AdminPageResponse.from(
                paymentRepository.findAllForAdmin(pageRequest).map(AdminPaymentResponse::from)
        );
    }
}
