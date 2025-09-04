// Utility to generate bcrypt hashes for test passwords
package vn.com.fecredit.chunkedupload.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptGenTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println("user: " + encoder.encode("password"));
    }
}