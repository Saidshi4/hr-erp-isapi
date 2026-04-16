package com.abv.hrerpisapi.model.request.device;

/**
 * Helper class with static factory methods for building ISAPI request JSON bodies.
 * Existing {@link UserRequest} lives in the same package for user-related requests.
 */
public class CardInfoSearchRequest {

    private CardInfoSearchRequest() {
    }

    public static String byCardNo(String cardNo) {
        return """
                {"CardInfoSearchCond":{"searchID":"1","SearchResultPosition":0,"maxResults":1,\
                "CardInfo":{"cardNo":"%s"}}}""".formatted(cardNo);
    }
}
