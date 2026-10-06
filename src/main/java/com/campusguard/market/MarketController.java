package com.campusguard.market;

import com.campusguard.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/market")
@Validated
public class MarketController {
  private final MarketService service;

  public MarketController(MarketService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> snapshot(@AuthenticationPrincipal Jwt jwt) {
    return service.snapshot(AuthenticatedUser.idOf(jwt));
  }

  @PostMapping("/trades")
  public Map<String, Object> trade(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MarketService.Trade trade) {
    return service.trade(AuthenticatedUser.idOf(jwt), trade);
  }

  @GetMapping("/trades")
  public Map<String, Object> history(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) UUID cursor,
      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
    return service.history(AuthenticatedUser.idOf(jwt), cursor, size);
  }

  @GetMapping("/leaderboard")
  public Map<String, Object> leaderboard(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
    return service.leaderboard(page, size);
  }
}
