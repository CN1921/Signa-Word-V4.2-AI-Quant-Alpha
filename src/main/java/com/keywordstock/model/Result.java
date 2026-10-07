package com.keywordstock.model;

public class Result<T> {
    private int code;
    private String message;
    private T data;

    public Result() {}
    public Result(int code, String message, T data) { this.code = code; this.message = message; this.data = data; }
    public static <T> Result<T> ok(T d) { return new Result<>(0, "ok", d); }
    public static <T> Result<T> fail(String msg) { return new Result<>(-1, msg, null); }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
