package com.abv.hrerpisapi.service;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.device.client.IsapiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves the employeeNo for an event.
 * Priority: employeeNoString (device gave it) → cardNo cache → ISAPI CardInfo lookup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityResolveService {

    private final CardInfoCacheService cardInfoCache;
    private final IsapiClient isapiClient;

    /**
     * @return employeeNo string, or {@code null} if resolution is not possible
     */
    public String resolve(DeviceEntity device, String employeeNoString, String cardNo) {
        if (employeeNoString != null && !employeeNoString.isBlank()) {
            return employeeNoString;
        }

        if (cardNo == null || cardNo.isBlank()) {
            return null;
        }

        return cardInfoCache.get(cardNo).orElseGet(() -> {
            try {
                return isapiClient.searchCardEmployeeNo(device, cardNo)
                        .map(empNo -> {
                            cardInfoCache.put(cardNo, empNo);
                            return empNo;
                        })
                        .orElse(null);
            } catch (Exception e) {
                log.warn("Card lookup failed for cardNo={} on device {}: {}",
                        cardNo, device.getId(), e.getMessage());
                return null;
            }
        });
    }
}
