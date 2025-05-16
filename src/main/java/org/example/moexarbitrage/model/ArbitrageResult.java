package org.example.moexarbitrage.model;

public class ArbitrageResult {
    private double spotPrice;
    private double futuresPrice;
    private double theoreticalPrice;
    private double arbitragePercent;
    private double interestRate; // <--- добавили
    private String recommendation;

    public ArbitrageResult(double spotPrice, double futuresPrice, double theoreticalPrice, double arbitragePercent, double interestRate, String recommendation) {
        this.spotPrice = spotPrice;
        this.futuresPrice = futuresPrice;
        this.theoreticalPrice = theoreticalPrice;
        this.arbitragePercent = arbitragePercent;
        this.interestRate = interestRate;
        this.recommendation = recommendation;
    }

    // геттеры
    public double getSpotPrice() {
        return spotPrice;
    }

    public double getFuturesPrice() {
        return futuresPrice;
    }

    public double getTheoreticalPrice() {
        return theoreticalPrice;
    }

    public double getArbitragePercent() {
        return arbitragePercent;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public String getRecommendation() {
        return recommendation;
    }
}



