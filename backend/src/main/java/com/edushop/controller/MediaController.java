package com.edushop.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {
  private final Path root;
  private final JdbcTemplate jdbc;

  public MediaController(
      JdbcTemplate jdbc, @Value("${edushop.media.dir:../.local/uploads}") String directory) {
    this.jdbc = jdbc;
    this.root = Path.of(directory).toAbsolutePath().normalize();
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> upload(
      @AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file)
      throws java.io.IOException {
    if (file.isEmpty() || file.getSize() > 8 * 1024 * 1024)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn ảnh JPG/PNG không quá 8 MB.");
    byte[] source = file.getBytes();
    if (!((source.length > 8
            && source[0] == (byte) 0x89
            && source[1] == 0x50
            && source[2] == 0x4e
            && source[3] == 0x47)
        || (source.length > 3 && source[0] == (byte) 0xff && source[1] == (byte) 0xd8)))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ ảnh JPG hoặc PNG hợp lệ.");
    // Read dimensions before decoding to avoid decompression bombs.
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext())
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được ảnh.");
      var reader = readers.next();
      try {
        reader.setInput(stream);
        if ((long) reader.getWidth(0) * reader.getHeight(0) > 24000000)
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "Ảnh quá lớn, tối đa 24 megapixel.");
      } finally {
        reader.dispose();
      }
    }
    var image = ImageIO.read(new ByteArrayInputStream(source));
    if (image == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh không hợp lệ.");
    var output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    byte[] bytes = output.toByteArray();
    if (bytes.length > 20 * 1024 * 1024)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh sau xử lý quá lớn.");
    String id = UUID.randomUUID().toString(), name = id + ".png";
    Files.createDirectories(root);
    Path target = root.resolve(name);
    Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
    String original = Objects.toString(file.getOriginalFilename(), "image");
    original = original.substring(0, Math.min(original.length(), 255));
    try {
      jdbc.update(
          "INSERT INTO"
              + " media_assets(id,owner_user_id,original_name,storage_name,content_type,byte_size)"
              + " VALUES (?,?,?,?,?,?)",
          id,
          Long.parseLong(jwt.getSubject()),
          original,
          name,
          "image/png",
          bytes.length);
    } catch (RuntimeException e) {
      Files.deleteIfExists(target);
      throw e;
    }
    return Map.of("id", id, "url", "/api/v1/media/" + id, "size", bytes.length);
  }

  @GetMapping("/{id}")
  public ResponseEntity<org.springframework.core.io.Resource> get(@PathVariable String id) {
    try {
      UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    var rows =
        jdbc.queryForList("SELECT storage_name FROM media_assets WHERE id=?", String.class, id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    Path path = root.resolve(rows.getFirst()).normalize();
    if (!path.startsWith(root) || !Files.isRegularFile(path))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)))
        .body(new org.springframework.core.io.FileSystemResource(path));
  }
}
