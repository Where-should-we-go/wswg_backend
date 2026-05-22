<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <title>명소 상세 정보</title>
</head>
<body>
    <h2>${attraction.title} 상세 정보</h2>
    <c:if test="${not empty attraction.firstImage1}">
        <img src="${attraction.firstImage1}" style="max-width: 500px;"><br>
    </c:if>
    <ul>
        <li>번호: ${attraction.no}</li>
        <li>주소: ${attraction.addr1} ${attraction.addr2}</li>
        <li>전화번호: ${attraction.tel}</li>
        <li>홈페이지: ${attraction.homepage}</li>
        <li>개요: <p>${attraction.overview}</p></li>
    </ul>
    <hr>
    <a href="list">목록으로</a>
    <a href="mvUpdate?no=${attraction.no}">수정하기</a>
    <a href="delete?no=${attraction.no}" onclick="return confirm('삭제하시겠습니까?');">삭제하기</a>
</body>
</html>