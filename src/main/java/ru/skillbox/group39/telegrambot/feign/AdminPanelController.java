package ru.skillbox.group39.telegrambot.feign;

import feign.Headers;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.skillbox.group39.telegrambot.config.FeignConfig;
import ru.skillbox.group39.telegrambot.dto.StorageDto;

@FeignClient(name = "AdminPanel", url = "http://5.63.154.191:8082/api/v1/admin-console", configuration = FeignConfig.class)
public interface AdminPanelController {

    @PostMapping(value = "/storage",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Headers("Content-Type: multipart/form-data")
    StorageDto storage(@RequestPart MultipartFile file) throws Exception;
}

