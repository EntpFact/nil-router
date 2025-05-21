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
    private Boolean targetFC;
    private Boolean targetEPH;
    private Boolean targetFCEPH;
    private Boolean targetSFMS;
    private String flowType;
    private String msgType;
    private String batchId;
    private Integer orignlReqCount;
    private Integer intermediateReqFCCount;
    private Integer intermediateReqEPHCount;
    private BigDecimal consolidateAmt;
    private String consolidateAmtEPH;
    private String consolidateAmtFC;


}
