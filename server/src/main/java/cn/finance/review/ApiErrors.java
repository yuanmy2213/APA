package cn.finance.review;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(ApiError.class)public ResponseEntity<Object> error(ApiError e){return ResponseEntity.status(e.status).body(Db.map("code",e.code,"message",e.getMessage(),"requestId",Db.id()));}
 @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)public ResponseEntity<Object> size(Exception e){return ResponseEntity.status(413).body(Db.map("code","FILE_SIZE","message","文件超过50MB限制"));}
 @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.bind.MissingRequestHeaderException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})public ResponseEntity<Object> bad(Exception e){return ResponseEntity.unprocessableEntity().body(Db.map("code","INVALID_REQUEST","message","请求参数缺失或格式无效"));}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)public ResponseEntity<Object> conflict(Exception e){return ResponseEntity.status(409).body(Db.map("code","DATA_CONFLICT","message","数据发生冲突，请刷新版本并检查字段长度与重复记录"));}
 @ExceptionHandler(Exception.class)public ResponseEntity<Object> unexpected(Exception e){String id=Db.id();org.slf4j.LoggerFactory.getLogger(ApiErrors.class).error("request {} failed",id,e);return ResponseEntity.internalServerError().body(Db.map("code","INTERNAL_ERROR","message","操作失败，请重试或联系管理员","requestId",id));}
}
