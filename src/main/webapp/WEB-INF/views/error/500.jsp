<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>서버 오류 - 500</title>
    <style>
        body { font-family: 'Arial', sans-serif; text-align: center; padding: 100px; color: #333; background: #fff; }
        .icon { font-size: 80px; color: #f39c12; }
        h1 { font-size: 40px; margin: 20px 0; }
        p { font-size: 18px; color: #777; line-height: 1.6; }
        .error-detail { margin-top: 20px; padding: 15px; background: #eee; font-family: monospace; display: inline-block; text-align: left; }
    </style>
</head>
<body>
    <div class="icon">⚠️</div>
    <h1>서버에 문제가 발생했습니다.</h1>
    <p>불편을 드려 죄송합니다.<br>잠시 후 다시 시도해 주세요. 시스템 관리자가 문제를 파악 중입니다.</p>
    
    <!-- 개발 단계에서 에러 원인을 확인하고 싶을 때 (실무에서는 권한 있는 사람에게만 노출) -->
    <div class="error-detail" th:if="${exception}">
        <strong>Error Message:</strong> <span th:text="${exception.message}">Error</span>
    </div>

    <br>
    <a href="javascript:history.back()" style="color: #3498db; text-decoration: none;">이전 페이지로 이동</a>
</body>
</html>