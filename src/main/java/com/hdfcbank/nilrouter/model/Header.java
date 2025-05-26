package com.hdfcbank.nilrouter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Data
@ToString
@NoArgsConstructor
public class Header {

    private String msgId;
    private String source;
    private boolean targetFC;
    private boolean targetEPH;
    private boolean targetFCEPH;
    private boolean targetSFMS;
    private String flowType;
    private String msgType;
    private String batchId;
    private int orignlReqCount;
    private int intermediateReqFCCount;
    private int intermediateReqEPHCount;
    private BigDecimal consolidateAmt;
    private BigDecimal consolidateAmtEPH;
    private BigDecimal consolidateAmtFC;


}
