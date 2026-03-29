package com.widyu.goal.medicineschedule.scheduler;

import com.widyu.global.properties.MedicineProperties;
import com.widyu.goal.medicineschedule.application.ExternalMedicineService;
import com.widyu.goal.medicineschedule.client.MedicineApiClient;
import com.widyu.goal.medicineschedule.dto.external.MedicineApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공공 의약품 API 데이터를 자체 DB에 주기적으로 동기화
 * - 매일 새벽 3시 실행 (트래픽 없는 시간대)
 * - 100건 단위 청크 처리로 DB 부하 분산
 * - 신규 약품만 INSERT (기존 데이터 스킵)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MedicineSyncScheduler {

    private static final int CHUNK_SIZE = 100;
    private static final long API_CALL_DELAY_MS = 300; // 공공 API 부하 방지

    private final MedicineApiClient medicineApiClient;
    private final ExternalMedicineService externalMedicineService;
    private final MedicineProperties medicineProperties;

    @Scheduled(cron = "0 0 3 1 * *")
    public void syncMedicineData() {
        log.info("의약품 DB 동기화 시작");
        int page = 1;
        int totalSynced = 0;

        while (true) {
            try {
                MedicineApiResponse response = medicineApiClient.fetchAllMedicines(
                        medicineProperties.api().serviceKey(),
                        CHUNK_SIZE,
                        page,
                        "json"
                );

                if (response == null || response.body() == null) {
                    log.warn("의약품 API 응답 없음 - page: {}", page);
                    break;
                }

                List<MedicineApiResponse.MedicineItem> items = response.body().items() != null
                        ? response.body().items()
                        : (response.body().item() != null ? response.body().item() : List.of());

                if (items.isEmpty()) {
                    log.info("의약품 동기화 완료 - 마지막 페이지: {}", page);
                    break;
                }

                externalMedicineService.upsertMedicines(items);
                totalSynced += items.size();
                log.debug("의약품 동기화 진행 중 - page: {}, 누적: {}건", page, totalSynced);

                if (items.size() < CHUNK_SIZE) break; // 마지막 페이지

                page++;
                Thread.sleep(API_CALL_DELAY_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("의약품 동기화 인터럽트 - page: {}", page);
                break;
            } catch (Exception e) {
                log.error("의약품 동기화 실패 - page: {}, error: {}", page, e.getMessage(), e);
                break;
            }
        }

        log.info("의약품 DB 동기화 완료 - 총 {}건 처리", totalSynced);
    }
}
