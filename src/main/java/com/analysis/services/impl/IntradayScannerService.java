package com.analysis.services.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.analysis.documents.VWAPValue;
import com.analysis.dto.BhavCopy;
import com.analysis.dto.IntradayLongResult;
import com.analysis.repositories.BhavCopyRepository;
import com.analysis.repositories.VWAPValueRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IntradayScannerService {

    private final VWAPValueRepository vwapRepository;
    private final BhavCopyRepository bhavCopyRepository;

    public List<IntradayLongResult> scanAllLongs() {

        // 1️⃣ Fetch all VWAP entries
        List<VWAPValue> vwapList = vwapRepository.findAll();
        if (vwapList.isEmpty()) {
            return List.of();
        }

        // 2️⃣ Extract symbols
        List<String> symbols = vwapList.stream()
                .map(VWAPValue::getSymbol)
                .toList();

        // 3️⃣ Bulk fetch bhavcopy (NO N+1)
        Map<String, BhavCopy> bhavMap =
                bhavCopyRepository.findByTckrSymbIn(symbols)
                        .stream()
                        .collect(Collectors.toMap(
                                BhavCopy::getTckrSymb,
                                Function.identity()
                        ));

        // 4️⃣ Scan LONG-only
        List<IntradayLongResult> results =
                new ArrayList<>(vwapList.size());

        for (VWAPValue vwap : vwapList) {

            BhavCopy bhav = bhavMap.get(vwap.getSymbol());
            if (bhav == null) continue;

            if (isValidLong(vwap, bhav)) {
                results.add(new IntradayLongResult(
                        vwap.getSymbol(),
                        vwap.getClose().doubleValue(),
                        vwap.getVwap().doubleValue(),
                        bhav.getHghPric()
                ));
            }
        }

        return results;
    }

    // 🔥 LONG CONDITION
    private boolean isValidLong(VWAPValue vwap, BhavCopy bhav) {

        return vwap.getClose().compareTo(vwap.getVwap()) > 0
            && vwap.getClose().compareTo(
                BigDecimal.valueOf(bhav.getHghPric())) > 0;
    }
}
