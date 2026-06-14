package com.ssafy.wswg;

import com.ssafy.wswg.model.dao.UserDao;
import com.ssafy.wswg.model.dto.Role;
import com.ssafy.wswg.model.dto.UserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T7: UserDao(MyBatis) 라운드트립이 실제 PostgreSQL에서 동작하는지 검증.
 * insertUser -> @Options(useGeneratedKeys)로 id 채워짐 -> findByEmail로 재조회.
 */
class UserDaoPgTest extends AbstractPostgisIntegrationTest {

    @Autowired
    UserDao userDao;

    @Test
    @DisplayName("T7: insertUser 후 findByEmail 라운드트립이 PG에서 동작")
    void t7_insertThenFindByEmail() {
        UserDto user = UserDto.builder()
                .email("roundtrip@example.com")
                .name("라운드트립")
                .profileImageUrl("https://example.com/p.png")
                .role(Role.USER)
                .build();

        userDao.insertUser(user);

        // useGeneratedKeys로 PK가 DTO에 채워졌는지
        assertThat(user.getId()).isNotNull();

        UserDto found = userDao.findByEmail("roundtrip@example.com");
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(user.getId());
        assertThat(found.getEmail()).isEqualTo("roundtrip@example.com");
        assertThat(found.getName()).isEqualTo("라운드트립");
        assertThat(found.getProfileImageUrl()).isEqualTo("https://example.com/p.png");
        assertThat(found.getRole()).isEqualTo(Role.USER);
    }
}
