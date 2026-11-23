package com.lab.job;

import com.lab.service.ReservationService;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.mockito.Mockito.*;

class PendingExpiryJobTest {
    @Test void perRecordFailureDoesNotStopOtherRecordsAndNextSweepRetries() {
        ReservationService service=mock(ReservationService.class);
        when(service.findDuePendingExpiryIds(0L,100)).thenReturn(Arrays.asList(1L,2L),Collections.singletonList(1L));
        doThrow(new IllegalStateException("temporary")).doNothing().when(service).processPendingExpiry(1L);
        ReservationMaintenanceJob job=new ReservationMaintenanceJob(service);
        job.sweep();job.sweep();
        verify(service,times(2)).processPendingExpiry(1L);verify(service).processPendingExpiry(2L);
        verify(service,times(2)).findDueNoShowIds(0L,100);verify(service,times(2)).findFinishedEquipmentIds(0L,100);
    }
}
