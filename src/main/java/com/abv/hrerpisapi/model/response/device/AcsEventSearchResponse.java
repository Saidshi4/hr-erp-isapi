package com.abv.hrerpisapi.model.response.device;

import java.util.List;

/**
 * Relevant fields from ISAPI AcsEvent/Search response.
 */
public record AcsEventSearchResponse(
        String searchID,
        String responseStatusStrg,
        int numOfMatches,
        int totalMatches,
        List<AcsEventItem> events
) {

    public record AcsEventItem(
            long serialNo,
            int majorEventType,
            int subEventType,
            String time,
            String employeeNoString,
            String cardNo
    ) {
    }
}
