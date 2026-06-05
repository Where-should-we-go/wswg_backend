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
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        String email = getEmail(registrationId, attributes);
        String name = getName(registrationId, attributes);
        String profileImageUrl = getProfileImageUrl(registrationId, attributes);

        UserDto userDto = userDao.findByEmail(email);

        if(userDto == null) {
            userDto = UserDto.builder()
                    .email(email)
                    .name(name)
                    .profileImageUrl(profileImageUrl)
                    .role(Role.USER).build();
            userDao.insertUser(userDto);
        } else {
            userDto.setName(name);
            userDto.setProfileImageUrl(profileImageUrl);
            userDao.updateUser(userDto);
        }

        return new CustomOAuth2User(attributes, userDto);
    }

    @SuppressWarnings("unchecked")
    private String getEmail(String registrationId, Map<String, Object> attributes) {
        if ("kakao".equals(registrationId)) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            String email = kakaoAccount == null ? null : (String) kakaoAccount.get("email");

            if (email != null && !email.isBlank()) {
                return email;
            }

            return attributes.get("id") + "@kakao.oauth";
        }

        return (String) attributes.get("email");
    }

    @SuppressWarnings("unchecked")
    private String getName(String registrationId, Map<String, Object> attributes) {
        if ("kakao".equals(registrationId)) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> profile = kakaoAccount == null ? null : (Map<String, Object>) kakaoAccount.get("profile");
            String nickname = profile == null ? null : (String) profile.get("nickname");

            if (nickname != null && !nickname.isBlank()) {
                return nickname;
            }

            return "kakao_" + attributes.get("id");
        }

        return (String) attributes.get("name");
    }

    @SuppressWarnings("unchecked")
    private String getProfileImageUrl(String registrationId, Map<String, Object> attributes) {
        if ("kakao".equals(registrationId)) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> profile = kakaoAccount == null ? null : (Map<String, Object>) kakaoAccount.get("profile");

            return profile == null ? null : (String) profile.get("profile_image_url");
        }

        return (String) attributes.get("picture");
    }
}
