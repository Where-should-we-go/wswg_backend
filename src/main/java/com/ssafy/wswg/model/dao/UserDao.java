package com.ssafy.wswg.model.dao;

import com.ssafy.wswg.model.dto.UserDto;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserDao {
    @Select("select id, email, name, profile_image_url, role from users where id = #{id}")
    UserDto findById(Long id);

    @Select("select id, email, name, profile_image_url, role from users where email = #{email}")
    UserDto findByEmail(String email);

    @Insert("insert into users (email, name, profile_image_url, role) values (#{email}, #{name}, #{profileImageUrl}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertUser(UserDto userDto);

    @Update("update users set name = #{name}, profile_image_url = #{profileImageUrl} where email = #{email}")
    void updateUser(UserDto userDto);
}
