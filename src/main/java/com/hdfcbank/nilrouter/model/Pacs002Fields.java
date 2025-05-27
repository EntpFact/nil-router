package com.hdfcbank.nilrouter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
public class Pacs002Fields {
    String bizMsgIdr;
    String endToEndId;
    String txId;
    String swtch;
}
