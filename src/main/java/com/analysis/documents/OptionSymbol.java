package com.analysis.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.analysis.constants.Constants;

import lombok.Data;

@Data
@Document(collection = Constants.OPTION_SYMBOLS_COLLECTION)
public class OptionSymbol {
    @Id
    private String id;
    private String symbol;
}