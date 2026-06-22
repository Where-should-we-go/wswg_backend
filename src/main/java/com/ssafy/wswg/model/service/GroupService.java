package com.ssafy.wswg.model.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.GroupDao;
import com.ssafy.wswg.model.dto.GroupCreateRequestDto;
import com.ssafy.wswg.model.dto.GroupDto;
import com.ssafy.wswg.model.dto.GroupFootprintDto;
import com.ssafy.wswg.model.dto.GroupInviteLinkDto;
import com.ssafy.wswg.model.dto.GroupJoinRequestDto;
import com.ssafy.wswg.model.dto.GroupMemberAddRequestDto;
import com.ssafy.wswg.model.dto.GroupMemberDto;
import com.ssafy.wswg.model.dto.GroupUpdateRequestDto;

@Service
@Transactional(readOnly = true)
public class GroupService {
    private static final String INVITE_KEY_PREFIX = "group_invite:";
    private static final int INVITE_TOKEN_BYTE_LENGTH = 32;
    private static final int INVITE_EXPIRES_HOURS = 24;

    private final GroupDao groupDao;
    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public GroupService(GroupDao groupDao, StringRedisTemplate stringRedisTemplate) {
        this.groupDao = groupDao;
        this.stringRedisTemplate = stringRedisTemplate;
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

    public List<GroupMemberDto> readMembers(Long groupId, Long userId) {
        validateMember(groupId, userId);

        return groupDao.readMembers(groupId);
    }

    public List<GroupFootprintDto> readFootprints(Long groupId, Long userId) {
        validateMember(groupId, userId);

        return groupDao.readFootprints(groupId);
    }

    @Transactional
    public GroupMemberDto addMember(Long groupId, Long userId, GroupMemberAddRequestDto request) {
        validateOwner(groupId, userId);

        Long targetUserId = request == null ? null : request.getUserId();
        if (targetUserId == null || groupDao.countUserById(targetUserId) == 0) {
            throw new CommonException(ErrorCode.NOT_FOUND_USER);
        }

        groupDao.addMember(groupId, targetUserId);

        return groupDao.readMembers(groupId).stream()
                .filter(member -> targetUserId.equals(member.getUserId()))
                .findFirst()
                .orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND_USER));
    }

    @Transactional
    public GroupInviteLinkDto createInviteLink(Long groupId, Long userId) {
        validateOwner(groupId, userId);

        GroupInviteLinkDto inviteLink = new GroupInviteLinkDto();
        inviteLink.setGroupId(groupId);
        inviteLink.setToken(generateInviteToken());
        inviteLink.setCreatedBy(userId);
        inviteLink.setExpiresAt(OffsetDateTime.now().plusHours(INVITE_EXPIRES_HOURS));

        stringRedisTemplate.opsForValue().set(
                getInviteKey(inviteLink.getToken()),
                String.valueOf(groupId),
                Duration.ofHours(INVITE_EXPIRES_HOURS));

        return inviteLink;
    }

    @Transactional
    public GroupDto joinGroup(Long userId, GroupJoinRequestDto request) {
        String token = normalizeToken(request == null ? null : request.getToken());
        String groupIdValue = stringRedisTemplate.opsForValue().get(getInviteKey(token));
        if (groupIdValue == null) {
            throw new CommonException(ErrorCode.INVALID_INVITE_TOKEN);
        }

        Long groupId = Long.parseLong(groupIdValue);
        validateGroupExists(groupId);

        groupDao.addMember(groupId, userId);

        return readGroup(groupId, userId);
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

    private void validateMember(Long groupId, Long userId) {
        validateGroupExists(groupId);
        if (groupDao.countGroupMember(groupId, userId) == 0) {
            throw new CommonException(ErrorCode.GROUP_MEMBER_REQUIRED);
        }
    }

    private void validateOwner(Long groupId, Long userId) {
        validateGroupExists(groupId);
        if (!isOwner(groupId, userId)) {
            throw new CommonException(ErrorCode.GROUP_OWNER_REQUIRED);
        }
    }

    private void validateGroupExists(Long groupId) {
        if (groupId == null || groupDao.countGroupById(groupId) == 0) {
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

    private String normalizeToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_INVITE_TOKEN);
        }

        return token.trim();
    }

    private String generateInviteToken() {
        byte[] randomBytes = new byte[INVITE_TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private String getInviteKey(String token) {
        return INVITE_KEY_PREFIX + token;
    }
}
