package api.imgfilters;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/images")
public class ImageController {
    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping(value = "/filter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> filter(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean grayscale,
            @RequestParam(defaultValue = "0") int brightness,
            @RequestParam(defaultValue = "1.0") double contrast,
            @RequestParam(defaultValue = "0") int blurRadius,
            @RequestParam(defaultValue = "0.0") double sharpenAmount,
            @RequestParam(defaultValue = "png") String format
    ) throws IOException {
        Image.FilterOptions options = new Image.FilterOptions(
                grayscale,
                brightness,
                contrast,
                blurRadius,
                sharpenAmount
        );
        String normalizedFormat = Image.normalizeFormat(format);
        byte[] body = imageService.applyFilters(file, options, normalizedFormat);

        return ResponseEntity.ok()
                .contentType(mediaTypeFor(normalizedFormat))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("filtered." + extensionFor(normalizedFormat))
                        .build()
                        .toString())
                .body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    private static MediaType mediaTypeFor(String format) {
        return switch (format) {
            case "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "bmp" -> MediaType.valueOf("image/bmp");
            default -> MediaType.IMAGE_PNG;
        };
    }

    private static String extensionFor(String format) {
        return "jpeg".equals(format) ? "jpg" : format;
    }
}
