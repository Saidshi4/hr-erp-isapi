package com.abv.hrerpisapi;

import com.abv.hrerpisapi.device.IsapiAlertStreamRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HrErpIsapiApplication {

    public static void main(String[] args) throws Exception {
        new Thread(new IsapiAlertStreamRunner("192.168.0.200", "admin", "12345678!", 1)).start();
    }

}