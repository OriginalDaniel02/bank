package danbank.common.dto;

public record ApiResponse<T>(Boolean success, String message, T data) {

    public static <T> ApiResponse <T> ok(T data){
        return new ApiResponse<>(true,"ok",data);

    }
    public static <T> ApiResponse <T> ok(String message, T data){
        return new ApiResponse<>(true,message,data);
    }

    public static <T> ApiResponse <T> error(String message, T data){
        return new ApiResponse<>(false,message,data);

    }
    public static <T> ApiResponse <T> error(String message){
        return new ApiResponse<>(false,message,null);
    }
}
