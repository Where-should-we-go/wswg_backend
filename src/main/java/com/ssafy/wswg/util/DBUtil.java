 package com.ssafy.wswg.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBUtil {
	private static final String URL = "jdbc:mysql://localhost:3306/ssafy_trip?serverTimezone=Asia/Seoul&useSSL=false";
	private static final String USER = "ssafy";
	private static final String PASSWORD = "ssafy";
	
	static {
        try {
            // MySQL 8.x 버전용 드라이버 클래스 로딩
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("드라이버를 찾을 수 없습니다. 라이브러리를 확인하세요!");
            e.printStackTrace();
        }
    }
	
	public static Connection getConnection() throws SQLException{
		Properties properties = new Properties();
		properties.setProperty("user", USER);
		properties.setProperty("password", PASSWORD);
		properties.setProperty("profileSQL", "true");
		return DriverManager.getConnection(URL, properties);
		
	}
	
	public static void close(AutoCloseable... closeables) {
        for (AutoCloseable res : closeables) {
            if (res != null) { // null 체크 추가 권장
                try {
                    res.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
