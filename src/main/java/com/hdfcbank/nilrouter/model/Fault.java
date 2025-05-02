package com.hdfcbank.nilrouter.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@Builder
@ToString
public class Fault {
	String errorType;
	String responseStatusCode;
    String errorCode;
    String errorDescription;
   
}
