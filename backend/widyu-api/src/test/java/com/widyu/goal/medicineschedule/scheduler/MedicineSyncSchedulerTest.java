package com.widyu.goal.medicineschedule.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.widyu.global.properties.MedicineProperties;
import com.widyu.goal.medicineschedule.application.ExternalMedicineService;
import com.widyu.goal.medicineschedule.client.MedicineApiClient;
import com.widyu.goal.medicineschedule.dto.external.MedicineApiResponse;
import com.widyu.medicine.Medicine;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicineSyncScheduler 단위 테스트")
class MedicineSyncSchedulerTest {

    @Mock private MedicineApiClient medicineApiClient;
    @Mock private ExternalMedicineService externalMedicineService;
    @Mock private MedicineProperties medicineProperties;

    @Captor private ArgumentCaptor<List<MedicineApiResponse.MedicineItem>> itemsCaptor;

    @InjectMocks private MedicineSyncScheduler medicineSyncScheduler;

    @Test
    @DisplayName("페이지 조회가 일시 실패한 후 재시도하면 다음 페이지까지 동기화한다")
    void 페이지_조회가_일시_실패한_후_재시도하면_다음_페이지까지_동기화한다() {
        // given
        given(medicineProperties.api()).willReturn(new MedicineProperties.Api("url", "service-key"));
        given(medicineApiClient.fetchAllMedicines(anyString(), anyInt(), anyInt(), anyString()))
                .willThrow(new IllegalStateException("temporary failure"))
                .willReturn(responseWithItems(100))
                .willReturn(emptyResponse());
        given(externalMedicineService.upsertMedicines(org.mockito.ArgumentMatchers.anyList()))
                .willReturn(savedMedicines(30));

        // when
        int totalSynced = medicineSyncScheduler.syncMedicinePages();

        // then
        verify(medicineApiClient, times(2)).fetchAllMedicines("service-key", 100, 1, "json");
        verify(medicineApiClient).fetchAllMedicines("service-key", 100, 2, "json");
        verify(externalMedicineService).upsertMedicines(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(100);
        assertThat(totalSynced).isEqualTo(30);
    }

    @Test
    @DisplayName("페이지 재시도가 소진되면 이후 동기화를 중단한다")
    void 페이지_재시도가_소진되면_이후_동기화를_중단한다() {
        // given
        given(medicineProperties.api()).willReturn(new MedicineProperties.Api("url", "service-key"));
        given(medicineApiClient.fetchAllMedicines(anyString(), anyInt(), anyInt(), anyString()))
                .willThrow(new IllegalStateException("persistent failure"));

        // when
        int totalSynced = medicineSyncScheduler.syncMedicinePages();

        // then
        verify(medicineApiClient, times(3)).fetchAllMedicines("service-key", 100, 1, "json");
        verify(externalMedicineService, never()).upsertMedicines(org.mockito.ArgumentMatchers.anyList());
        assertThat(totalSynced).isZero();
    }

    private MedicineApiResponse emptyResponse() {
        return new MedicineApiResponse(
                null,
                new MedicineApiResponse.Body(1, 0, 100, List.of(), null)
        );
    }

    private MedicineApiResponse responseWithItems(int count) {
        List<MedicineApiResponse.MedicineItem> items = IntStream.range(0, count)
                .mapToObj(index -> new MedicineApiResponse.MedicineItem(
                        "제조사",
                        "약품" + index,
                        "seq-" + index,
                        null,
                        null,
                        null
                ))
                .toList();
        return new MedicineApiResponse(null, new MedicineApiResponse.Body(1, count, 100, items, null));
    }

    private List<Medicine> savedMedicines(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> org.mockito.Mockito.mock(Medicine.class))
                .toList();
    }
}
