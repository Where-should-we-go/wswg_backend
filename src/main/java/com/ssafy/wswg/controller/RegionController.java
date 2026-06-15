package com.ssafy.wswg.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.wswg.model.dto.ContentTypeDto;
import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.dto.SidoDto;
import com.ssafy.wswg.model.service.RegionService;

import lombok.RequiredArgsConstructor;

/**
 * 검색 화면(S3) 필터용 참조 데이터 조회 컨트롤러.
 * 시/도, 시군구, 콘텐츠타입(테마) 목록을 제공한다. 모두 인증 필요.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RegionController {
    private final RegionService regionService;

    @GetMapping("/sidos")
    public ResponseEntity<List<SidoDto>> getSidos() {
        return ResponseEntity.ok(regionService.getSidos());
    }

    @GetMapping("/guguns")
    public ResponseEntity<List<GugunDto>> getGuguns(@RequestParam int sidoCode) {
        return ResponseEntity.ok(regionService.getGuguns(sidoCode));
    }

    @GetMapping("/content-types")
    public ResponseEntity<List<ContentTypeDto>> getContentTypes() {
        return ResponseEntity.ok(regionService.getContentTypes());
    }
}
