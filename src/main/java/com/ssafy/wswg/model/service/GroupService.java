package com.ssafy.wswg.model.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.GroupDao;
import com.ssafy.wswg.model.dto.GroupCreateRequestDto;
import com.ssafy.wswg.model.dto.GroupDto;
import com.ssafy.wswg.model.dto.GroupUpdateRequestDto;

@Service
@Transactional(readOnly = true)
public class GroupService {
    private final GroupDao groupDao;

    public GroupService(GroupDao groupDao) {
        this.groupDao = groupDao;
    }

    @Transactional
    public GroupDto createGroup(Long userId, GroupCreateRequestDto request) {
        GroupDto group = new GroupDto();
        group.setGroupName(normalizeGroupName(request == null ? null : request.getGroupName()));
        group.setOwnerId(userId);

        groupDao.createGroup(group);
        groupDao.addMember(group.getGroupId(), userId);

        return readGroup(group.getGroupId(), userId);
    }

    public List<GroupDto> readGroups(Long userId) {
        return groupDao.readGroupsByUserId(userId);
    }

    public GroupDto readGroup(Long groupId, Long userId) {
        GroupDto group = groupDao.readGroupById(groupId, userId);
        if (group == null) {
            throw new CommonException(ErrorCode.NOT_FOUND_GROUP);
        }

        return group;
    }

    @Transactional
    public GroupDto updateGroup(Long groupId, Long userId, GroupUpdateRequestDto request) {
        String normalizedGroupName = normalizeGroupName(request == null ? null : request.getGroupName());

        readGroup(groupId, userId);
        if (!isOwner(groupId, userId)) {
            throw new CommonException(ErrorCode.GROUP_OWNER_REQUIRED);
        }

        int updatedCount = groupDao.updateGroupName(groupId, normalizedGroupName);
        if (updatedCount == 0) {
            throw new CommonException(ErrorCode.NOT_FOUND_GROUP);
        }

        return readGroup(groupId, userId);
    }

    @Transactional
    public void deleteGroup(Long groupId, Long userId) {
        readGroup(groupId, userId);
        if (!isOwner(groupId, userId)) {
            throw new CommonException(ErrorCode.GROUP_OWNER_REQUIRED);
        }

        if (groupDao.deleteGroup(groupId) == 0) {
            throw new CommonException(ErrorCode.NOT_FOUND_GROUP);
        }
    }

    private boolean isOwner(Long groupId, Long userId) {
        return groupDao.countGroupOwner(groupId, userId) > 0;
    }

    private String normalizeGroupName(String groupName) {
        if (groupName == null) {
            throw new CommonException(ErrorCode.INVALID_GROUP_NAME);
        }

        String trimmed = groupName.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new CommonException(ErrorCode.INVALID_GROUP_NAME);
        }

        return trimmed;
    }
}
