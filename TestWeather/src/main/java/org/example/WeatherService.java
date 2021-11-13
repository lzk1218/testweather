package org.example;

import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class WeatherService {

    private Map<String, String> provinceMap = new HashMap<>();

    private Map<String, Map<String, String>> provinceToCityMap = new HashMap<>();

    private Map<String, Map<String, String>> cityToCountyMap = new ConcurrentHashMap<>();

    private static final String PROVINCE_URL = "http://www.weather.com.cn/data/city3jdata/china.html";

    private static final String CITY_URL = "http://www.weather.com.cn/data/city3jdata/provshi/{province}.html";

    private static final String COUNTY_URL = "http://www.weather.com.cn/data/city3jdata/station/{province}{city}.html";

    private static final String WEATHER_URL = "http://www.weather.com.cn/data/sk/{province}{city}{county}.html";

    private static final int MAX_RETRY_TIME = 3;

    private static final int MAX_TPS = 100;

    private RateLimiter rateLimiter = null;

    public WeatherService() {
        rateLimiter = new RateLimiter(MAX_TPS, 1);
    }

    public WeatherService(int tps) {
        rateLimiter = new RateLimiter(tps, 1);
    }

    public void init() throws Exception {
        setProvinceMap();
        setCityMap();
    }

    public Optional<Integer> getTemperature(String province, String city, String county) {

        if (rateLimiter.acquire()) {

            try {
                Integer temp = this.getT(province, city, county);
                return Optional.of(temp);
            } catch (Exception e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    protected Integer getT(String province, String city, String county) throws Exception {
        String provinceCode = provinceMap.get(province);
        if (provinceCode == null) {
            throw new Exception("province not exist:" + province);
        }
        Map<String, String> cityMap = provinceToCityMap.get(provinceCode);
        if (cityMap == null) {
            throw new Exception("province not exist:" + province);
        }
        String cityCode = cityMap.get(city);
        if (cityCode == null) {
            throw new Exception("city not exist:" + city);
        }

        String countyCode = getCountyCode(provinceCode, cityCode, county);

        Exception ex = null;

        for (int i=0; i<MAX_RETRY_TIME; i++) {

            try {
                String resp = getResponseFromUrl(WEATHER_URL.replace("{province}", provinceCode).replace("{city}", cityCode).replace("{county}", countyCode));

                JSONObject jsonObject = JSONObject.parseObject(resp);

                JSONObject content = jsonObject.getObject("weatherinfo", JSONObject.class);

                if (content == null) {
                    throw new Exception("cannot get weather info from result");
                }

                String temp = content.getString("temp");

                return new Float(temp).intValue();

            } catch (Exception e) {

                ex = e;
                System.out.println("get weather for {} failed, retry ".replace("{}", province + city + county) + (i+1));

            }
        }

        throw new Exception("get weather info failed", ex);
    }

    public String getResponseFromUrl(String urlString) throws Exception {
        System.out.println("获取网页信息,url:" + urlString);

        URL url = new URL(urlString);

        HttpURLConnection conn = (HttpURLConnection)url.openConnection();

        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {

                String str = "";

                StringBuffer buffer = new StringBuffer();

                while((str = reader.readLine()) != null) {
                    buffer.append(str);
                }

               return buffer.toString();
            }


        } else {
            throw new Exception("cannot get response from url:" + urlString);
        }
    }

    private void setProvinceMap() throws Exception {

        try {
            String resp = this.getResponseFromUrl(PROVINCE_URL);

            JSONObject jsonObject = JSONObject.parseObject(resp);

            jsonObject.forEach((k, v) -> {
                provinceMap.put((String) v, k);
            });

            System.out.println(provinceMap);

        } catch (Exception e) {
            throw new Exception("cannot get province mapping data", e);
        }

    }

    private void setCityMap() throws Exception {

        if (provinceMap != null) {


            for (String provinceCode : provinceMap.values()) {

                try {
                    String resp = this.getResponseFromUrl(CITY_URL.replace("{province}", provinceCode));

                    JSONObject jsonObject = JSONObject.parseObject(resp);

                    Map cityMap = new HashMap<String, String>();
                    jsonObject.forEach((k, v) -> {
                        cityMap.put((String) v, k);
                    });

                    provinceToCityMap.put(provinceCode, cityMap);

                    System.out.println(cityMap);

                } catch (Exception e) {
                    throw new Exception("cannot get city mapping data", e);
                }

            }
        }

    }

    protected String getCountyCode(String provinceCode, String cityCode, String county) throws Exception {


        Map<String, String> countyMap = null;

        try {
            countyMap = cityToCountyMap.computeIfAbsent(provinceCode + cityCode, k -> {

                try {

                    String resp = this.getResponseFromUrl(COUNTY_URL.replace("{province}", provinceCode).replace("{city}", cityCode));

                    JSONObject jsonObject = JSONObject.parseObject(resp);

                    Map<String, String> temp = new HashMap<>();

                    jsonObject.forEach((key, value) -> {
                        temp.put((String) value, key);
                    });

                    System.out.println(temp);

                    return temp;

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw new Exception("cannot get county mapping data", e);
        }

        System.out.println(countyMap);

        if (countyMap != null) {

            String countyCode = countyMap.get(county);

            if (countyCode == null) {
                throw new Exception("county not exist:" + county);
            } else {
                return countyCode;
            }

        } else {
            throw new Exception("province or city not exist:" + provinceCode + "," + cityCode);
        }

    }

    public static void main(String[] args) throws Exception {

        WeatherService weatherService = new WeatherService();
        weatherService.init();

        Optional<Integer> temp = weatherService.getTemperature("江苏", "苏州", "昆山1");

        temp.ifPresent(System.out::println);
        int t = temp.orElse(0);
        System.out.println(t);
    }
}
