<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <title>명소 등록</title>
</head>
<body>
    <h2>새 명소 등록</h2>
    <form action="register" method="post">
        명칭: <input type="text" name="title" required><br>
        콘텐츠ID: <input type="number" name="contentId"><br>
        유형ID: <input type="number" name="contentTypeId"><br>
        시도코드: <input type="number" name="areaCode"><br>
        구군코드: <input type="number" name="siGunGuCode"><br>
        이미지URL: <input type="text" name="firstImage1"><br>
        주소: <input type="text" name="addr1"><br>
        상세주소: <input type="text" name="addr2"><br>
        전화번호: <input type="text" name="tel"><br>
        개요: <br>
        <textarea name="overview" rows="5" cols="40"></textarea><br>
        <button type="submit">등록</button>
        <button type="button" onclick="history.back()">취소</button>
    </form>
</body>
</html>