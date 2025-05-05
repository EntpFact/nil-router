package com.hdfcbank.nilrouter.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionAudit {

    private String msgId;
    private String txnId;
    private String endToEndId;
    private String returnId;
    private String reqPayload;
    private String source;
    private String target;
    private String flowType;
    private String msgType;
    private BigDecimal amount;
    private String batchId;

}
