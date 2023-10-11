package ru.skillbox.group39.telegrambot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.skillbox.group39.telegrambot.config.BotConfig;
import ru.skillbox.group39.telegrambot.dto.authenticate.AuthenticateDto;
import ru.skillbox.group39.telegrambot.dto.posts.PostWithUser;
import ru.skillbox.group39.telegrambot.feign.AdminPanelController;
import ru.skillbox.group39.telegrambot.feign.AuthorizationController;
import ru.skillbox.group39.telegrambot.service.actions.*;

import java.io.File;
import java.util.*;

import static ru.skillbox.group39.telegrambot.service.Constants.*;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private AuthenticateDto authenticateDto;
    private AuthorizationAction authorizationAction;
    private RegistrationAction registrationAction;
    private final MyPostsAction myPostsAction;
    private final FriendsAction friendsAction;
    private final MyProfileAction myProfileAction;
    private final AuthorizationController authorizationService;
    private final AdminPanelController adminPanelController;
    private final FriendsPostsAction friendsPostsAction;
    private static Map<Long, String> users = new HashMap<>();
    public final BotConfig config;

    public TelegramBot(MyPostsAction myPostsAction,
                       FriendsAction friendsAction,
                       BotConfig config,
                       AuthorizationController authorizationService,
                       MyProfileAction myProfileAction,
                       AdminPanelController adminPanelController,
                       FriendsPostsAction friendsPostsAction) {
        this.myPostsAction = myPostsAction;
        this.friendsAction = friendsAction;
        this.config = config;
        this.authorizationService = authorizationService;
        this.myProfileAction = myProfileAction;
        this.adminPanelController = adminPanelController;
        this.friendsPostsAction = friendsPostsAction;
        List<BotCommand> listOfCommands = KeyboardFactory.listOfCommands();
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (authorizationAction != null) {
            checkAuthorization(update);
            authorizationUser(update.getMessage().getChatId());
            return;
        }
        if (registrationAction != null) {
            checkRegistration(update);
            registration(update.getMessage().getChatId());
            return;
        }
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch (messageText) {
                case "/start":
                    startCommandReceived(chatId);
                    choose(chatId);
                    break;
                case "/info":
                case "info":
                    getHelp(chatId, INFO_TEXT);
                    break;
                case "my profile":
                case "/my_profile":
                    checkToken(chatId);
                    myProfile(chatId, users.get(chatId));
                    break;
                case "/registration":
                case "registration":
                    registrationAction = new RegistrationAction(authorizationService, adminPanelController);
                    registration(chatId);
                    break;
                case "/authorization":
                case "authorization":
                    authorizationAction = new AuthorizationAction(authorizationService);
                    authenticateDto = new AuthenticateDto();
                    authorizationUser(chatId);
                    break;
                case "/get_my_posts":
                case "get my posts":
                    getMyPosts(chatId);
                    break;
                case "/get_posts_friends":
                case "get posts friends":
                    getFriendsPosts(chatId);
                    break;
                case "/friends_list":
                case "friends list":
                    getFriendsList(chatId);
                    break;
                case "/exit":
                case "exit":
                    stopBot(chatId);
                    break;
                default:
                    mainMenu(chatId, NOT_FOUND);
                    checkToken(chatId);
                    break;
            }
        } else if (update.hasCallbackQuery()) {
            String callBackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            if (callBackData.equalsIgnoreCase("yes")) {
                users.put(chatId, null);
                startCommandReceived(chatId);
                choose(chatId);
            } else if (callBackData.equalsIgnoreCase("no")) {
                if (users.get(chatId) == null) {
                    choose(chatId);
                } else {
                    mainMenu(chatId, RETURN);
                }
            }
        }
    }

    private void getFriendsPosts(long chatId) {
        checkToken(chatId);
        List<PostWithUser> friendsPosts = friendsPostsAction.getFriendsPosts(users.get(chatId));
        if (friendsPosts.size() < 1) {
            sendMessages(chatId, POST_NOT_FOUND);
        } else {
            friendsPosts.forEach(p -> sendMessages(chatId, p.toString().replaceAll(REGEX, VOID)
                    .replaceAll(HTML_P[0], VOID)
                    .replaceAll(HTML_P[1], VOID)));
        }
    }

    private void getFriendsList(long chatId) {
        checkToken(chatId);
        sendMessages(chatId, friendsAction.getFriends(users.get(chatId)));
    }

    private synchronized void authorizationUser(Long chatId) {
        if (checkWord(authenticateDto.getEmail())) {
            sendMessages(chatId, ENTER_EMAIL);
            return;
        }
        if (checkWord(authenticateDto.getPassword())) {
            sendMessages(chatId, ENTER_PASSWORD);
            return;
        }
        authorizationAction.authenticateDto = authenticateDto;
        boolean isAuth = authorizationAction.isValid();
        if (isAuth) {
            users.put(chatId, authorizationAction.getToken());
            authorizationAction = null;
            mainMenu(chatId, AUTHENTICATION_SUCCESSFUL);
        } else {
            authorizationAction = null;
            sendMessages(chatId, USER_NOT_FOUND);
            choose(chatId);
        }
    }

    private void choose(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(CHOOSE);
        ReplyKeyboardMarkup keyboardMarkup = KeyboardFactory.choose();
        sendMessage.setReplyMarkup(keyboardMarkup);
        getExecute(sendMessage);
    }

    private void mainMenu(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);

        ReplyKeyboardMarkup keyboardMarkup = KeyboardFactory.mainMenu();
        sendMessage.setReplyMarkup(keyboardMarkup);
        getExecute(sendMessage);
    }

    private synchronized void registration(Long chatId) {
        if (registrationAction == null) {
            return;
        }
        if (checkWord(registrationAction.getRegistrationDto().getFirstName())) {
            sendMessages(chatId, ENTER_FIRST_NAME);
        } else if (checkWord(registrationAction.getRegistrationDto().getLastName())) {
            sendMessages(chatId, ENTER_LAST_NAME);
        } else if (checkWord(registrationAction.getRegistrationDto().getEmail())) {
            sendMessages(chatId, ENTER_EMAIL);
        } else if (checkWord(registrationAction.getRegistrationDto().getPassword())) {
            sendMessages(chatId, ENTER_PASSWORD);
        } else if (checkWord(registrationAction.getRegistrationDto().getCaptchaCode())) {
            sendMessages(chatId, WAITING_CAPTCHA);
            String captcha = registrationAction.getCaptcha();
            if (captcha.equals(CAPTCHA_NOT_WORK)) {
                sendMessages(chatId, CAPTCHA_NOT_WORK);
                choose(chatId);
                return;
            }
            sendMessages(chatId, ENTER_CAPTCHA);
            SendAnimation sendPhoto = new SendAnimation();
            sendPhoto.setChatId(chatId);
            sendPhoto.setAnimation(new InputFile(new File(captcha)));
            sendPhoto.setParseMode(SendAnimation.PARSEMODE_FIELD);
            try {
                execute(sendPhoto);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        } else if (registrationAction.isValid()) {
            registrationAction = null;
            sendMessages(chatId, REGISTRATION_SUCCESSFUL);
            choose(chatId);
        } else {
            sendMessages(chatId, REGISTRATION_FAIL);
            registrationAction = null;
            choose(chatId);
        }
    }

    private void getMyPosts(Long chatId) {
        checkToken(chatId);
        List<PostWithUser> myPosts = myPostsAction.getPosts(users.get(chatId));
        if (myPosts.size() < 1) {
            sendMessages(chatId, POST_NOT_FOUND);
        } else {
            myPosts.forEach(p -> sendMessages(chatId, p.toString().replaceAll(REGEX, VOID)
                    .replaceAll(HTML_P[0], VOID)
                    .replaceAll(HTML_P[1], VOID)));
        }
    }

    private void stopBot(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(ARE_YOU_SURE);
        InlineKeyboardMarkup markupInLine = KeyboardFactory.stopBot();
        message.setReplyMarkup(markupInLine);
        getExecute(message);
    }

    private void myProfile(Long chatId, String token) {
        sendMessages(chatId, myProfileAction.getMyProfile(token));
    }

    private void startCommandReceived(long chatId) {
        getHelp(chatId, WELCOME);
    }

    private void getHelp(long chatId, String text) {
        sendMessages(chatId, text);
    }

    private void getExecute(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }

    private void checkToken(Long chatId) {
        if (checkWord(users.get(chatId))) {
            choose(chatId);
        }
    }

    private void sendMessages(Long chatId, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        sendMessage.setParseMode(ParseMode.HTML);
        getExecute(sendMessage);
    }

    private boolean checkWord(String word) {
        return word == null || word.isEmpty();
    }

    private void checkRegistration(Update update) {
        if (checkWord(registrationAction.getRegistrationDto().getFirstName())) {
            registrationAction.getRegistrationDto().setFirstName(update.getMessage().getText());
        } else if (checkWord(registrationAction.getRegistrationDto().getLastName())) {
            registrationAction.getRegistrationDto().setLastName(update.getMessage().getText());
        } else if (checkWord(registrationAction.getRegistrationDto().getEmail())) {
            registrationAction.getRegistrationDto().setEmail(update.getMessage().getText());
        } else if (checkWord(registrationAction.getRegistrationDto().getPassword())) {
            registrationAction.getRegistrationDto().setConfirmPassword(update.getMessage().getText());
            registrationAction.getRegistrationDto().setPassword(update.getMessage().getText());
        } else if (checkWord(registrationAction.getRegistrationDto().getCaptchaCode())) {
// убрать потом
            registrationAction.getRegistrationDto().setCaptchaSecret(update.getMessage().getText());
// убрать потом
            if (registrationAction.isCaptcha(update.getMessage().getText())) {
                registrationAction.getRegistrationDto().setCaptchaCode(update.getMessage().getText());
            } else {
                if (registrationAction.count == 0) {
                    registrationAction = null;
                    choose(update.getMessage().getChatId());
                    return;
                }
                sendMessages(update.getMessage().getChatId(), CAPTCHA_IS_INCORRECT + (registrationAction.tryToCaptcha()));
            }
        }
    }

    private void checkAuthorization(Update update) {
        if (checkWord(authenticateDto.getEmail())) {
            authenticateDto.setEmail(update.getMessage().getText());
        } else if (checkWord(authenticateDto.getPassword())) {
            authenticateDto.setPassword(update.getMessage().getText());
        }
    }
}
