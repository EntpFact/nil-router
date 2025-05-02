package com.hdfcbank.nilrouter.model;

import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response {

    String status;
    String message;
}
