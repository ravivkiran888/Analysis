package com.analysis.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.analysis.constants.Constants;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Document(collection = Constants.SECTOR_INDICES_COLLECTION)
public class SectorIndices {
    
    @Id
    private String id;
    private String sector;
    private String name;

    // Default constructor
    public SectorIndices() {}

    // Parameterized constructor
    public SectorIndices(String sector, String name) {
        this.sector = sector;
        this.name = name;
    }


}