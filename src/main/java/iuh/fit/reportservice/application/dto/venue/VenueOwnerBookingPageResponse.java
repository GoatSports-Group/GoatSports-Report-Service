package iuh.fit.reportservice.application.dto.venue;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VenueOwnerBookingPageResponse {
    private Meta meta;
    private List<VenueOwnerBookingResponse> result = new ArrayList<>();

    @Data
    public static class Meta {
        private int page;
        private int pageSize;
        private int pages;
        private long total;
    }
}
