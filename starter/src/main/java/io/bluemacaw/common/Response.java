package cn.com.wind.IMStarter.common;

import cn.com.wind.IMStarter.constant.ErrorCode;
import cn.com.wind.IMStarter.constant.ErrorMessageEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class Response<T> {
    private Integer code;
    private String message;
    private T data;

    public static<T> Response<T> success() {
        return Response
                .<T>builder()
                .code(ErrorCode.ErrorCode_Ok)
                .message("Success")
                .build();
    }

    // 第一个<T>代表定义一个泛型方法，方法持有一个泛型T，第二个<T>代表泛型类，第二、三个代表使用泛型T
    public static <T> Response<T> success(T t) {

        return Response
                .<T>builder()
                .code(ErrorCode.ErrorCode_Ok)
                .data(t)
                .message("Success")
                .build();
    }

    public static <T> Response<T> success(T t, String message) {

        return Response
                .<T>builder()
                .code(ErrorCode.ErrorCode_Ok)
                .data(t)
                .message(message)
                .build();
    }

    public static <T> Response<T> fail(T t, ErrorMessageEnum errorMessage) {
        return Response
                .<T>builder()
                .code(errorMessage.getCode())
                .message(errorMessage.getMessage())
                .build();
    }

    public static <T> Response<T> fail(ErrorMessageEnum errorMessage) {
        return Response
                .<T>builder()
                .code(errorMessage.getCode())
                .message(errorMessage.getMessage())
                .build();
    }

    public static <T> Response<T> fail(T t, int code, String message) {
        return Response
                .<T>builder()
                .code(code)
                .message(message)
                .build();
    }

    public static <T> Response<T> fail(int code, String message) {
        return Response
                .<T>builder()
                .code(code)
                .message(message)
                .build();
    }

    public static <T> Response<T> response(int code, String message) {
        return Response
                .<T>builder()
                .code(code)
                .message(message)
                .build();
    }
}
