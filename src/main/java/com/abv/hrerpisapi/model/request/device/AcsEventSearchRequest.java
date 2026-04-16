package com.abv.hrerpisapi.model.request.device;

/**
 * Builds the JSON body for ISAPI AcsEvent/Search requests.
 */
public class AcsEventSearchRequest {

    private AcsEventSearchRequest() {
    }

    /**
     * @param startTime ISO-8601 without offset, e.g. "2024-01-01T00:00:00"
     * @param endTime   ISO-8601 without offset
     * @param maxResults max number of results to return
     */
    public static String forMajor5(String startTime, String endTime, int maxResults) {
        return """
                {"AcsEventCond":{"searchID":"1","searchResultPosition":0,"maxResults":%d,\
                "major":5,"startTime":"%s","endTime":"%s"}}"""
                .formatted(maxResults, startTime, endTime);
    }
}
