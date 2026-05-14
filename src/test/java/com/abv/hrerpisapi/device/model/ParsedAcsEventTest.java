package com.abv.hrerpisapi.device.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ParsedAcsEventTest {

    @Test
    void shouldExtractHostlessPicturePathFromHttpUrl() {
        String pictureUrl = "http://192.168.0.200/LOCALS/pic/acsLinkCap/202605_00/01_170640_30075_0.jpeg@WEB000000000189";

        String picturePath = ParsedAcsEvent.toPicturePath(pictureUrl);

        assertEquals("/LOCALS/pic/acsLinkCap/202605_00/01_170640_30075_0.jpeg@WEB000000000189", picturePath);
    }

    @Test
    void shouldKeepAlreadyRelativePicturePath() {
        String picturePath = ParsedAcsEvent.toPicturePath("/LOCALS/pic/acsLinkCap/202605_00/x.jpeg@WEB1");

        assertEquals("/LOCALS/pic/acsLinkCap/202605_00/x.jpeg@WEB1", picturePath);
    }

    @Test
    void shouldKeepQueryAndFragmentAfterRemovingHost() {
        String pictureUrl = "http://192.168.0.200:8080/LOCALS/pic/x.jpeg@WEB1?token=abc#v1";

        String picturePath = ParsedAcsEvent.toPicturePath(pictureUrl);

        assertEquals("/LOCALS/pic/x.jpeg@WEB1?token=abc#v1", picturePath);
    }

    @Test
    void shouldReturnNullForMissingPictureUrl() {
        assertNull(ParsedAcsEvent.toPicturePath(null));
        assertNull(ParsedAcsEvent.toPicturePath(" "));
    }
}
