
package com.analysis.scanner;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.analysis.SignalState;

import lombok.Getter;
import lombok.Setter;


@Setter
@Getter

@Document(collection = "signal_states")
public class SignalStateDoc {

    @Id
    private String id;

    @Field("ScripCode")
    private String scripCode;
    private String symbol;
    private SignalState signalState;
    private Instant evaluatedAt;

  
}
