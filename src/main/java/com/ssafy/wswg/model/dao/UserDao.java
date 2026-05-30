package com.ssafy.wswg.model.dao;

import com.ssafy.wswg.model.dto.UserDto;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserDao {
    @Select("select id, email, name, role from users where email = #{email}")
    UserDto findByEmail(String email);

    @Insert("insert into users (email, name, role) values (#{email}, #{name}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertUser(UserDto userDto);

    @Update("update users set name= #{name} where email = #{email}")
    void updateUser(UserDto userDto);
}
