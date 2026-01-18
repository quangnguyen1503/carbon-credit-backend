package com.example.carbon_credit.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GrafanaClient {

    @Value("${grafana.url}")
    private String grafanaUrl;

    @Value("${grafana.token}")
    private String grafanaToken;

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(grafanaToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public String get(String path) {
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Void> entity = new HttpEntity<>(headers());

        ResponseEntity<String> response = restTemplate.exchange(
                grafanaUrl + path,
                HttpMethod.GET,
                entity,
                String.class
        );
        return response.getBody();
    }

    public String post(String path, Object body) {
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Object> entity = new HttpEntity<>(body, headers());

        ResponseEntity<String> response = restTemplate.exchange(
                grafanaUrl + path,
                HttpMethod.POST,
                entity,
                String.class
        );
        return response.getBody();
    }
}

