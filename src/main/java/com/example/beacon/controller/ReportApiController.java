package com.example.beacon.controller;

import com.example.beacon.entity.SecurityEvent;
import com.example.beacon.repository.SecurityEventRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportApiController {

    private final SecurityEventRepository securityEventRepository;

    @GetMapping("/export")
    public void exportCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"security-report.csv\"");
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        PrintWriter writer = response.getWriter();
        writer.println("ID,유형,소스IP,프로토콜,포트,위치,위험도,상태,차단여부,발생시간");

        List<SecurityEvent> events = securityEventRepository.findAll(
                PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        for (SecurityEvent e : events) {
            writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    "EVT-" + e.getId(),
                    safe(e.getEventType()),
                    safe(e.getSourceIp()),
                    safe(e.getProtocol()),
                    e.getPort() != null ? e.getPort() : "",
                    safe(e.getLocation()),
                    safe(e.getSeverity()),
                    safe(e.getStatus()),
                    e.getBlocked() != null ? (e.getBlocked() ? "차단" : "허용") : "",
                    e.getCreatedAt() != null ? e.getCreatedAt().toString().replace("T", " ") : ""
            );
        }
        writer.flush();
    }

    private String safe(String s) {
        return s != null ? s.replace("\"", "\"\"") : "";
    }
}
