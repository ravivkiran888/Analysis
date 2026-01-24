package com.analysis.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.dto.SymbolBhavDTO;
import com.analysis.services.BhavMongoTemplateService;

@RestController
public class BhavController {

    private final BhavMongoTemplateService service;

    public BhavController(BhavMongoTemplateService service) {
        this.service = service;
    }

    @GetMapping("/bhav/summary")
    public List<SymbolBhavDTO> getSummary() {
    	
    	
    var ss = 	service.getBhavSummary().stream().filter(e->e.getTtlTradgVol()<1500000).map(e->e.getSymbol()).collect(Collectors.toList());
    	
    System.out.println(ss);
    	
     return    service.getBhavSummary().stream().filter(e->e.getTtlTradgVol()>0).collect(Collectors.toList());
    }
}
