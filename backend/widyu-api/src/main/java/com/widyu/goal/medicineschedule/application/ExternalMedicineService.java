package com.widyu.goal.medicineschedule.application;

import com.widyu.global.properties.MedicineProperties;
import com.widyu.goal.medicineschedule.client.MedicineApiClient;
import com.widyu.goal.medicineschedule.dto.external.MedicineApiResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineSearchResponse;
import com.widyu.goal.medicineschedule.repository.MedicineRepository;
import com.widyu.medicine.Medicine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalMedicineService {

    private final MedicineApiClient medicineApiClient;
    private final MedicineRepository medicineRepository;
    private final MedicineProperties medicineProperties;

    /**
     * 외부 API로 약품 검색 후 DB에 없으면 저장
     */
    @Transactional
    public MedicineSearchResponse searchAndSaveMedicines(String keyword) {
        log.info("약품 검색 시작: keyword={}", keyword);

        try {
            // 외부 API 호출 (Feign이 자동으로 URL 인코딩)
            MedicineApiResponse response = medicineApiClient.searchMedicines(
                    medicineProperties.api().serviceKey(),
                    keyword,
                    10,  // 최대 10개 결과
                    1,   // 첫 페이지
                    "json"
            );

            if (response == null || response.body() == null) {
                log.warn("외부 API 응답이 비어있음: keyword={}", keyword);
                return new MedicineSearchResponse(List.of());
            }

            // items 또는 item 중 null이 아닌 것을 가져옴
            List<MedicineApiResponse.MedicineItem> apiItems = response.body().items() != null
                    ? response.body().items()
                    : (response.body().item() != null ? response.body().item() : List.of());

            if (apiItems.isEmpty()) {
                log.warn("외부 API 검색 결과가 없음: keyword={}", keyword);
                return new MedicineSearchResponse(List.of());
            }

            // 결과를 DB에 저장하고 응답 생성
            List<MedicineSearchResponse.MedicineItem> items = apiItems.stream()
                    .map(this::findOrSaveMedicine)
                    .map(medicine -> new MedicineSearchResponse.MedicineItem(
                            medicine.getId(),
                            medicine.getItemName(),
                            medicine.getItemImage(),
                            medicine.getEfcyQesitm(),
                            medicine.getUseMethodQesitm()
                    ))
                    .collect(Collectors.toList());

            log.info("약품 검색 완료: keyword={}, 결과 수={}", keyword, items.size());
            return new MedicineSearchResponse(items);

        } catch (Exception e) {
            log.error("약품 검색 실패: keyword={}, error={}", keyword, e.getMessage(), e);
            throw new RuntimeException("약품 검색 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * itemSeq로 DB에서 찾거나 새로 저장
     */
    private Medicine findOrSaveMedicine(MedicineApiResponse.MedicineItem apiItem) {
        return medicineRepository.findByItemSeq(apiItem.itemSeq())
                .orElseGet(() -> {
                    Medicine newMedicine = Medicine.create(
                            apiItem.itemSeq(),
                            apiItem.itemName(),
                            apiItem.entpName(),
                            apiItem.itemImage(),
                            apiItem.useMethodQesitm(),
                            apiItem.efcyQesitm()
                    );
                    Medicine saved = medicineRepository.save(newMedicine);
                    log.info("새로운 약품 저장: itemSeq={}, itemName={}", saved.getItemSeq(), saved.getItemName());
                    return saved;
                });
    }
}
