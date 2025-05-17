package org.example.moexarbitrage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.moexarbitrage.model.ArbitrageResult;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class MoexService {

    private static final String FUTURES_URL = "https://iss.moex.com/iss/engines/futures/markets/forts/securities.json";
    private static final String SPOT_SHARE_TEMPLATE_URL = "https://iss.moex.com/iss/engines/stock/markets/shares/boards/TQBR/securities/%s.json";
    private static final String FUTURES_MARKETDATA_URL_TEMPLATE = "https://iss.moex.com/iss/engines/futures/markets/forts/boards/RFUD/securities/%s.json";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> getAvailableFutures() {
        List<String> activeFutures = new ArrayList<>();
        try {
            String json = restTemplate.getForObject(FUTURES_URL, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode securities = root.path("securities").path("data");
            JsonNode columns = root.path("securities").path("columns");

            int secidIndex = -1;
            int statusIndex = -1;

            for (int i = 0; i < columns.size(); i++) {
                String col = columns.get(i).asText();
                if (col.equals("SECID")) secidIndex = i;
                if (col.equals("STATUS")) statusIndex = i;
            }

            if (secidIndex == -1) return activeFutures;

            for (JsonNode futureData : securities) {
                if (statusIndex != -1) {
                    String status = futureData.get(statusIndex).asText();
                    if (!"ACTIVE".equalsIgnoreCase(status)) continue;
                }
                String secid = futureData.get(secidIndex).asText();
                activeFutures.add(secid);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return activeFutures;
    }

    public String getUnderlyingAsset(String futuresSecid) {
        try {
            String json = restTemplate.getForObject(FUTURES_URL, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("securities").path("data");
            JsonNode columns = root.path("securities").path("columns");

            int secidIndex = -1;
            int assetCodeIndex = -1;

            for (int i = 0; i < columns.size(); i++) {
                String columnName = columns.get(i).asText();
                if ("SECID".equalsIgnoreCase(columnName)) {
                    secidIndex = i;
                } else if ("ASSETCODE".equalsIgnoreCase(columnName)) {
                    assetCodeIndex = i;
                }
            }

            if (secidIndex == -1 || assetCodeIndex == -1) {
                return null;
            }

            for (JsonNode row : data) {
                if (row.get(secidIndex).asText().equalsIgnoreCase(futuresSecid)) {
                    String assetCode = row.get(assetCodeIndex).asText();
                    System.out.println("Found ASSETCODE: " + assetCode);
                    return assetCode;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public double getSpotPriceBySecid(String secid) {
        try {
            String url = String.format(SPOT_SHARE_TEMPLATE_URL, secid);
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode marketdata = root.path("marketdata").path("data");
            JsonNode columns = root.path("marketdata").path("columns");

            if (marketdata.isArray() && marketdata.size() > 0) {
                int lastValueIndex = -1;
                for (int i = 0; i < columns.size(); i++) {
                    if ("LAST".equalsIgnoreCase(columns.get(i).asText()) || "LASTVALUE".equalsIgnoreCase(columns.get(i).asText())) {
                        lastValueIndex = i;
                        break;
                    }
                }
                if (lastValueIndex == -1) return 0.0;

                double lastValue = marketdata.get(0).get(lastValueIndex).asDouble(0.0);
                return lastValue;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public double getFuturesPrice(String secid) {
        try {
            String url = String.format(FUTURES_MARKETDATA_URL_TEMPLATE, secid);
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode marketdata = root.path("marketdata").path("data");
            JsonNode columns = root.path("marketdata").path("columns");

            if (marketdata.isArray() && marketdata.size() > 0) {
                int lastValueIndex = -1;
                for (int i = 0; i < columns.size(); i++) {
                    if ("LAST".equalsIgnoreCase(columns.get(i).asText()) || "LASTVALUE".equalsIgnoreCase(columns.get(i).asText())) {
                        lastValueIndex = i;
                        break;
                    }
                }
                if (lastValueIndex == -1) return 0.0;

                return marketdata.get(0).get(lastValueIndex).asDouble(0.0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public double getSpotPriceByFutures(String futuresSecid) {
        String underlying = getUnderlyingAsset(futuresSecid);
        System.out.println("Found UNDERLYING: " + underlying);
        if (underlying != null) {
            double price = getSpotPriceBySecid(underlying);
            System.out.println("Spot price for " + underlying + " is: " + price);
            return price;
        }
        return 0.0;
    }

    public ArbitrageResult analyzeArbitrage(String selectedFuture) {
        // Получение цен
        String assetCodeRaw = getUnderlyingAsset(selectedFuture);
        String assetCode = normalizeUnderlying(assetCodeRaw);
        double spotPrice = getSpotPriceForUnderlying(assetCode);
        double futuresPrice = getFuturesPrice(selectedFuture);

        // Получаем ключевую ставку
        double interestRate = getKeyRateFromCbr();

        // Получаем дату экспирации
        int daysToExpiration = getDaysToExpiration(selectedFuture);
        System.out.println("До экспирации осталось дней: " + daysToExpiration);

        // Расчёт теоретической цены
        double theoreticalPrice = spotPrice * (1 + interestRate * daysToExpiration / 365.0);

        // Расчёт арбитража
        double arbitragePercent = 0;
        if (theoreticalPrice != 0) {
            arbitragePercent = ((futuresPrice - theoreticalPrice) / theoreticalPrice) * 100;
        }

        String recommendation;
        if (arbitragePercent > 0) {
            recommendation = String.format("Фьючерс переоценен на %.2f%%. Рекомендация: продать фьючерс, купить акции.", arbitragePercent);
        } else {
            recommendation = String.format("Фьючерс недооценен на %.2f%%. Рекомендация: купить фьючерс, продать акции.", Math.abs(arbitragePercent));
        }

        return new ArbitrageResult(spotPrice, futuresPrice, theoreticalPrice, arbitragePercent, interestRate, recommendation);
    }

    public double getKeyRateFromCbr() {
        try {
            String url = "https://www.cbr-xml-daily.ru/daily_json.js";
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);

            // В этом JSON нет явного поля "KeyRate", но мы можем его заменить при необходимости на API ЦБ
            // Альтернатива: получить ставку из справочника на сайте ЦБ РФ или парсить HTML
            // Пока оставим заглушку, если не найдем
            System.out.println("⚠️ В JSON от CBR не найдено поле ключевой ставки.");
        } catch (Exception e) {
            System.out.println("Ошибка при получении ключевой ставки ЦБ РФ: " + e.getMessage());
        }
        return 0.15; // Временно — 15% как заглушка, заменим после выбора корректного источника
    }



    public double getSpotPriceForUnderlying(String assetCode) {
        try {
            String url = String.format("https://iss.moex.com/iss/engines/stock/markets/shares/securities/%s.json", assetCode);
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode marketdata = root.path("marketdata").path("data");
            JsonNode columns = root.path("marketdata").path("columns");

            if (marketdata.isArray() && marketdata.size() > 0) {
                int lastIndex = -1;
                for (int i = 0; i < columns.size(); i++) {
                    String col = columns.get(i).asText();
                    if ("LAST".equalsIgnoreCase(col) || "LASTVALUE".equalsIgnoreCase(col)) {
                        lastIndex = i;
                        break;
                    }
                }

                if (lastIndex != -1 && marketdata.get(0).get(lastIndex) != null) {
                    double value = marketdata.get(0).get(lastIndex).asDouble();
                    System.out.println("Spot price for " + assetCode + " is: " + value);
                    return value;
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to fetch spot price for asset: " + assetCode);
            e.printStackTrace();
        }
        return 0.0;
    }

    private String normalizeUnderlying(String assetCode) {
        // Удаляем F, R, или другие постфиксы, если они есть
        if (assetCode.endsWith("F")) {
            return assetCode.substring(0, assetCode.length() - 1);
        }
        return assetCode;
    }
    public int getDaysToExpiration(String futuresSecid) {
        try {
            String url = String.format("https://iss.moex.com/iss/securities/%s.json", futuresSecid);
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode columns = root.path("description").path("columns");
            JsonNode data = root.path("description").path("data").get(0);

            int lastTradeDateIndex = -1;
            for (int i = 0; i < columns.size(); i++) {
                if ("LASTTRADEDATE".equalsIgnoreCase(columns.get(i).asText())) {
                    lastTradeDateIndex = i;
                    break;
                }
            }

            if (lastTradeDateIndex != -1) {
                String lastTradeDateStr = data.get(lastTradeDateIndex).asText(); // формат: YYYY-MM-DD
                LocalDate expirationDate = LocalDate.parse(lastTradeDateStr);
                LocalDate today = LocalDate.now();

                long daysBetween = ChronoUnit.DAYS.between(today, expirationDate);
                return (int) Math.max(daysBetween, 0); // если дата уже прошла — 0
            }
        } catch (Exception e) {
            System.out.println("Ошибка при получении даты экспирации: " + e.getMessage());
        }
        return 0;
    }


}





