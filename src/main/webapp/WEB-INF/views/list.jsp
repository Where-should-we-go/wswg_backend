<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <title>관광 명소 목록</title>
</head>
<body>
    <h2>관광 명소 목록</h2>
    <a href="mvRegister">새 명소 등록</a>
    <table border="1">
        <thead>
            <tr>
                <th>번호</th>
                <th>이미지</th>
                <th>명칭</th>
                <th>주소</th>
                <th>전화번호</th>
                <th>관리</th>
            </tr>
        </thead>
        <tbody>
            <c:forEach var="item" items="${attractions}">
                <tr>
                    <td>${item.no}</td>
                    <td><img src="${item.firstImage1}" width="100"></td>
                    <td><a href="detail?no=${item.no}">${item.title}</a></td>
                    <td>${item.addr1} ${item.addr2}</td>
                    <td>${item.tel}</td>
                    <td>
                        <a href="mvUpdate?no=${item.no}">수정</a>
                        <a href="delete?no=${item.no}" onclick="return confirm('정말 삭제하시겠습니까?');">삭제</a>
                    </td>
                </tr>
            </c:forEach>
        </tbody>
    </table>
</body>
</html>