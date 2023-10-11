package ru.skillbox.group39.telegrambot.service.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.skillbox.group39.telegrambot.dto.account.AccountSecureDto;
import ru.skillbox.group39.telegrambot.dto.authenticate.RegistrationDto;
import ru.skillbox.group39.telegrambot.feign.AdminPanelController;
import ru.skillbox.group39.telegrambot.feign.AuthorizationController;

import static ru.skillbox.group39.telegrambot.service.Constants.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

@Slf4j
@Component
public class RegistrationAction {
    AuthorizationController authorizationController;
    AdminPanelController adminPanelController;
    public RegistrationDto registrationDto;
    ObjectMapper objectMapper;
    public int count = 3;

    public RegistrationAction(AuthorizationController authorizationController,
                              AdminPanelController adminPanelController) {
        objectMapper = new ObjectMapper();
        this.authorizationController = authorizationController;
        registrationDto = new RegistrationDto();
        this.adminPanelController = adminPanelController;
        getCaptcha();
    }

    public String getCaptcha() {
        try {
            String captcha = authorizationController.captcha();
            registrationDto.setCaptchaSecret(captcha);
            byte[] img = Base64.getDecoder().decode(captcha);
            Files.write(Paths.get(PATH), img);
            return PATH;
        } catch (Exception e) {
            log.error(e.getMessage());
            return CAPTCHA_NOT_WORK;
        }
    }

    public boolean isCaptcha(String captcha) {
        return registrationDto.getCaptchaSecret().equals(captcha);
    }

    public int tryToCaptcha() {
        return count--;
    }

    public boolean isValid() {
        Object response;
        try {
            log.info(registrationDto.toString());
            response = authorizationController.register(registrationDto);
        } catch (Exception e) {
            log.info(e.getMessage());
            return false;
        }
        AccountSecureDto authenticateResponseDto = objectMapper.convertValue(response, AccountSecureDto.class);
        log.info(response.toString());
        log.info(authenticateResponseDto.toString());
        log.info(registrationDto.toString());
        return true;
    }

    public RegistrationDto getRegistrationDto() {
        return registrationDto;
    }
}
