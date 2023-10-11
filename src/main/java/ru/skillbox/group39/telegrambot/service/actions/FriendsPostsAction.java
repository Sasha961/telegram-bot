package ru.skillbox.group39.telegrambot.service.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.skillbox.group39.telegrambot.config.RestResponsePage;
import ru.skillbox.group39.telegrambot.dto.account.AccountDtoLite;
import ru.skillbox.group39.telegrambot.dto.posts.Post;
import ru.skillbox.group39.telegrambot.dto.posts.PostWithUser;
import ru.skillbox.group39.telegrambot.feign.FriendsController;
import ru.skillbox.group39.telegrambot.feign.PostsController;
import ru.skillbox.group39.telegrambot.feign.UserController;
import ru.skillbox.group39.telegrambot.service.security.JwtUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ru.skillbox.group39.telegrambot.service.Constants.*;

@Component
@RequiredArgsConstructor
public class FriendsPostsAction {
    private final PostsController postsController;
    private final FriendsController friendsController;
    private final UserController userController;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    public List<PostWithUser> getFriendsPosts(String token) {
        Long id = jwtUtil.getUserId(token);
        List<Long> friendsList = friendsController.getFriendById(BEARER + token, id);
        friendsList.add(102L);
        List<Post> myPosts = new ArrayList<>();
        for (Long friend : friendsList) {
            RestResponsePage<Post> posts = postsController.postsGet(friend);
            myPosts.addAll(posts.toList());
        }
        List<PostWithUser> myPostLis = new ArrayList<>();
        for (Post post : myPosts) {
            PostWithUser postWithUser = objectMapper.convertValue(post, PostWithUser.class);
            String userName;
            try {
                userName = objectMapper
                        .convertValue(userController.getAccountById(token, post.getAuthorId()), AccountDtoLite.class)
                        .toString();
            } catch (Exception e) {
                userName = NOT_FOUND;
            }
            postWithUser.setAuthorName(userName);
            postWithUser.setPostText(HTML_I[0] + post.getPostText() + HTML_I[1]);
            postWithUser.setTitle(HTML_B[0] + postWithUser.getTitle() + HTML_B[1]);
            postWithUser.setAuthorName(HTML_U[0] + postWithUser.getAuthorName() + HTML_U[1]);
            myPostLis.add(postWithUser);
        }
        Collections.reverse(myPostLis);
        return myPostLis;
    }
}
