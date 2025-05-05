package com.hdfcbank.nilrouter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MsgEventTracker {

    private String msgId;
    private String source;
    private String target;
    private String flowType;
    private String msgType;
    private String originalReq;

}
