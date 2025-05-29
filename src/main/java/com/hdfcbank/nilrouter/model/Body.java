package com.hdfcbank.nilrouter.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
public class Body {

    private String reqPayload;
    private String fcPayload;
    private String ephPayload;
}
