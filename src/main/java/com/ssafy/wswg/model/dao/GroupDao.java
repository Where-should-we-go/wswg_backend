package com.ssafy.wswg.model.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ssafy.wswg.model.dto.GroupDto;
import com.ssafy.wswg.model.dto.GroupFootprintDto;
import com.ssafy.wswg.model.dto.GroupMediaDto;
import com.ssafy.wswg.model.dto.GroupMediaRequest;
import com.ssafy.wswg.model.dto.GroupMemberDto;

@Mapper
public interface GroupDao {
    int createGroup(GroupDto group);

    int addMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    List<GroupDto> readGroupsByUserId(Long userId);

    GroupDto readGroupById(@Param("groupId") Long groupId, @Param("userId") Long userId);

    int countGroupById(@Param("groupId") Long groupId);

    int countGroupMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    int countGroupOwner(@Param("groupId") Long groupId, @Param("userId") Long userId);

    int countUserById(@Param("userId") Long userId);

    List<GroupMemberDto> readMembers(@Param("groupId") Long groupId);

    List<GroupFootprintDto> readFootprints(@Param("groupId") Long groupId);

    List<GroupMediaDto> readMedia(@Param("groupId") Long groupId, @Param("request") GroupMediaRequest request);

    int updateGroupName(@Param("groupId") Long groupId, @Param("groupName") String groupName);

    int deleteGroup(Long groupId);
}
