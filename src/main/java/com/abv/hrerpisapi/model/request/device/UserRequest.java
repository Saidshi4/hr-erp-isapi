package com.abv.hrerpisapi.model.request.device;

public class UserRequest {

    public static String userAddJson(String employeeNo, String name) {
        // Bəzi cihazlar root-u {"UserInfo":{...}} istəyir. Ona görə onu belə saxlayırıq.
        return """
      {
        "UserInfo": {
          "employeeNo": "%s",
          "name": "%s",
          "userType": "normal",
          "Valid": {
            "enable": true,
            "beginTime": "2020-01-01T00:00:00",
            "endTime": "2037-12-31T23:59:59"
          },
          "doorRight": "1",
          "RightPlan": [
            { "doorNo": 1, "planTemplateNo": 1 }
          ]
        }
      }
      """.formatted(employeeNo, name);
    }
}