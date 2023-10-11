package ru.skillbox.group39.telegrambot.service.actions;

import org.springframework.stereotype.Component;
import ru.skillbox.group39.telegrambot.feign.UserController;

import static ru.skillbox.group39.telegrambot.service.Constants.BEARER;
import static ru.skillbox.group39.telegrambot.service.Constants.UNAUTHORIZED;

@Component
public class MyProfileAction {
    private UserController userService;

    public MyProfileAction(UserController userService) {
        this.userService = userService;
    }

    public String getMyProfile(String token) {
        try {
            return userService.getAccountWhenLogin(BEARER + token).toString();
        } catch (Exception e) {
            return UNAUTHORIZED;
        }
    }
}
