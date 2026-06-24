package com.ssafy.wswg.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.wswg.model.dto.GroupCreateRequestDto;
import com.ssafy.wswg.model.dto.GroupDto;
import com.ssafy.wswg.model.dto.GroupFootprintDto;
import com.ssafy.wswg.model.dto.GroupInviteLinkDto;
import com.ssafy.wswg.model.dto.GroupJoinRequestDto;
import com.ssafy.wswg.model.dto.GroupMediaDto;
import com.ssafy.wswg.model.dto.GroupMediaRequest;
import com.ssafy.wswg.model.dto.GroupMemberAddRequestDto;
import com.ssafy.wswg.model.dto.GroupMemberDto;
import com.ssafy.wswg.model.dto.GroupUpdateRequestDto;
import com.ssafy.wswg.model.service.GroupService;
import com.ssafy.wswg.security.LoginUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupDto> createGroup(
            @LoginUserId Long userId,
            @RequestBody(required = false) GroupCreateRequestDto request) {
        GroupDto group = groupService.createGroup(userId, request);

        return ResponseEntity
                .created(URI.create("/api/groups/" + group.getGroupId()))
                .body(group);
    }

    @GetMapping
    public ResponseEntity<List<GroupDto>> readGroups(@LoginUserId Long userId) {
        return ResponseEntity.ok(groupService.readGroups(userId));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDto> readGroup(
            @LoginUserId Long userId,
            @PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.readGroup(groupId, userId));
    }

    @PatchMapping("/{groupId}")
    public ResponseEntity<GroupDto> updateGroup(
            @LoginUserId Long userId,
            @PathVariable Long groupId,
            @RequestBody(required = false) GroupUpdateRequestDto request) {
        return ResponseEntity.ok(groupService.updateGroup(groupId, userId, request));
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<GroupMemberDto>> readMembers(
            @LoginUserId Long userId,
            @PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.readMembers(groupId, userId));
    }

    @GetMapping("/{groupId}/footprint")
    public ResponseEntity<List<GroupFootprintDto>> readFootprints(
            @LoginUserId Long userId,
            @PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.readFootprints(groupId, userId));
    }

    @GetMapping("/{groupId}/media")
    public ResponseEntity<List<GroupMediaDto>> readMedia(
            @LoginUserId Long userId,
            @PathVariable Long groupId,
            @ModelAttribute GroupMediaRequest request) {
        return ResponseEntity.ok(groupService.readMedia(groupId, userId, request));
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<GroupMemberDto> addMember(
            @LoginUserId Long userId,
            @PathVariable Long groupId,
            @RequestBody(required = false) GroupMemberAddRequestDto request) {
        return ResponseEntity.ok(groupService.addMember(groupId, userId, request));
    }

    @PostMapping("/{groupId}/invite-link")
    public ResponseEntity<GroupInviteLinkDto> createInviteLink(
            @LoginUserId Long userId,
            @PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.createInviteLink(groupId, userId));
    }

    @PostMapping("/join")
    public ResponseEntity<GroupDto> joinGroup(
            @LoginUserId Long userId,
            @RequestBody(required = false) GroupJoinRequestDto request) {
        return ResponseEntity.ok(groupService.joinGroup(userId, request));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @LoginUserId Long userId,
            @PathVariable Long groupId) {
        groupService.deleteGroup(groupId, userId);
        return ResponseEntity.noContent().build();
    }
}
