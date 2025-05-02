package com.hdfcbank.nilrouter.exception;



import com.hdfcbank.nilrouter.model.Fault;

import java.util.List;

public class NILException extends RuntimeException{
    private static final long serialVersionUID = 1L;
    List<Fault> errors;
    public NILException(String message){
        super(message);
    }

    public NILException(){

    }
    public NILException(String message, Throwable e){
        super(message,e);
    }

    public NILException(String message, List<Fault> errors)
    {
        super(message);
        this.errors=errors;
    }

    public List<Fault> getErrors() {
        return errors;
    }

    public void setErrors(List<Fault> errors) {
        this.errors = errors;
    }
}
