package com.example.auction.app.adapters.in.web.realtime;

import com.example.auction.app.adapters.out.realtime.RealtimePushAdapter;
import com.example.auction.app.security.JwtSubjectValidator;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/realtime")
public class RealtimePushController {
    private final RealtimePushAdapter realtimePushAdapter;

    public RealtimePushController(RealtimePushAdapter realtimePushAdapter) {
        this.realtimePushAdapter = realtimePushAdapter;
    }

    @GetMapping(path = "/events")
    public SseEmitter subscribe(@AuthenticationPrincipal Jwt jwt) {
        JwtSubjectValidator.requireSubject(jwt);
        return realtimePushAdapter.connect();
    }
}
