package mapzip.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.junit.jupiter.api.Assertions.*;

class XssProtectionFilterTest {

    private final XssProtectionFilter filter = new XssProtectionFilter();

    @Test
    void testJsonBodySanitization() {
        String maliciousJson = "{\"name\":\"<script>alert('xss')</script>John\",\"message\":\"Hello <b>world</b>\"}";
        
        MockServerHttpRequest request = MockServerHttpRequest.post("/test")
                .contentType(MediaType.APPLICATION_JSON)
                .body(maliciousJson);
        
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // JSON 파싱 테스트를 위한 직접 호출
        String result = filter.sanitizeJsonBody(maliciousJson);
        
        assertFalse(result.contains("<script>"));
        assertTrue(result.contains("John"));
        assertTrue(result.contains("<b>world</b>")); // 허용된 태그는 유지
    }

    @Test
    void testNonJsonBodySanitization() {
        String maliciousText = "<script>alert('xss')</script>Hello";
        
        String result = filter.sanitizeXss(maliciousText);
        
        assertFalse(result.contains("<script>"));
        assertTrue(result.contains("Hello"));
    }

    @Test
    void testNestedJsonSanitization() {
        String nestedJson = "{\"user\":{\"name\":\"<script>alert(1)</script>Test\",\"tags\":[\"<img src=x onerror=alert(1)>\",\"safe\"]}}";
        
        String result = filter.sanitizeJsonBody(nestedJson);
        
        assertFalse(result.contains("<script>"));
        assertFalse(result.contains("onerror"));
        assertTrue(result.contains("Test"));
        assertTrue(result.contains("safe"));
    }

    @Test
    void testFormUrlencodedSanitization() {
        String formData = "name=%3Cscript%3Ealert%281%29%3C%2Fscript%3EJohn&message=Hello+%3Cb%3Eworld%3C%2Fb%3E";
        
        String result = filter.sanitizeFormBody(formData);
        
        assertFalse(result.contains("script"));
        assertTrue(result.contains("John"));
        assertTrue(result.contains("world"));
    }
}