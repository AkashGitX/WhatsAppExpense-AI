package com.akash.budgetchanger.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves Thymeleaf HTML pages.
 * Authentication/authorisation is handled client-side via localStorage.
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String home() { return "index"; }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/dashboard")
    public String dashboard() { return "dashboard"; }

    @GetMapping("/expenses/new")
    public String addExpense() { return "add-expense"; }

    @GetMapping("/chat")
    public String chat() { return "chat"; }

    @GetMapping("/history")
    public String history() { return "history"; }

    @GetMapping("/settings")
    public String settings() { return "settings"; }
}
