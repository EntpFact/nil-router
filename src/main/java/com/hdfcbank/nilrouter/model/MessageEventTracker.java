package com.hdfcbank.nilrouter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class MessageEventTracker {

    private Header header;
    private Body body;

}
