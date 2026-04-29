package api.imgfilters;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;

@Service
public class ImageService {
    public byte[] applyFilters(MultipartFile file, Image.FilterOptions options, String format) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }

        BufferedImage source = Image.read(file.getBytes());
        BufferedImage filtered = Image.applyFilters(source, options);
        return Image.write(filtered, format);
    }
}
