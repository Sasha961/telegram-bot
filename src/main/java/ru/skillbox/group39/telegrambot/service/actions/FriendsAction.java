package ru.skillbox.group39.telegrambot.service.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.skillbox.group39.telegrambot.dto.account.AccountDtoLite;
import ru.skillbox.group39.telegrambot.feign.FriendsController;
import ru.skillbox.group39.telegrambot.feign.UserController;
import ru.skillbox.group39.telegrambot.service.security.JwtUtil;

import java.util.List;
import java.util.stream.Collectors;

import static ru.skillbox.group39.telegrambot.service.Constants.*;

@Component
@RequiredArgsConstructor
public class FriendsAction {
    private String token;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final FriendsController friendsController;
    private final UserController userController;

    public String getFriends(String token) {
        this.token = token;
        Long id = jwtUtil.getUserId(token);
        List<Long> friendById = friendsController.getFriendById(BEARER + token, id);
        System.out.println(friendById.toString());
        if (friendById.size() < 1) {
            return NOT_FRIENDS;
        }
        return convertValue(friendById);
    }

    private String convertValue(List<Long> friendListId) {
        return "You have " + friendListId.size() + " friend(s): \n" + friendListId.stream()
                .map(id -> userController.getAccountById(BEARER + token, id))
                .map(accountDto -> objectMapper.convertValue(accountDto, AccountDtoLite.class))
                .collect(Collectors.toList()).toString().replaceAll(REGEX, VOID);
    }
}
