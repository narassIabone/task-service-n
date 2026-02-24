package ru.cinimex.taskservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import ru.cinimex.taskservice.dto.UserResponseDto;

@FeignClient(name = "user-service", url = "${app.services.user-service.url}")
public interface UserServiceClient {
    @GetMapping("/admin/users/{login}")
    UserResponseDto getUserByLogin(
            @PathVariable("login") String login,
            @RequestHeader("Authorization") String authHeader
    );
}
