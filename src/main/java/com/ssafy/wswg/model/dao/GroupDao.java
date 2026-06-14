package com.ssafy.wswg.model.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ssafy.wswg.model.dto.GroupDto;

@Mapper
public interface GroupDao {
    int createGroup(GroupDto group);

    int addMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    List<GroupDto> readGroupsByUserId(Long userId);

    GroupDto readGroupById(@Param("groupId") Long groupId, @Param("userId") Long userId);

    int countGroupOwner(@Param("groupId") Long groupId, @Param("userId") Long userId);

    int updateGroupName(@Param("groupId") Long groupId, @Param("groupName") String groupName);

    int deleteGroup(Long groupId);
}
