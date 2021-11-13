package org.example;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class WeatherServiceTest {

    private WeatherService weatherService;

    @BeforeEach
    public void init() {
        //由于开发本机网络原因，本身无法达到QPS100，所以为单元测试设置成20
        weatherService = new WeatherService(20);

        try {
            weatherService.init();
        } catch (Exception e) {
            Assertions.fail(e.getMessage());
        }
    }

    @Test
    public void testGetTemperature() {

        //good
        Optional<Integer> temp = weatherService.getTemperature("江苏", "苏州", "昆山");
        Assertions.assertTrue(temp.isPresent());

        //bad
        temp = weatherService.getTemperature("江苏", "xxx", "昆山");
        Assertions.assertFalse(temp.isPresent());

    }

    @Test
    public void testGetTemperatureRateLimit() throws Exception {
        //good
        Optional<Integer> temp = weatherService.getTemperature("江苏", "苏州", "昆山");
        Assertions.assertTrue(temp.isPresent());

        Thread.sleep(2000);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();

        long l1 = System.currentTimeMillis();

        for(int i=0; i < 5; i++) {
            Thread t = new Thread(() -> {
                for (int j=0; j<6; j++) {
                    Optional<Integer> opt = weatherService.getTemperature("江苏", "苏州", "昆山");
                    if (opt.isPresent()) {
                        successCount.incrementAndGet();
                    } else {
                        failedCount.incrementAndGet();
                    }
                }

            });
            t.start();
            t.join();
        }

        long l2 = System.currentTimeMillis();

        //验证是否在1秒钟之内
        Assertions.assertTrue(l2 - l1 < 1000);
        Assertions.assertEquals(20, successCount.get());
        Assertions.assertEquals(10, failedCount.get());
    }

    @Test
    public void testGetResponseFromUrl() {

        WeatherService weatherService = new WeatherService();

        //good
        try {
            String resp = weatherService.getResponseFromUrl("http://www.weather.com.cn/data/city3jdata/china.html");

            Assertions.assertNotNull(resp);
        } catch (Exception e) {
            Assertions.fail(e.getMessage());
        }

        //bad
        try {
            weatherService.getResponseFromUrl("http://");
            Assertions.fail("do not throw exception when url is incorrect");
        } catch (Exception e) {
            Assertions.assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testGetCountyCode() {

        WeatherService weatherService = new WeatherService();

        String jiangsu = "10119";
        String suzhou = "04";

        //good
        try {
            String code = weatherService.getCountyCode(jiangsu, suzhou, "苏州");
            Assertions.assertNotNull(code);
        } catch (Exception e) {
            Assertions.fail(e.getMessage());
        }

        //bad
        try {
            String code = weatherService.getCountyCode("xxx", suzhou, "苏州");
            Assertions.fail("do not throw exception when param is not good");
        } catch (Exception e) {
            Assertions.assertEquals("cannot get county mapping data", e.getMessage());
        }

        //bad
        try {
            String code = weatherService.getCountyCode(jiangsu, "xxx", "苏州");
            Assertions.fail("do not throw exception when param is not good");
        } catch (Exception e) {
            Assertions.assertEquals("cannot get county mapping data", e.getMessage());
        }

        //bad
        try {
            String code = weatherService.getCountyCode(jiangsu, suzhou, "xxx");
            Assertions.fail("do not throw exception when param is not good");
        } catch (Exception e) {
            Assertions.assertEquals("county not exist:xxx", e.getMessage());
        }
    }

    @Test
    public void testGetT() {

        try {
            weatherService.getT("江浙", "苏州", "昆山");
            Assertions.fail("do not throw exception when param is not good");
        } catch (Exception e) {
            Assertions.assertEquals("province not exist:江浙", e.getMessage());
        }

        try {
            weatherService.getT("江苏", "姑苏", "昆山");
            Assertions.fail("do not throw exception when param is not good");
        } catch (Exception e) {
            Assertions.assertEquals("city not exist:姑苏", e.getMessage());
        }

        //good
        try {
            int temp = weatherService.getT("江苏", "苏州", "昆山");

            Assertions.assertTrue(temp != 0);
        } catch (Exception e) {
            Assertions.fail("not expected to happen", e);
        }


        // failed retry
        WeatherService mockService = Mockito.spy(weatherService);

        try {
            Mockito.when(mockService.getResponseFromUrl("http://www.weather.com.cn/data/sk/101190404.html")).thenThrow(new Exception("123"));

        } catch (Exception e) {
            Assertions.fail(e.getMessage());
        }

        try {
            int temp = mockService.getT("江苏", "苏州", "昆山");

            Assertions.fail("not expected to happen");
        } catch (Exception e) {
            Assertions.assertEquals("get weather info failed", e.getMessage());
        }

    }
}