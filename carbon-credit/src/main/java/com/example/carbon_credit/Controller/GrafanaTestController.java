package com.example.carbon_credit.Controller;

import com.example.carbon_credit.Service.GrafanaClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grafana")
public class GrafanaTestController {

    private final GrafanaClient grafanaClient;

    public GrafanaTestController(GrafanaClient grafanaClient) {
        this.grafanaClient = grafanaClient;
    }

    @GetMapping("/dashboards")
    public String dashboards() {
        return grafanaClient.get("/api/search");
    }
//    @PostMapping("/dashboard")
//    public String create() {
//        return grafanaClient.post("/api/dashboards/db", body);
//    }

    @GetMapping("/health")
    public String health() {
        return grafanaClient.get("/api/health");
    }
}

