<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <title>명소 정보 수정</title>
</head>
<body>
    <h2>명소 정보 수정</h2>
    <form action="modify" method="post">
        <%-- 수정을 위해 고유 번호(PK)를 hidden으로 전달 --%>
        <input type="hidden" name="no" value="${attraction.no}">
        
        명칭: <input type="text" name="title" value="${attraction.title}" required><br>
        콘텐츠ID: <input type="number" name="contentId" value="${attraction.contentId}"><br>
        이미지URL: <input type="text" name="firstImage1" value="${attraction.firstImage1}"><br>
        주소: <input type="text" name="addr1" value="${attraction.addr1}"><br>
        상세주소: <input type="text" name="addr2" value="${attraction.addr2}"><br>
        전화번호: <input type="text" name="tel" value="${attraction.tel}"><br>
        홈페이지: <input type="text" name="homepage" value="${attraction.homepage}"><br>
        개요: <br>
        <textarea name="overview" rows="5" cols="40">${attraction.overview}</textarea><br>
        
        <button type="submit">수정 완료</button>
        <button type="button" onclick="history.back()">취소</button>
    </form>
</body>
</html>