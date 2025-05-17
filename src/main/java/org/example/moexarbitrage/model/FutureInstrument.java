package org.example.moexarbitrage.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDate;

@Entity
public class FutureInstrument {
    @Id
    private String secid;

    private String assetCode;
    private LocalDate expirationDate;
    private double futuresPrice;
    private double spotPrice;

    public String getSecid() {
        return secid;
    }

    public void setSecid(String secid) {
        this.secid = secid;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public double getFuturesPrice() {
        return futuresPrice;
    }

    public void setFuturesPrice(double futuresPrice) {
        this.futuresPrice = futuresPrice;
    }

    public double getSpotPrice() {
        return spotPrice;
    }

    public void setSpotPrice(double spotPrice) {
        this.spotPrice = spotPrice;
    }
}

