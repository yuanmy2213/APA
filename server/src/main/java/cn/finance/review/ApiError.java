package cn.finance.review;
public class ApiError extends RuntimeException {
 public final int status; public final String code;
 public ApiError(int status,String code,String message){super(message);this.status=status;this.code=code;}
 public static void require(boolean ok,int status,String code,String msg){if(!ok)throw new ApiError(status,code,msg);}
}
