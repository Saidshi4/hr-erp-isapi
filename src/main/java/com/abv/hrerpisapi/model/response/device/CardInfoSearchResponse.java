package com.abv.hrerpisapi.model.response.device;

/**
 * Relevant fields from ISAPI CardInfo/Search response.
 */
public record CardInfoSearchResponse(
        String cardNo,
        String employeeNo
) {
}
