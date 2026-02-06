package io.github.nicechester.bibleai.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(name = "auth.enabled", havingValue = "true")
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
