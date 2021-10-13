package com.trynoice.api.helloworld;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloWorldController {

    @GetMapping("/")
    ResponseEntity<String> helloWorld() {
        return ResponseEntity.ok("Hello, world!");
    }
}
