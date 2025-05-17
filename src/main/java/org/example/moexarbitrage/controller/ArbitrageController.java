package org.example.moexarbitrage.controller;

import org.example.moexarbitrage.model.ArbitrageResult;
import org.example.moexarbitrage.model.FutureInstrument;
import org.example.moexarbitrage.repository.FutureInstrumentRepository;
import org.example.moexarbitrage.service.MoexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class ArbitrageController {

    private final MoexService moexService;
    private FutureInstrumentRepository repository;

    @Autowired
    public ArbitrageController(MoexService moexService) {
        this.moexService = moexService;
    }

    @GetMapping("/")
    public String showForm(Model model) {
        //List<String> futures = moexService.getAvailableFutures();
        List<FutureInstrument> futures = repository.findAll();
        model.addAttribute("futures", futures);
        return "index";
    }

    @GetMapping("/analyze")
    public String analyze(@RequestParam("selectedFuture") String selectedFuture, Model model) {
//        List<String> futures = moexService.getAvailableFutures();
        List<FutureInstrument> futures = repository.findAll();
        model.addAttribute("futures", futures);
        model.addAttribute("selectedFuture", selectedFuture);

        ArbitrageResult result = moexService.analyzeArbitrage(selectedFuture);
        model.addAttribute("result", result);

        String expirationDate = moexService.getExpirationDate(selectedFuture);
        model.addAttribute("expirationDate", expirationDate);

        return "index";
    }
}







