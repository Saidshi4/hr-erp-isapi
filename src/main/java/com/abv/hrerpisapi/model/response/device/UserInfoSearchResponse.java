package com.abv.hrerpisapi.model.response.device;

/**
 * Relevant fields from ISAPI UserInfo/Search response.
 */
public record UserInfoSearchResponse(
        String employeeNo,
        String name,
        String userType
) {
}
