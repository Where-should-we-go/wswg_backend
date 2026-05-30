package com.ssafy.wswg.security;

import com.ssafy.wswg.model.dao.UserDao;
import com.ssafy.wswg.model.dto.Role;
import com.ssafy.wswg.model.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserDao userDao;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        UserDto userDto = userDao.findByEmail(email);

        if(userDto == null) {
            userDto = UserDto.builder()
                    .email(email)
                    .name(name)
                    .role(Role.USER).build();
            userDao.insertUser(userDto);
        } else {
            userDto.setName(name);
            userDao.updateUser(userDto);
        }

        return new CustomOAuth2User(attributes, userDto);
    }
}
