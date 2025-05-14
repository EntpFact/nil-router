package com.hdfcbank.nilrouter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

    @Data
    @ToString
    @AllArgsConstructor
    public class Pacs004Fields {
        String bizMsgIdr;
        String endToEndId;
        String txId;
        String amount;
        String swtch;
        String batchId;
    }



