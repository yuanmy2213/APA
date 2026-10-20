package cn.finance.review;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
@Component
public class FileStore {
 final Path root;
 public FileStore(@Value("${audit.storage}")String root)throws IOException{this.root=Path.of(root).toAbsolutePath().normalize();Files.createDirectories(this.root);}
 public Map<String,Object> save(MultipartFile file)throws Exception{
  String original=Objects.requireNonNullElse(file.getOriginalFilename(),"upload.xlsx").replace('\\','/');original=original.substring(original.lastIndexOf('/')+1);ApiError.require(original.length()<=200,422,"FILE_NAME","文件名过长");
  ApiError.require(original.toLowerCase(Locale.ROOT).matches(".*\\.(xlsx|xls|csv|json)$"),422,"FILE_TYPE","仅支持 XLSX、XLS、CSV 或标准 JSON 文件");
  String id=Db.id(),name=id+original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT);var digest=MessageDigest.getInstance("SHA-256");long bytes=0;
  try(InputStream in=file.getInputStream();OutputStream out=Files.newOutputStream(path(name),StandardOpenOption.CREATE_NEW)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))>=0){bytes+=n;if(bytes>50L*1024*1024)throw new ApiError(413,"FILE_SIZE","文件超过50MB");digest.update(buf,0,n);out.write(buf,0,n);}}
  catch(Exception e){Files.deleteIfExists(path(name));throw e;}
  return Db.map("id",id,"originalName",original,"storageName",name,"sha256",HexFormat.of().formatHex(digest.digest()),"sizeBytes",bytes);
 }
 public Path path(String name){ApiError.require(name.matches("[a-zA-Z0-9_.-]{1,120}"),400,"FILE_PATH","文件标识无效");Path p=root.resolve(name).normalize();ApiError.require(p.getParent().equals(root),400,"FILE_PATH","文件标识无效");return p;}
}
