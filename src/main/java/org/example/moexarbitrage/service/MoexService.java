package org.example.moexarbitrage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.moexarbitrage.model.ArbitrageResult;
import org.example.moexarbitrage.model.FutureInstrument;
import org.example.moexarbitrage.repository.FutureInstrumentRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    @Autowired
    private FutureInstrumentRepository repository;

//    public List<String> getAvailableFutures() {
//        List<String> activeFutures = new ArrayList<>();
//        try {
//            String json = restTemplate.getForObject(FUTURES_URL, String.class);
//            JsonNode root = objectMapper.readTree(json);
//            JsonNode securities = root.path("securities").path("data");
//            JsonNode columns = root.path("securities").path("columns");
//
//            int secidIndex = -1;
//            int statusIndex = -1;
//            int lastTradeDateIndex = -1;
//            int assetCodeIndex = -1;
//
//            for (int i = 0; i < columns.size(); i++) {
//                String col = columns.get(i).asText();
//                if ("SECID".equalsIgnoreCase(col)) secidIndex = i;
//                if ("STATUS".equalsIgnoreCase(col)) statusIndex = i;
//                if ("LASTTRADEDATE".equalsIgnoreCase(col)) lastTradeDateIndex = i;
//                if ("ASSETCODE".equalsIgnoreCase(col)) assetCodeIndex = i;
//            }
//
//            if (secidIndex == -1 || lastTradeDateIndex == -1 || assetCodeIndex == -1)
//                return activeFutures;
//
//            for (JsonNode row : securities) {
//                String secid = row.get(secidIndex).asText();
//
//                // 1. Только ACTIVE
//                if (statusIndex != -1 && !row.get(statusIndex).asText().equalsIgnoreCase("ACTIVE")) {
//                    continue;
//                }
//
//                // 2. Дата экспирации
//                String dateStr = row.get(lastTradeDateIndex).asText();
//                if (dateStr == null || dateStr.isEmpty()) continue;
//
//                LocalDate lastTradeDate = LocalDate.parse(dateStr);
//                if (!lastTradeDate.isAfter(LocalDate.now()) || lastTradeDate.isEqual(LocalDate.of(2100, 1, 1))) {
//                    continue;
//                }
//
//                // 3. Получаем базовый актив (ASSETCODE → normalize)
//                String assetCodeRaw = row.get(assetCodeIndex).asText();
//                String assetCode = normalizeUnderlying(assetCodeRaw);
//
//                // 4. Получаем цены
//                double futuresPrice = getFuturesPrice(secid);
//                double spotPrice = getSpotPriceForUnderlying(assetCode);
//
//                if (futuresPrice == 0.0 || spotPrice == 0.0) {
//                    continue;
//                }
//
//                // Всё в порядке — добавляем
//                activeFutures.add(secid);
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return activeFutures;
//    }


    public void refreshFuturesToDb() {
        try {
            String json = restTemplate.getForObject(FUTURES_URL, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode securities = root.path("securities").path("data");
            JsonNode columns = root.path("securities").path("columns");

            int secidIndex = -1;
            int statusIndex = -1;
            int lastTradeDateIndex = -1;
            int assetCodeIndex = -1;

            for (int i = 0; i < columns.size(); i++) {
                String col = columns.get(i).asText();
                if ("SECID".equalsIgnoreCase(col)) secidIndex = i;
                if ("STATUS".equalsIgnoreCase(col)) statusIndex = i;
                if ("LASTTRADEDATE".equalsIgnoreCase(col)) lastTradeDateIndex = i;
                if ("ASSETCODE".equalsIgnoreCase(col)) assetCodeIndex = i;
            }

            List<FutureInstrument> result = new ArrayList<>();

            for (JsonNode row : securities) {
                String secid = row.get(secidIndex).asText();

                // Фильтрация по статусу
//                if (statusIndex != -1 && !row.get(statusIndex).asText().equalsIgnoreCase("ACTIVE")) {
//                    continue;
//                }

                // Фильтрация по дате
                String dateStr = row.get(lastTradeDateIndex).asText();
//                if (dateStr == null || dateStr.isEmpty()) continue;

                LocalDate expirationDate = LocalDate.parse(dateStr);
//                if (!expirationDate.isAfter(LocalDate.now()) || expirationDate.isEqual(LocalDate.of(2100, 1, 1))) {
//                    continue;
//                }

                // Получаем assetCode
                String assetCodeRaw = row.get(assetCodeIndex).asText();
                String assetCode = normalizeUnderlying(assetCodeRaw);

                // Получаем цены
                double futuresPrice = getFuturesPrice(secid);
                double spotPrice = getSpotPriceForUnderlying(assetCode);

//                if (futuresPrice == 0.0 || spotPrice == 0.0) continue;

                // Заполняем объект
                FutureInstrument fi = new FutureInstrument();
                fi.setSecid(secid);
                fi.setAssetCode(assetCode);
                fi.setExpirationDate(expirationDate);
                fi.setFuturesPrice(futuresPrice);
                fi.setSpotPrice(spotPrice);

                result.add(fi);
            }

            // Сохраняем в базу
            repository.deleteAllInBatch(); // очищаем старые данные
            repository.saveAll(result);
            System.out.println("Обновлено " + result.size() + " фьючерсов в базе.");
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                if (lastValueIndex == -1)
                    return 0.0;

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
                if (lastValueIndex == -1)
                    return 0.0;

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
            // Текущая дата в формате dd.MM.yyyy
            String date1 = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            // Текущая дата в формате dd/MM/yyyy
            String date2 = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            //Url на станицу ЦБ с актуальной ставкой на сегодня
            String url = "https://www.cbr.ru/hd_base/keyrate/?UniDbQuery.Posted=True&UniDbQuery.From=" + date1 + "&UniDbQuery.To=" + date2;


            // 1. Загружаем HTML
            Document doc = Jsoup.connect(url).get();

            // 2. Ищем таблицу с классом "data" (как в вашем примере)
            Element table = doc.select("table.data").first();
            if (table == null) {
                System.out.println("Таблица не найдена!");

            }
            // 3. Извлекаем последнюю строку (актуальная ставка)
            Elements rows = table.select("tr");
            Element lastRow = rows.last();
            String rate1 = lastRow.select("td").get(1).text();  // Ставка (вторая колонка)
            rate1 = rate1.replace(',', '.');

            double rate2 = Double.parseDouble(rate1) / 100.0; // переводим из процентов в доли
            System.out.println("Ключевая ставка ЦБ РФ: " + rate2);
            return rate2;

        } catch (Exception e) {
            System.out.println("Ошибка при получении ключевой ставки ЦБ РФ: " + e.getMessage());
        }

        return 0.0; // Заглушка на случай ошибки
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
                   // System.out.println("Spot price for " + assetCode + " is: " + value);
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

    public String getExpirationDate(String futuresSecid) {
        try {
            String url = String.format("https://iss.moex.com/iss/engines/futures/markets/forts/securities/%s.json", futuresSecid);
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode columns = root.path("securities").path("columns");
            JsonNode data = root.path("securities").path("data").get(0);

            int index = -1;
            for (int i = 0; i < columns.size(); i++) {
                if ("LASTTRADEDATE".equalsIgnoreCase(columns.get(i).asText())) {
                    index = i;
                    break;
                }
            }

            if (index != -1 && !data.get(index).isNull()) {
                return data.get(index).asText();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "н/д";
    }





}





