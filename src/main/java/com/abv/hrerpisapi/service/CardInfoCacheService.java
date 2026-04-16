package com.abv.hrerpisapi.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for cardNo → employeeNo lookups.
 * Avoids repeated ISAPI calls for the same card.
 */
@Service
public class CardInfoCacheService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public Optional<String> get(String cardNo) {
        return Optional.ofNullable(cache.get(cardNo));
    }

    public void put(String cardNo, String employeeNo) {
        cache.put(cardNo, employeeNo);
    }
}
